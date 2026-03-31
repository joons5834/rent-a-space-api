package rent_a_space_api_clone.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import rent_a_space_api_clone.dto.CreateSpaceRequest;
import rent_a_space_api_clone.dto.CreateSubspaceRequest;
import rent_a_space_api_clone.dto.UpdateSpaceRequest;
import rent_a_space_api_clone.entity.Category;
import rent_a_space_api_clone.entity.Space;
import rent_a_space_api_clone.entity.SpaceImage;
import rent_a_space_api_clone.entity.Subspace;
import rent_a_space_api_clone.enums.Role;
import rent_a_space_api_clone.repository.*;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    @AfterEach
    void tearDown() {
        // Delete in correct order to respect foreign key constraints
        // Child entities first, then parents
        subspaceImageRepository.deleteAll();
        spaceImageRepository.deleteAll();
        subspaceRepository.deleteAll();
        spaceRepository.deleteAll();
        categoryRepository.deleteAll();
        imageRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();
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
        UpdateSpaceRequest updateRequest = new UpdateSpaceRequest();
        updateRequest.setName("Updated Name");
        updateRequest.setDescription("Updated Description");
        updateRequest.setOpensAt("08:00:00");
        updateRequest.setClosesAt("18:00:00");
        updateRequest.setIsVisible(false);

        mockMvc.perform(patch("/v0/spaces/{id}", spaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(updateRequest)))
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
        UpdateSpaceRequest updateRequest = new UpdateSpaceRequest();
        updateRequest.setName("Changed Name");

        mockMvc.perform(patch("/v0/spaces/{id}", spaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(updateRequest)))
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
        UpdateSpaceRequest updateRequest = new UpdateSpaceRequest();

        mockMvc.perform(patch("/v0/spaces/{id}", spaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void updateSpace_NotFound() throws Exception {
        // Try to update non-existing space
        UpdateSpaceRequest updateRequest = new UpdateSpaceRequest();
        updateRequest.setName("New Name");

        mockMvc.perform(patch("/v0/spaces/{id}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(updateRequest)))
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

        UpdateSpaceRequest updateRequest = new UpdateSpaceRequest();
        updateRequest.setMainImageUrl("https://example.com/image1.png");
        updateRequest.setImagesUrls(List.of("https://example.com/image2.png"));

        mockMvc.perform(patch("/v0/spaces/{id}", spaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(updateRequest)))
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
}