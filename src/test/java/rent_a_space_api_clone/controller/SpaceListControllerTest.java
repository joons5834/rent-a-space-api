package rent_a_space_api_clone.controller;

import com.jayway.jsonpath.JsonPath;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import rent_a_space_api_clone.entity.*;
import rent_a_space_api_clone.enums.Role;
import rent_a_space_api_clone.repository.*;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public class SpaceListControllerTest {

    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ImageRepository imageRepository;
    @Autowired
    private SpaceImageRepository spaceImageRepository;
    @Autowired
    private SpaceRepository spaceRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;
    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        Category meetingCategory = new Category();
        meetingCategory.setName("meeting");
        categoryRepository.save(meetingCategory);

        Category practiceCategory = new Category();
        practiceCategory.setName("practice");
        categoryRepository.save(practiceCategory);

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

        for (int i = 1; i <= 9; i++) {
            Image mainImage = new Image();
            mainImage.setFullUrl("https://example.com/image" + i + ".png");
            imageRepository.save(mainImage);
        }


        for (int i = 1; i <= 9; i++) {
            JSONObject spaceCreateRequest = new JSONObject();
            spaceCreateRequest.put("category",
                    i % 2 == 0 ? "meeting" : "practice");
            spaceCreateRequest.put("name",
                    "Test Space " + i);
            spaceCreateRequest.put("main_image_url",
                    "https://example.com/image" + i + ".png");

            mockMvc.perform(post("/v0/spaces")
                            .with(user("host@example.com").roles("HOST"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(spaceCreateRequest.toString()))
                    .andExpect(status().isCreated());
        }
    }

    @AfterEach
    void tearDown() {
        spaceRepository.deleteAll();
        spaceImageRepository.deleteAll();
        imageRepository.deleteAll();
        categoryRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @WithAnonymousUser()
    void getSpaces_Success() throws Exception {
        mockMvc.perform(get("/v0/spaces")
                .param("limit", "2"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spaces", hasSize(2)))
                .andExpect(jsonPath("$.data.next_cursor").exists());
    }

    @Test
    @WithAnonymousUser
    void getSpaces_FilterByStatus_Success() throws Exception {
        mockMvc.perform(get("/v0/spaces")
                .param("category", "practice"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spaces", hasSize(5)))
                .andExpect(jsonPath("$.data.spaces", everyItem(hasEntry("category", "practice"))));
    }

    @Test
    @WithAnonymousUser
    void getSpaces_Pagination_Success() throws Exception {
        // First Page
        String content = mockMvc.perform(get("/v0/spaces")
                        .param("limit", "2"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spaces", hasSize(2)))
                .andReturn().getResponse().getContentAsString();

        Object cursorObj = JsonPath.read(content, "$.data.next_cursor");
        String nextCursor = cursorObj != null ? cursorObj.toString() : null;

        // Second Page
        mockMvc.perform(get("/v0/spaces")
                        .param("limit", "2")
                        .param("cursor", nextCursor))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spaces", hasSize(2)));
    }
}
