package rent_a_space_api_clone.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import rent_a_space_api_clone.entity.*;
import rent_a_space_api_clone.enums.ReservationStatus;
import rent_a_space_api_clone.enums.Role;
import rent_a_space_api_clone.repository.*;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public class ReservationListControllerTest {

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

    @BeforeEach
    void setUp() {
        Category category = categoryRepository.findByName("meeting")
                .orElseGet(() -> {
                    Category c = new Category();
                    c.setName("meeting");
                    return categoryRepository.save(c);
                });

        User hostUser = new User();
        hostUser.setEmail("host@example.com");
        hostUser.setPassword("password");
        hostUser.setEnabled(true);
        userRepository.save(hostUser);

        UserProfile hostProfile = new UserProfile();
        hostProfile.setUser(hostUser);
        hostProfile.setRole(Role.HOST);
        hostProfile.setEnabled(true);
        hostProfile.setNickname("TestHost");
        userProfileRepository.save(hostProfile);

        Space testSpace = new Space();
        testSpace.setName("Test Space");
        testSpace.setCategory(category);
        testSpace.setHostProfile(hostProfile);
        testSpace.setIsVisible(true);
        testSpace.setTimezone(ZoneId.of("UTC"));
        testSpace = spaceRepository.save(testSpace);

        Subspace testSubspace = new Subspace();
        testSubspace.setName("Test Subspace");
        testSubspace.setSpace(testSpace);
        testSubspace.setIsVisible(true);
        testSubspace = subspaceRepository.save(testSubspace);

        User renterUser = new User();
        renterUser.setEmail("renter@example.com");
        renterUser.setPassword("password");
        renterUser.setEnabled(true);
        userRepository.save(renterUser);

        UserProfile renterProfile = new UserProfile();
        renterProfile.setUser(renterUser);
        renterProfile.setRole(Role.RENTER);
        renterProfile.setEnabled(true);
        renterProfile.setNickname("TestRenter");
        userProfileRepository.save(renterProfile);

        User anotherRenterUser = new User();
        anotherRenterUser.setEmail("another_renter@example.com");
        anotherRenterUser.setPassword("password");
        anotherRenterUser.setEnabled(true);
        userRepository.save(anotherRenterUser);

        UserProfile anotherRenterProfile = new UserProfile();
        anotherRenterProfile.setUser(anotherRenterUser);
        anotherRenterProfile.setRole(Role.RENTER);
        anotherRenterProfile.setEnabled(true);
        anotherRenterProfile.setNickname("AnotherTestRenter");
        userProfileRepository.save(anotherRenterProfile);

        // Create some reservations
        List<Reservation> reservations = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            Reservation r = new Reservation();
            r.setSubspace(testSubspace);
            r.setRenterProfile(renterProfile);
            r.setTimezone(ZoneId.of("UTC"));
            r.setStartsAt(ZonedDateTime.of(2026, 4, i, 10, 0, 0, 0, ZoneId.of("UTC")));
            r.setEndsAt(ZonedDateTime.of(2026, 4, i, 12, 0, 0, 0, ZoneId.of("UTC")));
            r.setStatus(i % 2 == 0 ? ReservationStatus.CONFIRMED : ReservationStatus.PENDING);
            r.setRenterName("Renter " + i);
            r.setCreatedAt(OffsetDateTime.now());
            reservations.add(r);
        }

        Reservation r = new Reservation();
        r.setSubspace(testSubspace);
        r.setRenterProfile(anotherRenterProfile);
        r.setTimezone(ZoneId.of("UTC"));
        r.setStartsAt(ZonedDateTime.of(2026, 4, 6, 10, 0, 0, 0, ZoneId.of("UTC")));
        r.setEndsAt(ZonedDateTime.of(2026, 4, 6, 12, 0, 0, 0, ZoneId.of("UTC")));
        r.setStatus(ReservationStatus.CONFIRMED);
        r.setRenterName("AnotherRenter1");
        r.setCreatedAt(OffsetDateTime.now());
        reservations.add(r);
        reservationRepository.saveAll(reservations);
    }

    @AfterEach
    void tearDown() {
        reservationRepository.deleteAll();
        subspaceRepository.deleteAll();
        spaceRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "host@example.com", roles = {"HOST"})
    @Transactional
    void getHostReservations_OrderById_Success() throws Exception {
        mockMvc.perform(get("/v0/host/reservations")
                        .param("orderBy", "id")
                        .param("limit", "2"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations", hasSize(2)))
                .andExpect(jsonPath("$.next_cursor").exists());
    }

    @Test
    @WithMockUser(username = "renter@example.com", roles = {"RENTER"})
    void getRenterReservations_OrderById_Success() throws Exception {
        mockMvc.perform(get("/v0/renter/reservations")
                        .param("orderBy", "id")
                        .param("limit", "2"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations", hasSize(2)))
                .andExpect(jsonPath("$.next_cursor").exists());
    }

    @Test
    @WithMockUser(username = "host@example.com", roles = {"HOST"})
    @Transactional
    void getHostReservations_OrderByStartsAt_Success() throws Exception {
        mockMvc.perform(get("/v0/host/reservations")
                        .param("orderBy", "starts_at")
                        .param("limit", "2"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations", hasSize(2)))
                .andExpect(jsonPath("$.reservations[0].starts_at", containsString("2026-04-06T10:00:00")))
                .andExpect(jsonPath("$.reservations[1].starts_at", containsString("2026-04-05T10:00:00")))
                .andExpect(jsonPath("$.next_cursor").exists());
    }

    @Test
    @WithMockUser(username = "renter@example.com", roles = {"RENTER"})
    @Transactional
    void getRenterReservations_OrderByStartsAt_Success() throws Exception {
        mockMvc.perform(get("/v0/renter/reservations")
                        .param("orderBy", "starts_at")
                        .param("limit", "2"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations", hasSize(2)))
                .andExpect(jsonPath("$.reservations[0].starts_at", containsString("2026-04-05T10:00:00")))
                .andExpect(jsonPath("$.reservations[1].starts_at", containsString("2026-04-04T10:00:00")))
                .andExpect(jsonPath("$.next_cursor").exists());
    }

    @Test
    @WithMockUser(username = "host@example.com", roles = {"HOST"})
    @Transactional
    void getHostReservations_FilterByStatus_Success() throws Exception {
        mockMvc.perform(get("/v0/host/reservations")
                        .param("status", "CONFIRMED"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations", everyItem(hasEntry("status", "CONFIRMED"))));
    }

    @Test
    @WithMockUser(username = "renter@example.com", roles = {"RENTER"})
    @Transactional
    void getRenterReservations_FilterByStatus_Success() throws Exception {
        mockMvc.perform(get("/v0/renter/reservations")
                        .param("status", "CONFIRMED"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations", everyItem(hasEntry("status", "CONFIRMED"))));
    }

    @Test
    @WithMockUser(username = "host@example.com", roles = {"HOST"})
    @Transactional
    void getHostReservations_Pagination_Success() throws Exception {
        // First page
        String content = mockMvc.perform(get("/v0/host/reservations")
                        .with(user("host@example.com").roles("HOST"))
                        .param("orderBy", "id")
                        .param("limit", "2"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations", hasSize(2)))
                .andReturn().getResponse().getContentAsString();

        Object cursorObj = com.jayway.jsonpath.JsonPath.read(content, "$.next_cursor");
        String nextCursor = cursorObj != null ? cursorObj.toString() : null;

        // Second page
        mockMvc.perform(get("/v0/host/reservations")
                        .with(user("host@example.com").roles("HOST"))
                        .param("orderBy", "id")
                        .param("limit", "2")
                        .param("cursor", nextCursor))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations", hasSize(2)));
    }

    @Test
    @WithMockUser(username = "renter@example.com", roles = {"RENTER"})
    @Transactional
    void getRenterReservations_Paginarion_Success() throws Exception {
        //First Page
        String content = mockMvc.perform(get("/v0/renter/reservations")
                                .with(user("renter@example.com").roles("RENTER"))
                                .param("orderBy", "id")
                                .param("limit", "2"))
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.reservations", hasSize(2)))
                        .andReturn().getResponse().getContentAsString();

        Object cursorObj = JsonPath.read(content, "$.next_cursor");
        String nextCursor = cursorObj != null ? cursorObj.toString() : null;

        //Second Page
        mockMvc.perform(get("/v0/renter/reservations")
                        .with(user("renter@example.com").roles("RENTER"))
                        .param("orderBy", "id")
                        .param("limit", "2")
                        .param("cursor", nextCursor))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations", hasSize(2)));
    }

    @Test
    @WithMockUser(username = "renter@example.com", roles = {"RENTER"})
    @Transactional
    void getHostReservations_ForbiddenForRenter() throws Exception {
        mockMvc.perform(get("/v0/host/reservations"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "host@example.com", roles = {"HOST"})
    @Transactional
    void getRenterReservations_ForbiddenForHost() throws Exception {
        mockMvc.perform(get("/v0/renter/reservations"))
                .andExpect(status().isForbidden());
    }
}
