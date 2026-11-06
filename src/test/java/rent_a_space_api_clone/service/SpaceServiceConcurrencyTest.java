package rent_a_space_api_clone.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import rent_a_space_api_clone.dto.UpdateSubspaceRequest;
import rent_a_space_api_clone.entity.Image;
import rent_a_space_api_clone.entity.Space;
import rent_a_space_api_clone.entity.Subspace;
import rent_a_space_api_clone.entity.SubspaceImage;
import rent_a_space_api_clone.exception.ResourceNotFoundException;
import rent_a_space_api_clone.repository.ImageRepository;
import rent_a_space_api_clone.repository.SpaceRepository;
import rent_a_space_api_clone.repository.SubspaceImageRepository;
import rent_a_space_api_clone.repository.SubspaceRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:space_concurrency;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL")
@ActiveProfiles("test")
@Import(SpaceServiceConcurrencyTest.TestConfig.class)
class SpaceServiceConcurrencyTest {

    @Autowired
    private SpaceService spaceService;

    @Autowired
    private SubspaceRepository subspaceRepository;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private SubspaceImageRepository subspaceImageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private LookupGate lookupGate;

    private TransactionTemplate transactions;
    private Long spaceId;
    private Long subspaceId;
    private Long imageId;

    @BeforeEach
    void setUp() {
        transactions = new TransactionTemplate(transactionManager);
        transactions.executeWithoutResult(status -> {
            Space space = new Space();
            space.setName("Concurrency space");
            spaceId = spaceRepository.save(space).getId();

            Subspace subspace = new Subspace();
            subspace.setSpace(space);
            subspace.setName("Original name");
            subspace.setDescription("Original description");
            subspace.setMinHours(1);
            subspace.setMaxHours(4);
            subspace.setIsVisible(false);
            subspaceId = subspaceRepository.save(subspace).getId();

            Image image = new Image();
            image.setFullUrl("https://example.com/concurrency-original.png");
            imageId = imageRepository.save(image).getId();

            SubspaceImage subspaceImage = new SubspaceImage();
            subspaceImage.setSubspace(subspace);
            subspaceImage.setImage(image);
            subspaceImage.setOrderSeq(0);
            subspaceImageRepository.save(subspaceImage);
        });
    }

