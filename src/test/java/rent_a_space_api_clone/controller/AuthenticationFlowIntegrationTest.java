
package rent_a_space_api_clone.controller;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
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

import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthenticationFlowIntegrationTest {

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
        signupRequest.setPassword("flowtest123456");
        signupRequest.setRole(Role.RENTER);

        RoleProfileRequest roleProfile = new RoleProfileRequest("flowTestNick", "flowTestBio");
        signupRequest.setRoleProfile(roleProfile);

        return signupRequest;
    }

    @Test
    void fullAuthenticationFlow_ShouldWork() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // 1. Signup
        SignupRequest signupRequest = createValidSignupRequest("flow.test@example.com");

        mockMvc.perform(post("/v0/users")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("flow.test@example.com"));

        entityManager.flush();
        entityManager.clear();

        // 2. Login
        LoginRequest loginRequest = new LoginRequest(
                "flow.test@example.com",
                "flowtest123456"
        );

        mockMvc.perform(post("/v0/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value("flow.test@example.com"))
                .andExpect(jsonPath("$.data.user.id").exists())
                .andExpect(authenticated());

        // 3. Logout
        mockMvc.perform(post("/v0/logout")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(unauthenticated());

    }

    @Test
    void multipleRoleFlow_ShouldWork() throws Exception {
        MockHttpSession renterSession = new MockHttpSession();
        MockHttpSession hostSession = new MockHttpSession();

        // 1. Create RENTER user
        SignupRequest renterRequest = createValidSignupRequest("renter@example.com");
        renterRequest.setRole(Role.RENTER);

        mockMvc.perform(post("/v0/users")
                        .session(renterSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(renterRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("RENTER"));

        // 2. Create HOST user
        SignupRequest hostRequest = createValidSignupRequest("host@example.com");
        hostRequest.setRole(Role.HOST);

        mockMvc.perform(post("/v0/users")
                        .session(hostSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(hostRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("HOST"));

        entityManager.flush();
        entityManager.clear();

        // 3. Login as RENTER
        LoginRequest renterLogin = new LoginRequest("renter@example.com", "flowtest123456");
        mockMvc.perform(post("/v0/login")
                        .session(renterSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(renterLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.roles[0].role_name").value("RENTER"));

        // 4. Login as HOST
        LoginRequest hostLogin = new LoginRequest("host@example.com", "flowtest123456");

        mockMvc.perform(post("/v0/login")
                        .session(hostSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(hostLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.roles[0].role_name").value("HOST"));
    }
}