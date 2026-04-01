package rent_a_space_api_clone.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rent_a_space_api_clone.dto.AddRoleRequest;
import rent_a_space_api_clone.dto.LoginResponse;
import rent_a_space_api_clone.dto.SignupRequest;
import rent_a_space_api_clone.dto.UserResponse;
import rent_a_space_api_clone.enums.Role;
import rent_a_space_api_clone.entity.User;
import rent_a_space_api_clone.entity.UserProfile;
import rent_a_space_api_clone.exception.RoleAlreadyExistsException;
import rent_a_space_api_clone.exception.UserAlreadyExistsException;
import rent_a_space_api_clone.repository.UserProfileRepository;
import rent_a_space_api_clone.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;


    public UserService(UserRepository userRepository,
                       UserProfileRepository userProfileRepository,
                       PasswordEncoder passwordEncoder
                       ) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
    }
    
    public Optional<LoginResponse> getUserLoginData(String email) {
        return userRepository.findByEmailWithProfiles(email)
                .map(this::mapToLoginResponse);
    }
    
    private LoginResponse mapToLoginResponse(User user) {
        List<LoginResponse.RoleData> roles = user.getProfiles().stream()
                .filter(UserProfile::getEnabled)
                .map(profile -> new LoginResponse.RoleData(
                        profile.getRole(),
                        profile.getNickname()
                ))
                .toList();
        
        LoginResponse.UserData userData = new LoginResponse.UserData(
                user.getId(),
                user.getEmail(),
                roles
        );
        
        return new LoginResponse(new LoginResponse.LoginData(userData));
    }

    @Transactional
    public UserResponse createUser(SignupRequest request) {
        // Check if user already exists
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException("User with email " + request.email() + " already exists");
        }

        // Create user
        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setPhone(request.phone());
        user.setEnabled(true);
        user.setCreatedAt(OffsetDateTime.now());  // Explicit timing


        User savedUser = userRepository.save(user);

        // Create user profile with the specified role
        UserProfile profile = new UserProfile();
        profile.setUser(savedUser);
        profile.setRole(request.role()); // Store role in uppercase
        profile.setNickname(request.roleProfile().nickname());
        profile.setBio(request.roleProfile().bio());
        profile.setEnabled(isEnabledByDefault(request.role()));

        UserProfile savedProfile = userProfileRepository.save(profile);

        // Return response
        UserResponse.RoleProfileResponse roleProfileResponse =
                new UserResponse.RoleProfileResponse(
                        savedProfile.getNickname(),
                        savedProfile.getBio()
                );

        return new UserResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getPhone(),
                savedUser.getEnabled(),
                savedUser.getCreatedAt(),
                savedProfile.getRole(),
                roleProfileResponse
        );
    }

    @Transactional
    public UserResponse addUserRole(Long userId, AddRoleRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        // Check if the user already has this role
        if (userProfileRepository.existsByUserIdAndRole(userId, request.role())) {
            throw new RoleAlreadyExistsException("User already has role: " + request.role());
        }

        // Create new user profile with the specified role
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setRole(request.role());
        profile.setNickname(request.roleProfile().nickname());
        profile.setBio(request.roleProfile().bio());
        profile.setEnabled(isEnabledByDefault(request.role()));

        UserProfile savedProfile = userProfileRepository.save(profile);

        // Return response with the new role profile
        UserResponse.RoleProfileResponse roleProfileResponse =
                new UserResponse.RoleProfileResponse(
                        savedProfile.getNickname(),
                        savedProfile.getBio()
                );

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getEnabled(),
                user.getCreatedAt(),
                savedProfile.getRole(),
                roleProfileResponse
        );
    }

    private static boolean isEnabledByDefault(Role role) {
        return !role.equals(Role.ADMIN);
    }

    public boolean isUserOwner(Long userId, String email) {
        return userRepository.findById(userId)
                .map(user -> user.getEmail().equals(email))
                .orElse(false);
    }

    //Only used for testing
    public void enableAdminRole(Long id) {
        userProfileRepository.enableAdminRole(id);
    }
}