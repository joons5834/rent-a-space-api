package rent_a_space_api_clone.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import rent_a_space_api_clone.entity.*;
import rent_a_space_api_clone.enums.ReservationStatus;
import rent_a_space_api_clone.enums.Role;
import rent_a_space_api_clone.repository.*;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public class GetReservationDetailControllerTest {

    @Autowired
    private MockMvc mockMvc;

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

    private Reservation testReservation;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        Category category = categoryRepository.findByName("meeting")
                .orElseGet(() -> {
                    Category c = new Category();
                    c.setName("meeting");
                    return categoryRepository.save(c);
                });

        // Setup Host 1
        User hostUser = createAndSaveUser("host@example.com");
        UserProfile hostProfile = createAndSaveProfile(hostUser, Role.HOST, "Host1");

        // Setup Space & Subspace for Host 1
        Space space = createAndSaveSpace("Test Space", category, hostProfile);
        Subspace subspace = createAndSaveSubspace("Test Subspace", space);

        // Setup Renter 1
        User renterUser = createAndSaveUser("renter@example.com");
        UserProfile renterProfile = createAndSaveProfile(renterUser, Role.RENTER, "Renter1");

        // Create Reservation for Renter 1 in Host 1's Space
        testReservation = new Reservation();
        testReservation.setSubspace(subspace);
        testReservation.setRenterProfile(renterProfile);
        testReservation.setTimezone(ZoneId.of("UTC"));
        testReservation.setStartsAt(ZonedDateTime.of(2026, 5, 1, 10, 0, 0, 0, ZoneId.of("UTC")));
        testReservation.setEndsAt(ZonedDateTime.of(2026, 5, 1, 12, 0, 0, 0, ZoneId.of("UTC")));
        testReservation.setStatus(ReservationStatus.CONFIRMED);
        testReservation.setRenterName("Renter1");
        testReservation.setCreatedAt(OffsetDateTime.now());
        testReservation = reservationRepository.save(testReservation);

        // Setup Other Renter
        User otherRenterUser = createAndSaveUser("other_renter@example.com");
        UserProfile otherRenterProfile = createAndSaveProfile(otherRenterUser, Role.RENTER, "OtherRenter");

        // Setup Other Host
        User otherHostUser = createAndSaveUser("other_host@example.com");
        UserProfile otherHostProfile = createAndSaveProfile(otherHostUser, Role.HOST, "OtherHost");
    }

    private User createAndSaveUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("password");
        user.setEnabled(true);
        return userRepository.save(user);
    }

    private UserProfile createAndSaveProfile(User user, Role role, String nickname) {
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setRole(role);
        profile.setEnabled(true);
        profile.setNickname(nickname);
        return userProfileRepository.save(profile);
    }

    private Space createAndSaveSpace(String name, Category category, UserProfile host) {
        Space space = new Space();
        space.setName(name);
        space.setCategory(category);
        space.setHostProfile(host);
        space.setIsVisible(true);
        space.setTimezone(ZoneId.of("UTC"));
        return spaceRepository.save(space);
    }

    private Subspace createAndSaveSubspace(String name, Space space) {
        Subspace subspace = new Subspace();
        subspace.setName(name);
        subspace.setSpace(space);
        subspace.setIsVisible(true);
        return subspaceRepository.save(subspace);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM reservations");
        jdbcTemplate.execute("DELETE FROM subspaces");
        jdbcTemplate.execute("DELETE FROM spaces");
        jdbcTemplate.execute("DELETE FROM users_profiles");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("DELETE FROM categories");
    }

    @Test
    @Transactional
    void getReservationDetail_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/v0/reservation/" + testReservation.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    void getReservationDetail_AsRenter_OwnReservation_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/v0/reservation/" + testReservation.getId())
                        .with(user("renter@example.com").roles("RENTER")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(testReservation.getId()))
                .andExpect(jsonPath("$.data.renter_name").value("Renter1"));
    }

    @Test
    @Transactional
    void getReservationDetail_AsRenter_OtherReservation_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/v0/reservation/" + testReservation.getId())
                        .with(user("other_renter@example.com").roles("RENTER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void getReservationDetail_AsHost_OwnSpace_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/v0/reservation/" + testReservation.getId())
                        .with(user("host@example.com").roles("HOST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(testReservation.getId()));
    }

    @Test
    @Transactional
    void getReservationDetail_AsHost_OtherSpace_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/v0/reservation/" + testReservation.getId())
                        .with(user("other_host@example.com").roles("HOST")))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void getReservationDetail_AsAdmin_ReturnsSuccess() throws Exception {
        // Setup Admin
        User adminUser = createAndSaveUser("admin@example.com");
        createAndSaveProfile(adminUser, Role.ADMIN, "Admin");

        mockMvc.perform(get("/v0/reservation/" + testReservation.getId())
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(testReservation.getId()));
    }

    @Test
    @Transactional
    void getReservationDetail_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/v0/reservation/999999")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }
}
