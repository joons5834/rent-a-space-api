package rent_a_space_api_clone.controller;

import com.jayway.jsonpath.JsonPath;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import rent_a_space_api_clone.dto.CreateSpaceRequest;
import rent_a_space_api_clone.dto.CreateSubspaceRequest;
import rent_a_space_api_clone.dto.UpdateSpaceRequest;
import rent_a_space_api_clone.dto.UpdateSubspaceRequest;
import rent_a_space_api_clone.entity.*;
import rent_a_space_api_clone.enums.Role;
import rent_a_space_api_clone.repository.*;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@WithMockUser(username = "host@example.com", roles = {"HOST"})
public class SpaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private SubspaceRepository subspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubspaceImageRepository subspaceImageRepository;

    @Autowired
    private SpaceImageRepository spaceImageRepository;

    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public Clock fakeClockConfig(){
            Instant fixedInstant = Instant.parse("2026-03-01T10:00:00Z");
            return Clock.fixed(fixedInstant, ZoneId.of("UTC"));
        }
    }

    @AfterEach
    void tearDown() {
        // Delete in correct order to respect foreign key constraints
        // Child entities first, then parents
        jdbcTemplate.execute("DELETE FROM subspaces_images");
        jdbcTemplate.execute("DELETE FROM spaces_images");
        jdbcTemplate.execute("DELETE FROM reservations");
        jdbcTemplate.execute("DELETE FROM subspaces");
        jdbcTemplate.execute("DELETE FROM holiday_override");
        jdbcTemplate.execute("DELETE FROM holiday_rule");
        jdbcTemplate.execute("DELETE FROM spaces");
        jdbcTemplate.execute("DELETE FROM categories");
        jdbcTemplate.execute("DELETE FROM images");
        jdbcTemplate.execute("DELETE FROM users_profiles");
        jdbcTemplate.execute("DELETE FROM users");
    }

    @BeforeEach
    void setUp() {
        // Ensure category exists
        if (categoryRepository.findByName("meeting").isEmpty()) {
            Category category = new Category();
            category.setName("meeting");
            categoryRepository.save(category);
        }
        if (categoryRepository.findByName("practice").isEmpty()) {
            Category category = new Category();
            category.setName("practice");
            categoryRepository.save(category);
        }

        // Ensure host profile exists
        if (userRepository.findByEmail("host@example.com").isEmpty()) {
            var user = new rent_a_space_api_clone.entity.User();
            user.setEmail("host@example.com");
            user.setPassword("password");
            user.setEnabled(true);
            userRepository.save(user);
            var profile = new rent_a_space_api_clone.entity.UserProfile();
            profile.setUser(user);
            profile.setRole(Role.HOST);
            profile.setEnabled(true);
            profile.setNickname("TestHost");
            userProfileRepository.save(profile);
        }

        // Ensure renter profile exists
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

        // Ensure images exist
        if (imageRepository.findByFullUrl("https://example.com/image1.png").isEmpty()) {
            var image1 = new rent_a_space_api_clone.entity.Image();
            image1.setFullUrl("https://example.com/image1.png");
            imageRepository.save(image1);
        }
        if (imageRepository.findByFullUrl("https://example.com/image2.png").isEmpty()) {
            var image2 = new rent_a_space_api_clone.entity.Image();
            image2.setFullUrl("https://example.com/image2.png");
            imageRepository.save(image2);
        }
        if (imageRepository.findByFullUrl("https://example.com/image3.png").isEmpty()) {
            var image3 = new rent_a_space_api_clone.entity.Image();
            image3.setFullUrl("https://example.com/image3.png");
            imageRepository.save(image3);
        }
    }

    @Test
    void createSpace_Success() throws Exception {
        CreateSpaceRequest request = new CreateSpaceRequest(
                "meeting", "Test Space", "A test space",
                false, "09:00:00", "18:00:00",
                "https://example.com/image1.png",
                List.of("https://example.com/image2.png"),
                "1234567890", null, "space@example.com",
                false, null, null,true,
                ZoneId.of("Asia/Seoul")
        );

        mockMvc.perform(post("/v0/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Test Space"))
                .andExpect(jsonPath("$.data.description").value("A test space"))
                .andExpect(jsonPath("$.data.phone1").value("1234567890"))
                .andExpect(jsonPath("$.data.category").value("meeting"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Seoul"));

        assertThat(spaceRepository.findAll()).hasSize(1);
    }

    @Test
    void createSpace_InvalidRequest() throws Exception {
        String jsonRequest = "{}";
        // Missing required fields: name, category

        mockMvc.perform(post("/v0/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSpace_InvalidCategory() throws Exception {
        String jsonRequest = """
                {
                    "category": "invalid",
                    "name": "Test Space",
                    "timezone": "Asia/Seoul"
                }
                """;

        mockMvc.perform(post("/v0/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSpace_ImageNotFound() throws Exception {
        String jsonRequest = """
                {
                    "category": "meeting",
                    "name": "Test Space",
                    "main_image_url": "https://nonexistent.com/image.png",
                    "timezone": "Asia/Seoul"
                }
                """;

        mockMvc.perform(post("/v0/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createSpace_WithHolidayRules() throws Exception {
        String jsonRequest = """
                {
                    "category": "meeting",
                    "name": "Holiday Space",
                    "closes_on_every" : {"type": "every_week", "days": ["Mon", "Fri"]},
                    "timezone": "Asia/Seoul"
                }
                """;

        mockMvc.perform(post("/v0/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.closes_on_every.type").value("every_week"))
                .andExpect(jsonPath("$.data.closes_on_every.days", containsInAnyOrder("Mon", "Fri")))
                .andExpect(jsonPath("$.data.closes_on_every.days.length()").value(2));

        var space = spaceRepository.findAll().iterator().next();
        assertThat(space.getHolidayRules()).isNotEmpty();
    }

    @Test
    void createSpace_24Hours() throws Exception {
        String jsonRequest = """
                {
                    "category": "practice",
                    "name": "24 Hour Space",
                    "is_open_24": true,
                    "is_visible": true,
                    "timezone": "Asia/Seoul"
                }
                """;

        mockMvc.perform(post("/v0/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("24 Hour Space"))
                .andExpect(jsonPath("$.data.is_open_24").value(true))
                .andExpect(jsonPath("$.data.opens_at").value("00:00:00"))
                .andExpect(jsonPath("$.data.closes_at").value("00:00:00"));
    }

    @Test
    @Transactional
    void updateSpace_Success() throws Exception {
        // Create a space to update
        Space space = new Space();
        space.setName("Original Name");
        space.setDescription("Original Description");
        space.setCategory(categoryRepository.findByName("meeting").get());
        space.setHostProfile(userProfileRepository.findByUserEmail("host@example.com"));
        space.setCloseStart(LocalTime.of(9, 0));
        space.setCloseEnd(LocalTime.of(17, 0));
        space.setIsClosedAtPublicHolidays(false);
        space.setIsVisible(true);
        space.setPhone1("0123456789");
        Long spaceId = spaceRepository.save(space).getId();

        // Update some fields
        JSONObject updateRequest = new JSONObject();
        updateRequest.put("name", "Updated Name");
        updateRequest.put("description", "Updated Description");
        updateRequest.put("opens_at" , "08:00:00");
        updateRequest.put("closes_at" , "18:00:00");
        updateRequest.put("is_visible" , false);

        mockMvc.perform(patch("/v0/spaces/{id}", spaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Name"))
                .andExpect(jsonPath("$.data.description").value("Updated Description"))
                .andExpect(jsonPath("$.data.opens_at").value("08:00:00"))
                .andExpect(jsonPath("$.data.closes_at").value("18:00:00"))
                .andExpect(jsonPath("$.data.is_visible").value(false))
                // Check that unchanged fields remain
                .andExpect(jsonPath("$.data.category").value("meeting"))
                .andExpect(jsonPath("$.data.phone1").value("0123456789"));

        // Verify in database
        Space updatedSpace = spaceRepository.findById(spaceId).get();
        assertThat(updatedSpace.getName()).isEqualTo("Updated Name");
        assertThat(updatedSpace.getDescription()).isEqualTo("Updated Description");
        assertThat(updatedSpace.getCloseEnd()).isEqualTo(LocalTime.of(8, 0));
        assertThat(updatedSpace.getCloseStart()).isEqualTo(LocalTime.of(18, 0));
        assertThat(updatedSpace.getIsVisible()).isEqualTo(false);
        // Unchanged
        assertThat(updatedSpace.getPhone1()).isEqualTo("0123456789");
        assertThat(updatedSpace.getIsClosedAtPublicHolidays()).isEqualTo(false);
    }

    @Test
    @Transactional
    void updateSpace_PartialUpdate() throws Exception {
        // Create a space
        Space space = new Space();
        space.setName("Initial Name");
        space.setDescription("Initial Desc");
        space.setCategory(categoryRepository.findByName("meeting").get());
        space.setHostProfile(userProfileRepository.findByUserEmail("host@example.com"));
        space.setIsVisible(true);
        Long spaceId = spaceRepository.save(space).getId();

        // Update only name
        JSONObject updateRequest = new JSONObject();
        updateRequest.put("name", "Changed Name");

        mockMvc.perform(patch("/v0/spaces/{id}", spaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Changed Name"))
                // Description should remain unchanged
                .andExpect(jsonPath("$.data.description").value("Initial Desc"));

        // Verify in DB
        Space updatedSpace = spaceRepository.findById(spaceId).get();
        assertThat(updatedSpace.getName()).isEqualTo("Changed Name");
        assertThat(updatedSpace.getDescription()).isEqualTo("Initial Desc");
    }

    @Test
    @Transactional
    void updateSpace_EmptyRequest() throws Exception {
        // Create a space
        Space space = new Space();
        space.setName("Test Space");
        space.setCategory(categoryRepository.findByName("meeting").get());
        space.setHostProfile(userProfileRepository.findByUserEmail("host@example.com"));
        Long spaceId = spaceRepository.save(space).getId();

        // Empty update request
        String updateRequest = "{}";

        mockMvc.perform(patch("/v0/spaces/{id}", spaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void updateSpace_NotFound() throws Exception {
        // Try to update non-existing space
        JSONObject updateRequest = new JSONObject();
        updateRequest.put("name", "New Name");

        mockMvc.perform(patch("/v0/spaces/{id}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void updateSpace_UpdateImages() throws Exception {
        // Create a space with initial images
        Space space = new Space();
        space.setName("Space with Images");
        space.setCategory(categoryRepository.findByName("meeting").get());
        space.setHostProfile(userProfileRepository.findByUserEmail("host@example.com"));
        Long spaceId = spaceRepository.save(space).getId();

        // Add initial image
        var image1 = new rent_a_space_api_clone.entity.Image();
        image1.setFullUrl("https://example.com/initial.png");
        imageRepository.save(image1);

        var spacesImage1 = new SpaceImage();
        spacesImage1.setSpace(space);
        spacesImage1.setImage(image1);
        spacesImage1.setOrderSeq(1);
        // Assume SpacesImageRepository save
        // (Assuming autowired added, but not shown)

        // Assume SpacesImageRepository is autowired as spacesImageRepository

        // For simplicity, not saving here, just test the update

        JSONObject updateRequest = new JSONObject();
        updateRequest.put("main_image_url", "https://example.com/image1.png");
        updateRequest.put("images_urls", new JSONArray(List.of("https://example.com/image2.png")));

        mockMvc.perform(patch("/v0/spaces/{id}", spaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.main_image_url").value("https://example.com/image1.png"))
                .andExpect(jsonPath("$.data.images_urls.length()").value(1))
                .andExpect(jsonPath("$.data.images_urls[0]").value("https://example.com/image2.png"));
    }

    @Test
    @Transactional
    void createSubspace_Success() throws Exception {
        // Create a space to add subspace to
        Space space = new Space();
        space.setName("Test Space");
        space.setCategory(categoryRepository.findByName("meeting").get());
        space.setHostProfile(userProfileRepository.findByUserEmail("host@example.com"));
        space.setIsVisible(true);
        Long spaceId = spaceRepository.save(space).getId();

        CreateSubspaceRequest request = new CreateSubspaceRequest(
                "Subspace Name",
                "A test subspace description",
                null,
                null,
                1,
                8,
                true
        );

        mockMvc.perform(post("/v0/spaces/{id}/subspaces", spaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Subspace Name"))
                .andExpect(jsonPath("$.data.description").value("A test subspace description"))
                .andExpect(jsonPath("$.data.min_hours").value(1))
                .andExpect(jsonPath("$.data.max_hours").value(8))
                .andExpect(jsonPath("$.data.is_visible").value(true));

        // Verify subspace was created and linked to space
        List<Subspace> subspaces = subspaceRepository.findAll();
        assertThat(subspaces).hasSize(1);
        assertThat(subspaces.get(0).getName()).isEqualTo("Subspace Name");
        assertThat(subspaces.get(0).getSpace().getId()).isEqualTo(spaceId);
    }

    @Test
    @Transactional
    void createSubspace_WithImages() throws Exception {
        // Create a space
        Space space = new Space();
        space.setName("Test Space");
        space.setCategory(categoryRepository.findByName("meeting").get());
        space.setHostProfile(userProfileRepository.findByUserEmail("host@example.com"));
        space.setIsVisible(true);
        Long spaceId = spaceRepository.save(space).getId();

        CreateSubspaceRequest request = new CreateSubspaceRequest(
                "Subspace With Images",
                "Description",
                "https://example.com/image1.png",
                List.of("https://example.com/image2.png"),
                2,
                10,
                true
        );

        mockMvc.perform(post("/v0/spaces/{id}/subspaces", spaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Subspace With Images"));

        // Verify images were created
        List<Subspace> subspaces = subspaceRepository.findAll();
        assertThat(subspaces).hasSize(1);
        assertThat(subspaces.get(0).getImages()).hasSize(2);
    }

    @Test
    @Transactional
    void createSubspace_InvalidRequest_MissingName() throws Exception {
        // Create a space
        Space space = new Space();
        space.setName("Test Space");
        space.setCategory(categoryRepository.findByName("meeting").get());
        space.setHostProfile(userProfileRepository.findByUserEmail("host@example.com"));
        space.setIsVisible(true);
        Long spaceId = spaceRepository.save(space).getId();

        // Create request without required name
        CreateSubspaceRequest request = new CreateSubspaceRequest(
                null, // name is @NotBlank
                "Description",
                null,
                null,
                1,
                8,
                true
        );

        mockMvc.perform(post("/v0/spaces/{id}/subspaces", spaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createSubspace_InvalidRequest_BlankName() throws Exception {
        // Create a space
        Space space = new Space();
        space.setName("Test Space");
        space.setCategory(categoryRepository.findByName("meeting").get());
        space.setHostProfile(userProfileRepository.findByUserEmail("host@example.com"));
        space.setIsVisible(true);
        Long spaceId = spaceRepository.save(space).getId();

        // Create request with blank name
        CreateSubspaceRequest request = new CreateSubspaceRequest(
                "   ", // blank name
                "Description",
                null,
                null,
                1,
                8,
                true
        );

        mockMvc.perform(post("/v0/spaces/{id}/subspaces", spaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createSubspace_SpaceNotFound() throws Exception {
        // Try to create subspace for non-existing space
        CreateSubspaceRequest request = new CreateSubspaceRequest(
                "Subspace Name",
                "Description",
                null,
                null,
                1,
                8,
                true
        );

        mockMvc.perform(post("/v0/spaces/{id}/subspaces", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void createSubspace_SpaceNotOwnedByHost() throws Exception {
        // Create another host user
        var otherUser = new rent_a_space_api_clone.entity.User();
        otherUser.setEmail("other@example.com");
        otherUser.setPassword("password");
        otherUser.setEnabled(true);
        userRepository.save(otherUser);

        var otherProfile = new rent_a_space_api_clone.entity.UserProfile();
        otherProfile.setUser(otherUser);
        otherProfile.setRole(Role.HOST);
        otherProfile.setEnabled(true);
        otherProfile.setNickname("OtherHost");
        userProfileRepository.save(otherProfile);

        // Create a space owned by other host
        Space space = new Space();
        space.setName("Other Host Space");
        space.setCategory(categoryRepository.findByName("meeting").get());
        space.setHostProfile(otherProfile);
        space.setIsVisible(true);
        Long spaceId = spaceRepository.save(space).getId();

        // Try to create subspace (currently authenticated as host@example.com)
        CreateSubspaceRequest request = new CreateSubspaceRequest(
                "Subspace Name",
                "Description",
                null,
                null,
                1,
                8,
                true
        );

        mockMvc.perform(post("/v0/spaces/{id}/subspaces", spaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void createSubspace_EmptyRequest() throws Exception {
        // Create a space
        Space space = new Space();
        space.setName("Test Space");
        space.setCategory(categoryRepository.findByName("meeting").get());
        space.setHostProfile(userProfileRepository.findByUserEmail("host@example.com"));
        space.setIsVisible(true);
        Long spaceId = spaceRepository.save(space).getId();

        // Send empty/minimal request (only name is required)
        CreateSubspaceRequest request = new CreateSubspaceRequest(
                "Valid Name",
                null,
                null,
                null,
                0,
                0,
                null
        );

        mockMvc.perform(post("/v0/spaces/{id}/subspaces", spaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Valid Name"));
    }
    
    @Test
    void updateSubspace_FullRequest_success() throws Exception {
        
        Space space = new Space();
        space.setName("Space 1");
        space.setHostProfile(userProfileRepository.findByUserEmail("host@example.com"));
        Long spaceId = spaceRepository.save(space).getId();

        Subspace subspace = new Subspace();
        subspace.setName("Initial Name");
        subspace.setDescription("Initial Desc");
        subspace.setSpace(space);
        Long subspaceId = subspaceRepository.save(subspace).getId();

        UpdateSubspaceRequest updateSubspaceRequest = new UpdateSubspaceRequest(
                "Updated Subspace",
                "Updated description",
                "https://example.com/image1.png",
                List.of("https://example.com/image2.png", "https://example.com/image3.png"),
                2, 10, true);

        mockMvc.perform(patch("/v0/subspaces/{id}", subspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(updateSubspaceRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Subspace"))
                .andExpect(jsonPath("$.data.description").value("Updated description"))
                .andExpect(jsonPath("$.data.main_image_url").value("https://example.com/image1.png"))
                .andExpect(jsonPath("$.data.images_urls.length()").value(2))
                .andExpect(jsonPath("$.data.images_urls", contains("https://example.com/image2.png", "https://example.com/image3.png")))
                .andExpect(jsonPath("$.data.min_hours").value(2))
                .andExpect(jsonPath("$.data.max_hours").value(10))
                .andExpect(jsonPath("$.data.is_visible").value(true));
    }

    @Test
    void updateSubspace_PartialUpdate_Success() throws Exception {
        Space space = new Space();
        space.setName("Space 1");
        space.setHostProfile(userProfileRepository.findByUserEmail("host@example.com"));
        Long spaceId = spaceRepository.save(space).getId();

        Subspace subspace = new Subspace();
        subspace.setName("Subspace 1");
        subspace.setDescription("Initial Desc.");
        subspace.setSpace(space);
        Long subspaceId = subspaceRepository.save(subspace).getId();

        JSONObject updateRequest = new JSONObject();
        updateRequest.put("images_urls", new JSONArray(
                List.of("https://example.com/image2.png", "https://example.com/image3.png")
        ));

        mockMvc.perform(patch("/v0/subspaces/{id}", subspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images_urls.length()").value(2))
                .andExpect(jsonPath("$.data.images_urls", contains("https://example.com/image2.png", "https://example.com/image3.png")))
                .andExpect(jsonPath("$.data.name").value("Subspace 1"))
                .andExpect(jsonPath("$.data.description").value("Initial Desc."));
    }

    @Test
    void updateSubspace_PartialMainImageUpdate_Success() throws Exception {

        Space space = new Space();
        space.setName("Space 1");
        space.setHostProfile(userProfileRepository.findByUserEmail("host@example.com"));
        Long spaceId = spaceRepository.save(space).getId();

        Subspace subspace = new Subspace();
        subspace.setName("Subspace 1");
        subspace.setDescription("Initial Desc.");
        subspace.setSpace(space);
        Long subspaceId = subspaceRepository.save(subspace).getId();

        for (int i = 1 ; i <= 2; i++) {
            var image = new Image();
            image.setFullUrl("https://example.com/initial" + i + ".png");
            imageRepository.save(image);

            var spacesImage = new SubspaceImage();
            spacesImage.setSubspace(subspace);
            spacesImage.setImage(image);
            spacesImage.setOrderSeq(i);
            subspaceImageRepository.save(spacesImage);
        }

        JSONObject updateRequest = new JSONObject();
        updateRequest.put("main_image_url", "https://example.com/image1.png");

        mockMvc.perform(patch("/v0/subspaces/{id}", subspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.main_image_url").value("https://example.com/image1.png"))
                .andExpect(jsonPath("$.data.images_urls.length()").value(2))
                .andExpect(jsonPath("$.data.images_urls", contains("https://example.com/initial1.png", "https://example.com/initial2.png")))
                .andExpect(jsonPath("$.data.name").value("Subspace 1"))
                .andExpect(jsonPath("$.data.description").value("Initial Desc."));
    }

    @Test
    void publicViewOfSpace_Success() throws Exception {
        JSONObject createSpaceRequestObj = new JSONObject();
        createSpaceRequestObj.put("category", "practice");
        createSpaceRequestObj.put("name", "Test Space");
        createSpaceRequestObj.put("is_visible", true);
        createSpaceRequestObj.put("main_image_url", "https://example.com/image1.png");
        createSpaceRequestObj.put("images_urls", new JSONArray(
                List.of("https://example.com/image2.png")
        ));
        createSpaceRequestObj.put("timezone", "Asia/Seoul");

        MvcResult mvcResult = mockMvc.perform(post("/v0/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSpaceRequestObj.toString()))
                .andExpect(status().isCreated())
                .andReturn();

        String spaceResponse = mvcResult.getResponse().getContentAsString();
        Object spaceIdObj = JsonPath.read(spaceResponse, "$.data.id");
        String spaceId = spaceIdObj != null ? spaceIdObj.toString() : null;


        String createSubspaceRequest = """
                {
                    "name" : "subspace 1",
                    "is_visible" : true
                }
                """;

        mockMvc.perform(post("/v0/spaces/{id}/subspaces", spaceId)
                        .with(user("host@example.com").roles("HOST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSubspaceRequest))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/v0/spaces/{id}", spaceId)
                        .with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Test Space"))
                .andExpect(jsonPath("$.data.images_urls.length()").value(2))
                .andExpect(jsonPath("$.data.images_urls",
                        contains("https://example.com/image1.png", "https://example.com/image2.png")))
                .andExpect(jsonPath("$.data.subspaces[0].name").value("subspace 1"));

    }

    @Test
    public void publicViewOfSubspace_Success() throws Exception {
        JSONObject createSpaceRequestObj = new JSONObject();
        createSpaceRequestObj.put("category", "practice");
        createSpaceRequestObj.put("name", "Test Space");
        createSpaceRequestObj.put("is_visible", true);
        createSpaceRequestObj.put("timezone", "Asia/Seoul");

        MvcResult mvcResult = mockMvc.perform(post("/v0/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSpaceRequestObj.toString()))
                .andExpect(status().isCreated())
                .andReturn();

        String spaceResponse = mvcResult.getResponse().getContentAsString();
        Object spaceIdObj = JsonPath.read(spaceResponse, "$.data.id");
        String spaceId = spaceIdObj != null ? spaceIdObj.toString() : null;

        String createSubspaceRequest = """
                {
                    "name" : "subspace 1",
                    "description" : "Test subspace 1",
                    "main_image_url": "https://example.com/image1.png",
                    "images_urls": ["https://example.com/image2.png", "https://example.com/image3.png"],
                    "min_hours" : 2,
                    "max_hours" : 4,
                    "is_visible" : true
                }
                """;

        MvcResult subspaceMvcResult = mockMvc.perform(post("/v0/spaces/{id}/subspaces", spaceId)
                        .with(user("host@example.com").roles("HOST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSubspaceRequest))
                .andExpect(status().isCreated())
                .andReturn();

        String subspaceResponse = subspaceMvcResult.getResponse().getContentAsString();
        Object subspaceIdObj = JsonPath.read(subspaceResponse, "$.data.id");
        String subspaceId = subspaceIdObj != null ? subspaceIdObj.toString() : null;

        mockMvc.perform(get("/v0/subspaces/{id}", subspaceId)
                        .with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("subspace 1"))
                .andExpect(jsonPath("$.data.description").value("Test subspace 1"))
                .andExpect(jsonPath("$.data.images_urls.length()").value(3))
                .andExpect(jsonPath("$.data.images_urls",
                        contains("https://example.com/image1.png",
                                "https://example.com/image2.png",
                                "https://example.com/image3.png")))
                .andExpect(jsonPath("$.data.min_hours").value(2))
                .andExpect(jsonPath("$.data.max_hours").value(4));

    }

    @Test
    public void unavailableDatesOfaSubspace_Success() throws Exception {
        String createSpaceRequest = """
                {
                    "category": "meeting",
                    "name": "MySpace 1",
                    "is_closed_on_public_holidays": false,
                    "closes_on_every": {
                        "type": "every_week",
                        "days": [
                            "Mon",
                            "Tue"
                        ]
                    },
                    "closes_on": [{
                        "name": "family emergency",
                        "start_date": "2026-03-10",
                        "last_date": "2026-03-20",
                        "days": [
                            "Mon",
                            "Tue",
                            "Wed",
                            "Thu",
                            "Fri",
                            "Sat",
                            "Sun"
                        ]
                    }],
                    "timezone": "Asia/Seoul"
                }
                """;
        MvcResult mvcResult = mockMvc.perform(post("/v0/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSpaceRequest))
                .andExpect(status().isCreated())
                .andReturn();

        String spaceResponse = mvcResult.getResponse().getContentAsString();
        Object spaceIdObj = JsonPath.read(spaceResponse, "$.data.id");
        String spaceId = spaceIdObj != null ? spaceIdObj.toString() : null;

        String createSubspaceRequest = """
                { "name" : "subspace 1"}
                """;

        MvcResult subspaceMvcResult = mockMvc.perform(post("/v0/spaces/{id}/subspaces", spaceId)
                        .with(user("host@example.com").roles("HOST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSubspaceRequest))
                .andExpect(status().isCreated())
                .andReturn();

        String subspaceResponse = subspaceMvcResult.getResponse().getContentAsString();
        Object subspaceIdObj = JsonPath.read(subspaceResponse, "$.data.id");
        String subspaceId = subspaceIdObj != null ? subspaceIdObj.toString() : null;

        mockMvc.perform(get("/v0/subspaces/{id}/unavailable-dates", subspaceId)
                        .with(user("host@example.com").roles("HOST"))
                        .param("year", "2026")
                        .param("month", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unavailable_dates",
                        contains(getHolidays())))
                .andExpect(jsonPath("$.data.unavailable_dates.length()").value(getHolidays().length));

    }

    private static Integer @NonNull [] getHolidays() {
        return new Integer[]{2, 3, 9, 10, 11, 12, 13, 14, 15, 16
                , 17, 18, 19, 20, 23, 24, 30, 31};
    }

    @Test
    public void unavailableDatesOfaSubspace2_Success() throws Exception {
        String createSpaceRequest = """
                {
                    "category": "meeting",
                    "name": "MySpace 1",
                    "is_closed_on_public_holidays": false,
                    "closes_on_every": {
                        "type": "every_odd_week",
                        "days": [
                            "Mon",
                            "Tue"
                        ]
                    },
                    "closes_on": [{
                        "name": "family emergency",
                        "start_date": "2026-03-10",
                        "last_date": "2026-03-20",
                        "days": [
                            "Mon",
                            "Tue",
                            "Wed",
                            "Thu",
                            "Fri",
                            "Sat",
                            "Sun"
                        ]
                    }],
                    "timezone": "Asia/Seoul"
                }
                """;
        MvcResult mvcResult = mockMvc.perform(post("/v0/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSpaceRequest))
                .andExpect(status().isCreated())
                .andReturn();

        String spaceResponse = mvcResult.getResponse().getContentAsString();
        Object spaceIdObj = JsonPath.read(spaceResponse, "$.data.id");
        String spaceId = spaceIdObj != null ? spaceIdObj.toString() : null;

        String createSubspaceRequest = """
                { "name" : "subspace 1"}
                """;

        MvcResult subspaceMvcResult = mockMvc.perform(post("/v0/spaces/{id}/subspaces", spaceId)
                        .with(user("host@example.com").roles("HOST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSubspaceRequest))
                .andExpect(status().isCreated())
                .andReturn();

        String subspaceResponse = subspaceMvcResult.getResponse().getContentAsString();
        Object subspaceIdObj = JsonPath.read(subspaceResponse, "$.data.id");
        String subspaceId = subspaceIdObj != null ? subspaceIdObj.toString() : null;
        Integer[] holidays = {2, 3, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 30, 31};

        mockMvc.perform(get("/v0/subspaces/{id}/unavailable-dates", subspaceId)
                        .with(user("host@example.com").roles("HOST"))
                        .param("year", "2026")
                        .param("month", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unavailable_dates",
                        contains(holidays)))
                .andExpect(jsonPath("$.data.unavailable_dates.length()").value(holidays.length));

    }

    @Test
    public void unavailableHoursOfaSubspace_Success() throws Exception {
        String createSpaceRequest = """
                {
                    "category": "meeting",
                    "name": "MySpace 1",
                    "description": "This is MySpace 1",
                    "is_open_24": false,
                    "opens_at": "09:00:00",
                    "closes_at": "18:00:00",
                    "timezone": "Asia/Seoul",
                    "is_visible": true
                }
                """;

        MvcResult mvcSpaceResult = mockMvc.perform(post("/v0/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSpaceRequest))
                .andExpect(status().isCreated())
                .andReturn();

        String spaceResponse = mvcSpaceResult.getResponse().getContentAsString();
        Object spaceIdObj = JsonPath.read(spaceResponse, "$.data.id");
        String spaceId = spaceIdObj != null ? spaceIdObj.toString() : null;

        String createSubspaceRequest = """
                {"name" : "subspace 1"}
                """;

        MvcResult subspaceMvcResult = mockMvc.perform(post("/v0/spaces/{id}/subspaces", spaceId)
                        .with(user("host@example.com").roles("HOST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSubspaceRequest))
                .andExpect(status().isCreated())
                .andReturn();

        String subspaceResponse = subspaceMvcResult.getResponse().getContentAsString();
        Object subspaceIdObj = JsonPath.read(subspaceResponse, "$.data.id");
        String subspaceId = subspaceIdObj != null ? subspaceIdObj.toString() : null;

        String createReservationRequest = """
                {"subspace_id": %s,
                "starts_at": "2026-03-04T15:00:00",
                "ends_at": "2026-03-04T16:00:00",
                "renter_name": "Mike",
                "renter_phone": "01012345678",
                "renter_email": "example@example.com",
                "custom_request": "I need 2 chairs."}
                """;

        createReservationRequest = String.format(createReservationRequest, subspaceId);

        mockMvc.perform(post("/v0/reservation")
                        .with(user("renter@example.com").roles("RENTER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReservationRequest))
                .andExpect(status().isCreated());


        mockMvc.perform(get("/v0/subspaces/{id}/unavailable-hours", subspaceId)
                .with(user("renter@example.com").roles("RENTER"))
                .param("date", "2026-03-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unavailable_hours.length()").value(2))
                .andExpect(jsonPath("$.data.unavailable_hours[0].start").value("18:00:00"))
                .andExpect(jsonPath("$.data.unavailable_hours[0].end").value("09:00:00"))
                .andExpect(jsonPath("$.data.unavailable_hours[1].start").value("15:00:00"))
                .andExpect(jsonPath("$.data.unavailable_hours[1].end").value("16:00:00"));

    }

    @Test
    public void deleteASubspace_Success() throws Exception {
        Space space = new Space();
        space.setName("Space 1");
        space.setHostProfile(userProfileRepository.findByUserEmail("host@example.com"));
        Long spaceId = spaceRepository.save(space).getId();

        Subspace subspace = new Subspace();
        subspace.setName("Subspace 1");
        subspace.setDescription("Subspace1 Desc.");
        subspace.setIsVisible(true);
        subspace.setSpace(space);
        Long subspaceId = subspaceRepository.save(subspace).getId();

        mockMvc.perform(get("/v0/spaces/{id}", spaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subspaces").isNotEmpty());

        mockMvc.perform(delete("/v0/subspaces/{id}", subspaceId)
                        .with(user("host@example.com").roles("HOST")))
                .andExpect(status().isOk());


        Assertions.assertThrows(NoSuchElementException.class, () ->
                subspaceRepository.findById(subspaceId).orElseThrow());

        Integer softDeletedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subspaces WHERE id = ? AND deleted_at IS NOT NULL",
                Integer.class,
                subspaceId);
        assertThat(softDeletedRows).isEqualTo(1);

        mockMvc.perform(get("/v0/spaces/{id}", spaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subspaces").isEmpty());

        mockMvc.perform(get("/v0/host/subspaces/{id}", subspaceId)
                        .with(user("host@example.com").roles("HOST")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/v0/subspaces/{id}", subspaceId)
                        .with(user("host@example.com").roles("HOST")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/v0/subspaces/{id}/unavailable-dates", subspaceId)
                        .param("year", "2026")
                        .param("month", "3")
                        .with(user("renter@example.com").roles("RENTER")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/v0/subspaces/{id}/unavailable-hours", subspaceId)
                        .param("date", "2026-03-04")
                        .with(user("renter@example.com").roles("RENTER")))
                .andExpect(status().isNotFound());

        String updateSubspaceRequest = """
                {"name": "updated Subspace"}
                """;

        mockMvc.perform(patch("/v0/subspaces/{id}", subspaceId)
                        .with(user("host@example.com").roles("HOST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateSubspaceRequest))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/v0/subspaces/{id}", subspaceId)
                        .with(user("host@example.com").roles("HOST")))
                .andExpect(status().isForbidden());

    }

}