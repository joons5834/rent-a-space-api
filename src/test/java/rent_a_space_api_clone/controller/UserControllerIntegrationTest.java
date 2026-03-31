package rent_a_space_api_clone.controller;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import rent_a_space_api_clone.dto.LoginRequest;
import rent_a_space_api_clone.dto.SignupRequest;
import rent_a_space_api_clone.dto.RoleProfileRequest;
import rent_a_space_api_clone.enums.Role;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    
    @Autowired
    private EntityManager entityManager;

    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    private SignupRequest createValidSignupRequest(String email) {
        SignupRequest signupRequest = new SignupRequest();
        signupRequest.setEmail(email);
        signupRequest.setPhone("1234567890");
        signupRequest.setPassword("integrationtest123");
        signupRequest.setRole(Role.RENTER);

        RoleProfileRequest roleProfile = new RoleProfileRequest("integ_nick", "integrationTestBio");
        signupRequest.setRoleProfile(roleProfile);

        return signupRequest;
    }

    // SIGNUP INTEGRATION TESTS
    @Test
    void signup_ShouldCreateUser_WhenValidRequest() throws Exception {
        // Given
        SignupRequest signupRequest = createValidSignupRequest("integration.test@example.com");

        // When & Then
        mockMvc.perform(post("/v0/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.email").value("integration.test@example.com"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.role").value("RENTER"));
    }

    @Test
    void signup_ShouldReturnBadRequest_WhenDuplicateEmail() throws Exception {
        // Given - Create first user
        SignupRequest firstUser = createValidSignupRequest("duplicate@example.com");

        mockMvc.perform(post("/v0/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(firstUser)))
                .andExpect(status().isCreated());

        // When - Try to create another user with the same email
        SignupRequest duplicateUser = createValidSignupRequest("duplicate@example.com");

        // Then
        mockMvc.perform(post("/v0/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(duplicateUser)))
                .andExpect(status().isConflict());
    }

    @Test
    void signup_ShouldCreateHostUser_WhenHostRole() throws Exception {
        // Given
        SignupRequest signupRequest = createValidSignupRequest("host@example.com");
        signupRequest.setRole(Role.HOST);

        // When & Then
        mockMvc.perform(post("/v0/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("host@example.com"))
                .andExpect(jsonPath("$.role").value("HOST"));
    }

    // LOGIN INTEGRATION TESTS
    @Test
    void login_ShouldAuthenticateUser_WhenValidCredentials() throws Exception {
        // Given - First create a user
        SignupRequest signupRequest = createValidSignupRequest("login.test@example.com");

        mockMvc.perform(post("/v0/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

        // Clear the persistence context to force fresh fetch
        entityManager.flush();
        entityManager.clear();

        // When - Login with the created user
        LoginRequest loginRequest = new LoginRequest(
                "login.test@example.com",
                "integrationtest123"
        );

        // Then
        mockMvc.perform(post("/v0/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.user.email").value("login.test@example.com"))
                .andExpect(jsonPath("$.data.user.id").exists())
                .andExpect(jsonPath("$.data.user.roles").isArray())
                .andExpect(jsonPath("$.data.user.roles[0].role_name").exists())
                .andExpect(jsonPath("$.data.user.roles[0].nickname").exists());
    }

    @Test
    void login_ShouldReturnUnauthorized_WhenInvalidCredentials() throws Exception {
        // Given
        LoginRequest loginRequest = new LoginRequest(
                "nonexistent@example.com",
                "wrongpassword"
        );

        // When & Then
        mockMvc.perform(post("/v0/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_ShouldReturnUnauthorized_WhenWrongPassword() throws Exception {
        // Given - Create a user first
        SignupRequest signupRequest = createValidSignupRequest("wrongpass@example.com");

        mockMvc.perform(post("/v0/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

        // When - Try to login with wrong password
        LoginRequest loginRequest = new LoginRequest(
                "wrongpass@example.com",
                "wrongpassword123"
        );

        // Then
        mockMvc.perform(post("/v0/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }
}