package rent_a_space_api_clone.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import rent_a_space_api_clone.dto.LoginRequest;
import rent_a_space_api_clone.dto.LoginResponse;
import rent_a_space_api_clone.dto.SignupRequest;
import rent_a_space_api_clone.dto.RoleProfileRequest;
import rent_a_space_api_clone.dto.UserResponse;
import rent_a_space_api_clone.enums.Role;
import rent_a_space_api_clone.exception.GlobalExceptionHandler;
import rent_a_space_api_clone.service.UserService;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserService userService;

    @Mock
    private SecurityContextRepository securityContextRepository;

    @Mock
    private SecurityContextHolderStrategy securityContextHolderStrategy;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;

    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        jsonMapper = new JsonMapper();
    }

    private SignupRequest createValidSignupRequest() {
        SignupRequest signupRequest = new SignupRequest();
        signupRequest.setEmail("john.doe@example.com");
        signupRequest.setPhone("1234567890");
        signupRequest.setPassword("password123456");
        signupRequest.setRole(Role.RENTER);

        RoleProfileRequest roleProfile = new RoleProfileRequest("John", "Test bio");
        signupRequest.setRoleProfile(roleProfile);

        return signupRequest;
    }

    // SIGNUP TESTS
    @Test
    void signup_ShouldReturnCreatedUser_WhenValidRequest() throws Exception {
        // Given
        SignupRequest signupRequest = createValidSignupRequest();

        UserResponse userResponse = new UserResponse(
                1L,
                "john.doe@example.com",
                "1234567890",
                true,
                null,
                Role.RENTER,
                new UserResponse.RoleProfileResponse("John", "Test bio")
        );

        when(userService.createUser(any(SignupRequest.class))).thenReturn(userResponse);

        // When & Then
        mockMvc.perform(post("/v0/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.role").value("RENTER"));

        verify(userService).createUser(any(SignupRequest.class));
    }

    @Test
    void signup_ShouldReturnBadRequest_WhenEmailIsInvalid() throws Exception {
        // Given
        SignupRequest signupRequest = createValidSignupRequest();
        signupRequest.setEmail("invalid-email");

        // When & Then
        mockMvc.perform(post("/v0/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void signup_ShouldReturnBadRequest_WhenPasswordTooShort() throws Exception {
        // Given
        SignupRequest signupRequest = createValidSignupRequest();
        signupRequest.setPassword("short");

        // When & Then
        mockMvc.perform(post("/v0/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void signup_ShouldReturnBadRequest_WhenPasswordTooLong() throws Exception {
        // Given
        SignupRequest signupRequest = createValidSignupRequest();
        signupRequest.setPassword("a".repeat(129)); // 129 characters

        // When & Then
        mockMvc.perform(post("/v0/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void signup_ShouldReturnBadRequest_WhenPhoneTooShort() throws Exception {
        // Given
        SignupRequest signupRequest = createValidSignupRequest();
        signupRequest.setPhone("123456789"); // 9 characters

        // When & Then
        mockMvc.perform(post("/v0/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void signup_ShouldReturnBadRequest_WhenRoleIsNull() throws Exception {
        // Given
        SignupRequest signupRequest = createValidSignupRequest();
        signupRequest.setRole(null);

        // When & Then
        mockMvc.perform(post("/v0/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void signup_ShouldReturnBadRequest_WhenRoleProfileIsNull() throws Exception {
        // Given
        SignupRequest signupRequest = createValidSignupRequest();
        signupRequest.setRoleProfile(null);

        // When & Then
        mockMvc.perform(post("/v0/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    // LOGIN TESTS
    @Test
    void login_ShouldReturnLoginResponse_WhenValidCredentials() throws Exception {
        // Given
        LoginRequest loginRequest = new LoginRequest(
                "john.doe@example.com",
                "password123456"
        );

        LoginResponse loginResponse = new LoginResponse(
                new LoginResponse.LoginData(
                        new LoginResponse.UserData(
                                1L,
                                "john.doe@example.com",
                                List.of(new LoginResponse.RoleData(Role.RENTER, "John"))
                        )
                )
        );

        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authentication);
        when(securityContextHolderStrategy.createEmptyContext()).thenReturn(securityContext);
        when(userService.getUserLoginData(anyString())).thenReturn(Optional.of(loginResponse));

        // When & Then
        mockMvc.perform(post("/v0/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.user.id").value(1L))
                .andExpect(jsonPath("$.data.user.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.data.user.roles[0].role_name").value("RENTER"))
                .andExpect(jsonPath("$.data.user.roles[0].nickname").value("John"));

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(securityContext).setAuthentication(authentication);
        verify(securityContextHolderStrategy).setContext(securityContext);
        verify(securityContextRepository).saveContext(eq(securityContext), any(HttpServletRequest.class), any(HttpServletResponse.class));
        verify(userService).getUserLoginData("john.doe@example.com");
    }

    @Test
    void login_ShouldReturnUnauthorized_WhenInvalidCredentials() throws Exception {
        // Given
        LoginRequest loginRequest = new LoginRequest(
                "john.doe@example.com",
                "wrongpassword"
        );

        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        // When & Then
        mockMvc.perform(post("/v0/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Invalid credentials"));

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verifyNoInteractions(userService);
        verifyNoInteractions(securityContextRepository);
    }

    @Test
    void login_ShouldReturnNotFound_WhenUserNotFoundAfterAuthentication() throws Exception {
        // Given
        LoginRequest loginRequest = new LoginRequest(
                "john.doe@example.com",
                "password123456"
        );

        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authentication);
        when(securityContextHolderStrategy.createEmptyContext()).thenReturn(securityContext);
        when(userService.getUserLoginData(anyString())).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(post("/v0/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isNotFound());

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(userService).getUserLoginData("john.doe@example.com");
    }
}