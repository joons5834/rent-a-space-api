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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import rent_a_space_api_clone.dto.*;
import rent_a_space_api_clone.enums.Role;
import rent_a_space_api_clone.service.UserService;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AddRoleIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private EntityManager entityManager;

    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private UserService userService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    private SignupRequest createSignupRequest(String email, Role role) {
        RoleProfileRequest roleProfile = new RoleProfileRequest("TestNick", "Test bio");
        return new SignupRequest(email, "1234567890", "password12345",
                role, roleProfile);
    }

    private AddRoleRequest createAddRoleRequest(Role role, String nickname, String bio) {
        RoleProfileRequest roleProfile = new RoleProfileRequest(nickname, bio);
        return new AddRoleRequest(role, roleProfile);
    }

    private MockHttpSession loginAndGetSession(String email, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest(email, password);

        MvcResult result = mockMvc.perform(post("/v0/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession();
    }

    private Long createUserAndGetId(String email, Role role) throws Exception {
        SignupRequest signupRequest = createSignupRequest(email, role);

        MvcResult result = mockMvc.perform(post("/v0/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        UserResponse userResponse = jsonMapper.readValue(responseBody, UserResponse.class);

        Long id = userResponse.getId();

        if (role.equals(Role.ADMIN)) {
            userService.enableAdminRole(id);
        }
        entityManager.flush();
        entityManager.clear();

        return id;
    }

    @Test
    void addUserRole_ShouldSucceed_WhenOwnerAddsRole() throws Exception {
        // Given - Create a user and login
        Long userId = createUserAndGetId("owner@example.com", Role.RENTER);
        MockHttpSession session = loginAndGetSession("owner@example.com", "password12345");

        AddRoleRequest addRoleRequest = createAddRoleRequest(Role.HOST, "HostNick", "Host bio");

        // When & Then
        mockMvc.perform(post("/v0/users/{id}/roles", userId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(addRoleRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                .andExpect(jsonPath("$.role").value("HOST"))
                .andExpect(jsonPath("$.role_profile.nickname").value("HostNick"))
                .andExpect(jsonPath("$.role_profile.bio").value("Host bio"))
                .andExpect(authenticated().withRoles("RENTER", "HOST"));
    }

    @Test
    void addUserRole_ShouldSucceed_WhenAdminAddsRoleToOtherUser() throws Exception {
        // Given - Create an admin user and a regular user
        Long adminId = createUserAndGetId("admin@example.com", Role.ADMIN);
        Long regularUserId = createUserAndGetId("regular@example.com", Role.RENTER);

        MockHttpSession adminSession = loginAndGetSession("admin@example.com", "password12345");

        AddRoleRequest addRoleRequest = createAddRoleRequest(Role.HOST, "NewHost", "New host bio");

        // When & Then - Admin adds role to another user
        mockMvc.perform(post("/v0/users/{id}/roles", regularUserId)
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(addRoleRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(regularUserId))
                .andExpect(jsonPath("$.email").value("regular@example.com"))
                .andExpect(jsonPath("$.role").value("HOST"));
    }

    @Test
    void addUserRole_ShouldFail_WhenNonOwnerNonAdminTriesToAddRole() throws Exception {
        // Given - Create two regular users
        Long user1Id = createUserAndGetId("user1@example.com", Role.RENTER);
        Long user2Id = createUserAndGetId("user2@example.com", Role.RENTER);

        MockHttpSession user1Session = loginAndGetSession("user1@example.com", "password12345");

        AddRoleRequest addRoleRequest = createAddRoleRequest(Role.HOST, "HostNick", "Host bio");

        // When & Then - User1 tries to add role to User2 (should be forbidden)
        mockMvc.perform(post("/v0/users/{id}/roles", user2Id)
                        .session(user1Session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(addRoleRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void addUserRole_ShouldFail_WhenUnauthenticated() throws Exception {
        // Given - Create a user
        Long userId = createUserAndGetId("user@example.com", Role.RENTER);

        AddRoleRequest addRoleRequest = createAddRoleRequest(Role.HOST, "HostNick", "Host bio");

        // When & Then - Try to add role without authentication
        mockMvc.perform(post("/v0/users/{id}/roles", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(addRoleRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addUserRole_ShouldFail_WhenRoleAlreadyExists() throws Exception {
        // Given - Create a user with HOST role
        Long userId = createUserAndGetId("host@example.com", Role.HOST);
        MockHttpSession session = loginAndGetSession("host@example.com", "password12345");

        AddRoleRequest addRoleRequest = createAddRoleRequest(Role.HOST, "AnotherHostNick", "Another bio");

        // When & Then - Try to add the same role again
        mockMvc.perform(post("/v0/users/{id}/roles", userId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(addRoleRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("User already has role: HOST"));
    }

    @Test
    void addUserRole_ShouldFail_WhenUserNotFound() throws Exception {
        // Given - Create and login as admin
        createUserAndGetId("user@example.com", Role.RENTER);
        MockHttpSession adminSession = loginAndGetSession("user@example.com", "password12345");

        Long nonExistentUserId = 99999L;
        AddRoleRequest addRoleRequest = createAddRoleRequest(Role.HOST, "HostNick", "Host bio");

        // When & Then
        mockMvc.perform(post("/v0/users/{id}/roles", nonExistentUserId)
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(addRoleRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void addUserRole_ShouldFail_WhenRoleIsNull() throws Exception {
        // Given
        Long userId = createUserAndGetId("user@example.com", Role.RENTER);
        MockHttpSession session = loginAndGetSession("user@example.com", "password12345");

        AddRoleRequest addRoleRequest = createAddRoleRequest(null, "Nick", "Bio");

        // When & Then
        mockMvc.perform(post("/v0/users/{id}/roles", userId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(addRoleRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.role").value("Role is required"));
    }

    @Test
    void addUserRole_ShouldFail_WhenRoleProfileIsNull() throws Exception {
        // Given
        Long userId = createUserAndGetId("user@example.com", Role.RENTER);
        MockHttpSession session = loginAndGetSession("user@example.com", "password12345");

        AddRoleRequest addRoleRequest = new AddRoleRequest(Role.HOST, null);


        // When & Then
        mockMvc.perform(post("/v0/users/{id}/roles", userId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(addRoleRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.roleProfile").value("Role profile is required"));
    }

    @Test
    void addUserRole_ShouldFail_WhenNicknameIsEmpty() throws Exception {
        // Given
        Long userId = createUserAndGetId("user@example.com", Role.RENTER);
        MockHttpSession session = loginAndGetSession("user@example.com", "password12345");

        AddRoleRequest addRoleRequest = createAddRoleRequest(Role.HOST, "", "Bio");

        // When & Then
        mockMvc.perform(post("/v0/users/{id}/roles", userId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(addRoleRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.['roleProfile.nickname']").exists());
    }

    @Test
    void addUserRole_ShouldFail_WhenNicknameTooLong() throws Exception {
        // Given
        Long userId = createUserAndGetId("user@example.com", Role.RENTER);
        MockHttpSession session = loginAndGetSession("user@example.com", "password12345");

        String longNickname = "a".repeat(16); // More than 15 characters
        AddRoleRequest addRoleRequest = createAddRoleRequest(Role.HOST, longNickname, "Bio");

        // When & Then
        mockMvc.perform(post("/v0/users/{id}/roles", userId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(addRoleRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.['roleProfile.nickname']").exists());
    }

    @Test
    void addUserRole_ShouldFail_WhenBioTooLong() throws Exception {
        // Given
        Long userId = createUserAndGetId("user@example.com", Role.RENTER);
        MockHttpSession session = loginAndGetSession("user@example.com", "password12345");

        String longBio = "a".repeat(256); // More than 255 characters
        AddRoleRequest addRoleRequest = createAddRoleRequest(Role.HOST, "Nick", longBio);

        // When & Then
        mockMvc.perform(post("/v0/users/{id}/roles", userId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(addRoleRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.['roleProfile.bio']").exists());
    }

    @Test
    void addUserRole_ShouldSucceed_WhenBioIsNull() throws Exception {
        // Given
        Long userId = createUserAndGetId("user@example.com", Role.RENTER);
        MockHttpSession session = loginAndGetSession("user@example.com", "password12345");

        AddRoleRequest addRoleRequest = createAddRoleRequest(Role.HOST, "Nick", null);

        // When & Then
        mockMvc.perform(post("/v0/users/{id}/roles", userId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(addRoleRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role_profile.nickname").value("Nick"))
                .andExpect(jsonPath("$.role_profile.bio").isEmpty());
    }

    @Test
    void addUserRole_ShouldAllowMultipleRolesForSameUser() throws Exception {
        // Given - Create a user with RENTER role
        Long userId = createUserAndGetId("multiuser@example.com", Role.RENTER);
        MockHttpSession session = loginAndGetSession("multiuser@example.com", "password12345");

        // When - Add HOST role
        AddRoleRequest hostRoleRequest = createAddRoleRequest(Role.HOST, "HostNick", "Host bio");

        mockMvc.perform(post("/v0/users/{id}/roles", userId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(hostRoleRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("HOST"));

        // Then - Add ADMIN role
        AddRoleRequest adminRoleRequest = createAddRoleRequest(Role.ADMIN, "AdminNick", "Admin bio");

        mockMvc.perform(post("/v0/users/{id}/roles", userId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(adminRoleRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }
}