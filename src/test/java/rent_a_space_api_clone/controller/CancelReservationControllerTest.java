package rent_a_space_api_clone.controller;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import rent_a_space_api_clone.dto.CancelReservationRequest;
import rent_a_space_api_clone.entity.*;
import rent_a_space_api_clone.enums.Role;
import rent_a_space_api_clone.repository.*;
import tools.jackson.databind.json.JsonMapper;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static rent_a_space_api_clone.enums.ReservationStatus.CANCELLED;
import static rent_a_space_api_clone.enums.ReservationStatus.CONFIRMED;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@WithMockUser(username = "renter@example.com", roles = {"RENTER"})
public class CancelReservationControllerTest {

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
    private Long reservationId;
    private Long renterProfileId;
    private Long hostProfileId;
    private Long adminProfileId;

    @BeforeEach
    void setUp() {
        if (categoryRepository.findByName("meeting").isEmpty()) {
            Category category = new Category();
            category.setName("meeting");
            categoryRepository.save(category);
        }

        if (userRepository.findByEmail("renter@example.com").isEmpty()) {
            var user = new User();
            user.setEmail("renter@example.com");
            user.setPassword("password");
            user.setEnabled(true);
            userRepository.save(user);

            var profile = new UserProfile();
            profile.setUser(user);
            profile.setRole(Role.RENTER);
            profile.setEnabled(true);
            profile.setNickname("TestRenter");
            renterProfileId = userProfileRepository.save(profile).getId();
        }

        if (userRepository.findByEmail("host@example.com").isEmpty()) {
            var hostUser = new User();
            hostUser.setEmail("host@example.com");
            hostUser.setPassword("password");
            hostUser.setEnabled(true);
            userRepository.save(hostUser);

            var hostProfile = new UserProfile();
            hostProfile.setUser(hostUser);
            hostProfile.setRole(Role.HOST);
            hostProfile.setEnabled(true);
            hostProfile.setNickname("TestHost");
            hostProfileId = userProfileRepository.save(hostProfile).getId();
        }

        if (userRepository.findByEmail("admin@example.com").isEmpty()) {
            var adminUser = new User();
            adminUser.setEmail("admin@example.com");
            adminUser.setPassword("password");
            adminUser.setEnabled(true);
            userRepository.save(adminUser);

            var adminProfile = new UserProfile();
            adminProfile.setUser(adminUser);
            adminProfile.setRole(Role.ADMIN);
            adminProfile.setEnabled(true);
            adminProfile.setNickname("TestAdmin");
            adminProfileId = userProfileRepository.save(adminProfile).getId();
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

        UserProfile renterProfile = userProfileRepository.findByUserEmail("renter@example.com");
        Reservation reservation = new Reservation();
        reservation.setSubspace(testSubspace);
        reservation.setRenterProfile(renterProfile);
        reservation.setTimezone(ZoneId.of("Asia/Seoul"));
        reservation.setStartsAt(ZonedDateTime.of(LocalDateTime.of(2026, 3, 6, 10, 0, 0),
                ZoneId.of("Asia/Seoul")));
        reservation.setEndsAt(ZonedDateTime.of(LocalDateTime.of(2026, 3, 6, 12, 0, 0),
                ZoneId.of("Asia/Seoul")));
        reservation.setStatus(CONFIRMED);
        reservation.setCreatedAt(OffsetDateTime.now());
        reservationId = reservationRepository.save(reservation).getId();
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
    void cancelReservationByRenter_Success() throws Exception {
        CancelReservationRequest request = new CancelReservationRequest();
        request.setRole(Role.RENTER);
        request.setCancellationReason("Change of schedule");

        mockMvc.perform(delete("/v0/reservation/{id}", reservationId)
                        .with(user("renter@example.com").roles("RENTER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/v0/reservation/{id}", reservationId)
                        .with(user("renter@example.com").roles("RENTER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Reservation already cancelled"));

        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(CANCELLED);
        assertThat(reservation.getCancelledAt()).isNotNull();
        assertThat(reservation.getCancellationReason()).isEqualTo("Change of schedule");
        assertThat(reservation.getCancelledByProfile().getId())
                .isEqualTo(renterProfileId);
    }

    @Test
    void cancelReservationByHost_Success() throws Exception {
        CancelReservationRequest request = new CancelReservationRequest();
        request.setRole(Role.HOST);
        request.setCancellationReason("Change of schedule");

        mockMvc.perform(delete("/v0/reservation/{id}", reservationId)
                        .with(user("host@example.com").roles("HOST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/v0/reservation/{id}", reservationId)
                        .with(user("host@example.com").roles("HOST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Reservation already cancelled"));

        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(CANCELLED);
        assertThat(reservation.getCancelledAt()).isNotNull();
        assertThat(reservation.getCancellationReason()).isEqualTo("Change of schedule");
        assertThat(reservation.getCancelledByProfile().getId())
                .isEqualTo(hostProfileId);
    }

    @Test
    void cancelReservationByAdmin_Success() throws Exception {
        CancelReservationRequest request = new CancelReservationRequest();
        request.setRole(Role.ADMIN);
        request.setCancellationReason("Change of schedule");

        mockMvc.perform(delete("/v0/reservation/{id}", reservationId)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/v0/reservation/{id}", reservationId)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Reservation already cancelled"));

        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(CANCELLED);
        assertThat(reservation.getCancelledAt()).isNotNull();
        assertThat(reservation.getCancellationReason()).isEqualTo("Change of schedule");
        assertThat(reservation.getCancelledByProfile().getId())
                .isEqualTo(adminProfileId);
    }

    @Test
    @DisplayName("Renters who did not made the reservation cannot cancel the reservation")
    void cancelReservation_RenterForbidden() throws Exception {
        var user = new User();
        user.setEmail("renter2@example.com");
        user.setPassword("password");
        user.setEnabled(true);
        userRepository.save(user);

        var profile = new UserProfile();
        profile.setUser(user);
        profile.setRole(Role.RENTER);
        profile.setEnabled(true);
        profile.setNickname("TestRenter2");
        userProfileRepository.save(profile);

        CancelReservationRequest request = new CancelReservationRequest();
        request.setRole(Role.RENTER);
        request.setCancellationReason("Change of schedule");

        mockMvc.perform(delete("/v0/reservation/{id}", reservationId)
                        .with(user("renter2@example.com").roles("RENTER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getStatus()).isNotEqualTo(CANCELLED);
        assertThat(reservation.getCancelledAt()).isNull();
        assertThat(reservation.getCancellationReason()).isNull();
        assertThat(reservation.getCancelledByProfile()).isNull();
    }

    @Test
    @DisplayName("Hosts who does not own the space cannot cancel the reservation")
    void cancelReservation_HostForbidden() throws Exception {
        var user = new User();
        user.setEmail("host2@example.com");
        user.setPassword("password");
        user.setEnabled(true);
        userRepository.save(user);

        var profile = new UserProfile();
        profile.setUser(user);
        profile.setRole(Role.HOST);
        profile.setEnabled(true);
        profile.setNickname("TestHost2");
        userProfileRepository.save(profile);

        CancelReservationRequest request = new CancelReservationRequest();
        request.setRole(Role.HOST);
        request.setCancellationReason("Change of schedule");

        mockMvc.perform(delete("/v0/reservation/{id}", reservationId)
                        .with(user("host2@example.com").roles("HOST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getStatus()).isNotEqualTo(CANCELLED);
        assertThat(reservation.getCancelledAt()).isNull();
        assertThat(reservation.getCancellationReason()).isNull();
        assertThat(reservation.getCancelledByProfile()).isNull();
    }

    @Test
    @DisplayName("Cannot cancel the reservation in admin mode without admin role.")
    void cancelReservation_AdminForbidden() throws Exception {
        CancelReservationRequest request = new CancelReservationRequest();
        request.setRole(Role.ADMIN);
        request.setCancellationReason("Change of schedule");

        mockMvc.perform(delete("/v0/reservation/{id}", reservationId)
                        .with(user("renter@example.com").roles("RENTER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getStatus()).isNotEqualTo(CANCELLED);
        assertThat(reservation.getCancelledAt()).isNull();
        assertThat(reservation.getCancellationReason()).isNull();
        assertThat(reservation.getCancelledByProfile()).isNull();
    }

    @Test
    @DisplayName("Only can cancel future reservations. Cannot cancel past reservations")
    void cancelReservation_cancelsPastReservation() throws Exception {
        UserProfile renterProfile = userProfileRepository.findByUserEmail("renter@example.com");
        Reservation reservation = new Reservation();
        reservation.setSubspace(testSubspace);
        reservation.setRenterProfile(renterProfile);
        reservation.setTimezone(ZoneId.of("Asia/Seoul"));
        reservation.setStartsAt(ZonedDateTime.of(LocalDateTime.of(2026, 2, 28, 10, 0, 0),
                ZoneId.of("Asia/Seoul")));
        reservation.setEndsAt(ZonedDateTime.of(LocalDateTime.of(2026, 2, 28, 12, 0, 0),
                ZoneId.of("Asia/Seoul")));
        reservation.setStatus(CONFIRMED);
        reservation.setCreatedAt(OffsetDateTime.now());
        Long reservationId = reservationRepository.save(reservation).getId();

        CancelReservationRequest request = new CancelReservationRequest();
        request.setRole(Role.RENTER);
        request.setCancellationReason("Change of schedule");

        mockMvc.perform(delete("/v0/reservation/{id}", reservationId)
                        .with(user("renter@example.com").roles("RENTER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Only able to cancel future reservations."));

        Reservation fetchedReservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(fetchedReservation.getStatus()).isNotEqualTo(CANCELLED);
        assertThat(fetchedReservation.getCancelledAt()).isNull();
        assertThat(fetchedReservation.getCancellationReason()).isNull();
        assertThat(fetchedReservation.getCancelledByProfile()).isNull();
    }
}