    @AfterEach
    void tearDown() {
        transactions.executeWithoutResult(status -> {
            if (subspaceId != null) {
                jdbcTemplate.update("DELETE FROM subspaces_images WHERE subspace_id = ?", subspaceId);
                jdbcTemplate.update("DELETE FROM subspaces WHERE id = ?", subspaceId);
            }
            if (spaceId != null) {
                jdbcTemplate.update("DELETE FROM spaces WHERE id = ?", spaceId);
            }
            if (imageId != null) {
                jdbcTemplate.update("DELETE FROM images WHERE id = ?", imageId);
            }
        });
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void updateAndDeleteSerializeWithoutResurrectingSubspace() throws Exception {
        CountDownLatch lookupFinished = new CountDownLatch(1);
        CountDownLatch resumeUpdate = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> update = workers.submit(() -> {
                lookupGate.arm(lookupFinished, resumeUpdate);
                try {
                    spaceService.updateSubspace(subspaceId, new UpdateSubspaceRequest(
                            "Updated name", null, null, null, null, null, null));
                } finally {
                    lookupGate.clear();
                }
            });

            if (!lookupFinished.await(10, TimeUnit.SECONDS)) {
                if (update.isDone()) {
                    update.get();
                }
                throw new AssertionError("Updater did not complete its real repository lookup");
            }
            Future<Boolean> delete = workers.submit(this::tryDeleteWithNowait);
            boolean deletedWhileUpdatePaused = delete.get(10, TimeUnit.SECONDS);

            resumeUpdate.countDown();
            update.get(10, TimeUnit.SECONDS);
            if (!deletedWhileUpdatePaused) {
                spaceService.softDeleteASubspace(subspaceId);
            }

            Map<String, Object> physicalRow = jdbcTemplate.queryForMap(
                    "SELECT name, deleted_at FROM subspaces WHERE id = ?", subspaceId);
            assertThat(physicalRow.get("name")).isEqualTo("Updated name");
            assertThat(physicalRow.get("deleted_at"))
                    .as("delete survives update; delete completed while update was paused: %s", deletedWhileUpdatePaused)
                    .isNotNull();
            assertThat(subspaceRepository.findById(subspaceId)).isEmpty();
            assertThat(deletedWhileUpdatePaused)
                    .as("NOWAIT must conflict with the updater's row lock").isFalse();
        } finally {
            resumeUpdate.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS))
                    .as("worker threads terminate").isTrue();
        }
    }

    private boolean tryDeleteWithNowait() {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            try {
                Long lockedId = jdbcTemplate.queryForObject(
                        "SELECT id FROM subspaces WHERE id = ? FOR UPDATE NOWAIT", Long.class, subspaceId);
                assertThat(lockedId).isEqualTo(subspaceId);
            } catch (DataAccessException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof SQLException sqlException
                        && sqlException.getErrorCode() == 50200
                        && "HYT00".equals(sqlException.getSQLState())) {
                    status.setRollbackOnly();
                    return false;
                }
                throw ex;
            }
            spaceService.softDeleteASubspace(subspaceId);
            return true;
        }));
    }

    @Test
    void deleteFirstExcludesRowFromLockedLookupAndUpdate() {
        spaceService.softDeleteASubspace(subspaceId);
        Map<String, Object> before = physicalSubspace();
        List<Map<String, Object>> imagesBefore = physicalImages();
        assertThat(before.get("deleted_at")).isNotNull();
        assertThat(imagesBefore).hasSize(1);
        Optional<Subspace> lockedLookup = transactions.execute(
                status -> subspaceRepository.findByIdForUpdate(subspaceId));
        assertThat(lockedLookup).isEmpty();

        assertThatThrownBy(() -> spaceService.updateSubspace(subspaceId, new UpdateSubspaceRequest(
                "Changed name", "Changed description", "https://example.com/replacement.png",
                List.of(), 2, 8, true)))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(physicalSubspace()).isEqualTo(before);
        assertThat(physicalImages()).isEqualTo(imagesBefore);
        assertThat(subspaceRepository.findById(subspaceId)).isEmpty();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean
        static LookupGate lookupGate() {
            return new LookupGate();
        }

        @Bean
        static BeanPostProcessor subspaceLookupInterceptor(LookupGate gate) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!"subspaceRepository".equals(beanName) || !(bean instanceof SubspaceRepository)) {
                        return bean;
                    }
                    ProxyFactory proxy = new ProxyFactory();
                    proxy.setTarget(bean);
                    proxy.setInterfaces(SubspaceRepository.class);
                    proxy.addAdvice((MethodInterceptor) invocation -> {
                        Object result = invocation.proceed();
                        String method = invocation.getMethod().getName();
                        if ("findById".equals(method) || "findByIdForUpdate".equals(method)) {
                            gate.afterLookup(result);
                        }
                        return result;
                    });
                    return proxy.getProxy();
                }
            };
        }
    }

    static class LookupGate {
        private final ThreadLocal<Pause> pauses = new ThreadLocal<>();

        void arm(CountDownLatch observed, CountDownLatch resume) {
            pauses.set(new Pause(observed, resume));
        }

        void clear() {
            pauses.remove();
        }

        void afterLookup(Object result) throws InterruptedException {
            Pause pause = pauses.get();
            if (pause == null) {
                return;
            }
            assertThat((Optional<?>) result).isPresent();
            pause.observed().countDown();
            if (!pause.resume().await(20, TimeUnit.SECONDS)) {
                throw new AssertionError("Updater was not released after lookup");
            }
        }

        private record Pause(CountDownLatch observed, CountDownLatch resume) {
        }
    }

    private Map<String, Object> physicalSubspace() {
        return jdbcTemplate.queryForMap(
                "SELECT name, description, min_hours, max_hours, is_visible, deleted_at FROM subspaces WHERE id = ?",
                subspaceId);
    }

    private List<Map<String, Object>> physicalImages() {
        return jdbcTemplate.queryForList(
                "SELECT image_id, order_seq FROM subspaces_images WHERE subspace_id = ? ORDER BY order_seq",
                subspaceId);
    }
}
