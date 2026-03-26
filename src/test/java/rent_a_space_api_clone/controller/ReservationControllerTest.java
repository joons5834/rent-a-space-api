package rent_a_space_api_clone.controller;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import rent_a_space_api_clone.dto.CreateReservationRequest;
import rent_a_space_api_clone.entity.*;
import rent_a_space_api_clone.enums.HolidayFrequencyType;
import rent_a_space_api_clone.enums.Role;
import rent_a_space_api_clone.repository.*;
import tools.jackson.databind.json.JsonMapper;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@WithMockUser(username = "renter@example.com", roles = {"RENTER"})
public class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private SubspaceRepository subspaceRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private HolidayRuleRepository holidayRuleRepository;

    @Autowired
    private HolidayOverrideRepository holidayOverrideRepository;

    @Autowired
    private EntityManager entityManager;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public Clock FakeClockConfig(){
            Instant fixedInstant = Instant.parse("2026-03-01T10:00:00Z");
            return Clock.fixed(fixedInstant, ZoneId.of("UTC"));
        }
    }

    private Space testSpace;
    private Subspace testSubspace;

    @BeforeEach
    void setUp() {
        if (categoryRepository.findByName("meeting").isEmpty()) {
            Category category = new Category();
            category.setName("meeting");
            categoryRepository.save(category);
        }

        if (userRepository.findByEmail("renter@example.com").isEmpty()) {
            var user = new rent_a_space_api_clone.entity.User();
            user.setEmail("renter@example.com");
            user.setPassword("password");
            user.setEnabled(true);
            userRepository.save(user);

            var profile = new rent_a_space_api_clone.entity.UserProfile();
            profile.setUser(user);
            profile.setRole(Role.RENTER);
            profile.setEnabled(true);
            profile.setNickname("TestRenter");
            userProfileRepository.save(profile);
        }

        if (userRepository.findByEmail("host@example.com").isEmpty()) {
            var hostUser = new rent_a_space_api_clone.entity.User();
            hostUser.setEmail("host@example.com");
            hostUser.setPassword("password");
            hostUser.setEnabled(true);
            userRepository.save(hostUser);

            var hostProfile = new rent_a_space_api_clone.entity.UserProfile();
            hostProfile.setUser(hostUser);
            hostProfile.setRole(Role.HOST);
            hostProfile.setEnabled(true);
            hostProfile.setNickname("TestHost");
            userProfileRepository.save(hostProfile);
        }

        UserProfile hostProfile = userProfileRepository.findByUserEmail("host@example.com");
        testSpace = new Space();
        testSpace.setName("Test Space");
        testSpace.setCategory(categoryRepository.findByName("meeting").get());
        testSpace.setHostProfile(hostProfile);
        testSpace.setCloseStart(LocalTime.of(17, 0));
        testSpace.setCloseEnd(LocalTime.of(9, 0));
        testSpace.setIsClosedAtPublicHolidays(false);
        testSpace.setIsVisible(true);
        testSpace.setTimezone(ZoneId.of("Asia/Seoul"));
        testSpace = spaceRepository.save(testSpace);

        testSubspace = new Subspace();
        testSubspace.setName("Test Subspace");
        testSubspace.setSpace(testSpace);
        testSubspace.setMinHours(1);
        testSubspace.setMaxHours(7);
        testSubspace.setIsVisible(true);
        testSubspace = subspaceRepository.save(testSubspace);
    }

    @AfterEach
    void tearDown() {
        reservationRepository.deleteAll();
        holidayOverrideRepository.deleteAll();
        holidayRuleRepository.deleteAll();
        subspaceRepository.deleteAll();
        spaceRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    @Transactional
    void createReservation_Success() throws Exception {
        CreateReservationRequest request = new CreateReservationRequest(
                testSubspace.getId(),
                LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 10, 12, 0),
                "Mike",
                "01012345678",
                "example@example.com",
                "I need 2 chairs."
        );

        mockMvc.perform(post("/v0/reservation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists());

        assertThat(reservationRepository.findAll()).hasSize(1);
        Reservation saved = reservationRepository.findAll().get(0);
        assertThat(saved.getRenterName()).isEqualTo("Mike");
        assertThat(saved.getRenterPhone()).isEqualTo("01012345678");
        assertThat(saved.getRenterEmail()).isEqualTo("example@example.com");
        assertThat(saved.getCustomRequest()).isEqualTo("I need 2 chairs.");
        assertThat(saved.getStatus()).isEqualTo("confirmed");
        assertThat(saved.getSubspace().getId()).isEqualTo(testSubspace.getId());
    }

    @Test
    @Transactional
    void createReservation_InvalidRequest_MissingRequiredFields() throws Exception {
        CreateReservationRequest request = new CreateReservationRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        mockMvc.perform(post("/v0/reservation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createReservation_InvalidEmail() throws Exception {
        CreateReservationRequest request = new CreateReservationRequest(
                testSubspace.getId(),
                LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 10, 12, 0),
                "Mike",
                "01012345678",
                "invalid-email",
                null
        );

        mockMvc.perform(post("/v0/reservation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createReservation_SubspaceNotFound() throws Exception {
        CreateReservationRequest request = new CreateReservationRequest(
                999L,
                LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 10, 12, 0),
                "Mike",
                "01012345678",
                "example@example.com",
                null
        );

        mockMvc.perform(post("/v0/reservation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void createReservation_OverlappingReservation() throws Exception {
        Reservation existing = new Reservation();
        existing.setSubspace(testSubspace);
        existing.setRenterProfile(userProfileRepository.findByUserEmail("renter@example.com"));
        existing.setTimezone("Asia/Seoul");
        existing.setStartsAt(ZonedDateTime.of(LocalDateTime.of(2026, 3, 10, 10, 0), testSubspace.getSpace().getTimezone()));
        existing.setEndsAt(ZonedDateTime.of(LocalDateTime.of(2026, 3, 10, 12, 0), testSubspace.getSpace().getTimezone()));
        existing.setStatus("confirmed");
        existing.setRenterName("Existing");
        existing.setRenterPhone("01000000000");
        existing.setRenterEmail("existing@example.com");
        existing.setCreatedAt(java.time.OffsetDateTime.now());
        reservationRepository.save(existing);

        CreateReservationRequest request = new CreateReservationRequest(
                testSubspace.getId(),
                LocalDateTime.of(2026, 3, 10, 11, 0),
                LocalDateTime.of(2026, 3, 10, 13, 0),
                "Mike",
                "01012345678",
                "example@example.com",
                null
        );

        mockMvc.perform(post("/v0/reservation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Time window overlaps with existing reservations"));
    }

    @Test
    @Transactional
    void createReservation_SpaceClosed_BeforeOpening() throws Exception {
        CreateReservationRequest request = new CreateReservationRequest(
                testSubspace.getId(),
                LocalDateTime.of(2026, 3, 10, 8, 0),
                LocalDateTime.of(2026, 3, 10, 10, 0),
                "Mike",
                "01012345678",
                "example@example.com",
                null
        );

        mockMvc.perform(post("/v0/reservation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Space is closed during the requested time window"));
    }

    @Test
    @Transactional
    void createReservation_SpaceClosed_AfterClosing() throws Exception {
        CreateReservationRequest request = new CreateReservationRequest(
                testSubspace.getId(),
                LocalDateTime.of(2026, 3, 10, 16, 0),
                LocalDateTime.of(2026, 3, 10, 18, 0),
                "Mike",
                "01012345678",
                "example@example.com",
                null
        );

        mockMvc.perform(post("/v0/reservation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Space is closed during the requested time window"));
    }

    @Test
    @Transactional
    void createReservation_SpaceClosed_OnHoliday() throws Exception {
        HolidayOverride holiday = new HolidayOverride();
        holiday.setSpace(testSpace);
        holiday.setName("Christmas");
        holiday.setStartsAt(java.time.LocalDate.of(2026, 3, 10));
        holiday.setEndsAt(java.time.LocalDate.of(2026, 3, 10));
        holiday.setIsClosed(true);
        holiday.setDayMask((short) 127);
        holiday.setPriorityWeight(0);
        holidayOverrideRepository.save(holiday);

        entityManager.flush();
        entityManager.clear();

        CreateReservationRequest request = new CreateReservationRequest(
                testSubspace.getId(),
                LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 10, 12, 0),
                "Mike",
                "01012345678",
                "example@example.com",
                null
        );

        mockMvc.perform(post("/v0/reservation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Space is closed on holiday for the requested date"));
    }

    @Test
    @Transactional
    void createReservation_SpaceClosed_OnHolidayRule() throws Exception {
        HolidayRule rule = new HolidayRule();
        rule.setSpace(testSpace);
        rule.setFrequencyType(HolidayFrequencyType.WEEKLY);
        rule.setDayMask((short) 16);
        holidayRuleRepository.save(rule);

        entityManager.flush();
        entityManager.clear();

        CreateReservationRequest request = new CreateReservationRequest(
                testSubspace.getId(),
                LocalDateTime.of(2026, 3, 12, 10, 0),
                LocalDateTime.of(2026, 3, 12, 12, 0),
                "Mike",
                "01012345678",
                "example@example.com",
                null
        );

        mockMvc.perform(post("/v0/reservation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Space is closed on holiday for the requested date"));
    }

    @Test
    @Transactional
    void createReservation_DurationTooShort() throws Exception {
        CreateReservationRequest request = new CreateReservationRequest(
                testSubspace.getId(),
                LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 10, 10, 30),
                "Mike",
                "01012345678",
                "example@example.com",
                null
        );

        mockMvc.perform(post("/v0/reservation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Reservation duration is less than minimum hours allowed: 1"));
    }

    @Test
    @Transactional
    void createReservation_DurationTooLong() throws Exception {
        CreateReservationRequest request = new CreateReservationRequest(
                testSubspace.getId(),
                LocalDateTime.of(2026, 3, 10, 9, 0),
                LocalDateTime.of(2026, 3, 10, 17, 0),
                "Mike",
                "01012345678",
                "example@example.com",
                null
        );

        mockMvc.perform(post("/v0/reservation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Reservation duration exceeds maximum hours allowed: 7"));
    }

    @Test
    @Transactional
    void createReservation_SpaceOpen24Hours() throws Exception {
        testSpace.setCloseStart(LocalTime.of(0, 0));
        testSpace.setCloseEnd(LocalTime.of(0, 0));
        spaceRepository.save(testSpace);

        CreateReservationRequest request = new CreateReservationRequest(
                testSubspace.getId(),
                LocalDateTime.of(2026, 3, 10, 22, 0),
                LocalDateTime.of(2026, 3, 11, 2, 0),
                "Mike",
                "01012345678",
                "example@example.com",
                null
        );

        mockMvc.perform(post("/v0/reservation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists());
    }

    @Test
    @Transactional
    void createReservation_AdjacentReservations_NoOverlap() throws Exception {
        Reservation existing = new Reservation();
        existing.setSubspace(testSubspace);
        existing.setRenterProfile(userProfileRepository.findByUserEmail("renter@example.com"));
        existing.setTimezone("Asia/Seoul");
        existing.setStartsAt(ZonedDateTime.of(LocalDateTime.of(2026, 3, 10, 10, 0), testSubspace.getSpace().getTimezone()));
        existing.setEndsAt(ZonedDateTime.of(LocalDateTime.of(2026, 3, 10, 12, 0), testSubspace.getSpace().getTimezone()));
        existing.setStatus("confirmed");
        existing.setRenterName("Existing");
        existing.setRenterPhone("01000000000");
        existing.setRenterEmail("existing@example.com");
        existing.setCreatedAt(java.time.OffsetDateTime.now());
        reservationRepository.save(existing);

        CreateReservationRequest request = new CreateReservationRequest(
                testSubspace.getId(),
                LocalDateTime.of(2026, 3, 10, 12, 0),
                LocalDateTime.of(2026, 3, 10, 14, 0),
                "Mike",
                "01012345678",
                "example@example.com",
                null
        );

        mockMvc.perform(post("/v0/reservation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists());
    }
}
