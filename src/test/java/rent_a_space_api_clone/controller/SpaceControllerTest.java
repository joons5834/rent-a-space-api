package rent_a_space_api_clone.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import rent_a_space_api_clone.entity.Category;
import rent_a_space_api_clone.repository.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@WithMockUser(username = "host@example.com", roles = {"HOST"})
public class SpaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        spaceRepository.deleteAll();
        categoryRepository.deleteAll();
        imageRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

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
            profile.setRole(rent_a_space_api_clone.entity.Role.HOST);
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
        CreateSpaceRequest request = new CreateSpaceRequest();
        request.setCategory("meeting");
        request.setName("Test Space");
        request.setDescription("A test space");
        request.setIsOpen24(false);
        request.setOpensAt("09:00:00");
        request.setClosesAt("18:00:00");
        request.setMainImageUrl("https://example.com/image1.png");
        request.setImagesUrls(List.of("https://example.com/image2.png"));
        request.setPhone1("1234567890");
        request.setEmail("space@example.com");
        request.setIsClosedOnPublicHolidays(false);
        request.setIsVisible(true);

        mockMvc.perform(post("/v0/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Test Space"))
                .andExpect(jsonPath("$.data.description").value("A test space"))
                .andExpect(jsonPath("$.data.phone1").value("1234567890"))
                .andExpect(jsonPath("$.data.category").value("meeting"));

        assertThat(spaceRepository.findAll()).hasSize(1);
    }

    @Test
    void createSpace_InvalidRequest() throws Exception {
        CreateSpaceRequest request = new CreateSpaceRequest();
        // Missing required fields: name, category

        mockMvc.perform(post("/v0/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSpace_InvalidCategory() throws Exception {
        CreateSpaceRequest request = new CreateSpaceRequest();
        request.setCategory("invalid");
        request.setName("Test Space");

        mockMvc.perform(post("/v0/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSpace_ImageNotFound() throws Exception {
        CreateSpaceRequest request = new CreateSpaceRequest();
        request.setCategory("meeting");
        request.setName("Test Space");
        request.setMainImageUrl("https://nonexistent.com/image.png");

        mockMvc.perform(post("/v0/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createSpace_WithHolidayRules() throws Exception {
        CreateSpaceRequest request = new CreateSpaceRequest();
        request.setCategory("meeting");
        request.setName("Holiday Space");
        request.setClosesOnEvery(new CreateSpaceRequest.ClosesOnEvery());
        request.getClosesOnEvery().setType("every_week");
        request.getClosesOnEvery().setDays(List.of("Mon", "Fri"));

        mockMvc.perform(post("/v0/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.closes_on_every.type").value("every_week"))
                .andExpect(jsonPath("$.data.closes_on_every.days", containsInAnyOrder("Mon", "Fri")))
                .andExpect(jsonPath("$.data.closes_on_every.days.length()").value(2));

        var space = spaceRepository.findAll().iterator().next();
        assertThat(space.getHolidayRules()).isNotEmpty();
    }

    @Test
    void createSpace_24Hours() throws Exception {
        CreateSpaceRequest request = new CreateSpaceRequest();
        request.setCategory("practice");
        request.setName("24 Hour Space");
        request.setIsOpen24(true);
        request.setIsVisible(true);

        mockMvc.perform(post("/v0/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("24 Hour Space"))
                .andExpect(jsonPath("$.data.is_open_24").value(true))
                .andExpect(jsonPath("$.data.opens_at").value("00:00:00"))
                .andExpect(jsonPath("$.data.closes_at").value("00:00:00"));
    }
}