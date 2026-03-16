package rent_a_space_api_clone.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rent_a_space_api_clone.dto.LoginResponse;
import rent_a_space_api_clone.dto.SignupRequest;
import rent_a_space_api_clone.dto.UserResponse;
import rent_a_space_api_clone.entity.User;
import rent_a_space_api_clone.entity.UserProfile;
import rent_a_space_api_clone.exception.UserAlreadyExistsException;
import rent_a_space_api_clone.repository.UserProfileRepository;
import rent_a_space_api_clone.repository.UserRepository;

import java.time.LocalDateTime;
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
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("User with email " + request.getEmail() + " already exists");
        }

        // Create user
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());  // Explicit timing


        User savedUser = userRepository.save(user);

        // Create user profile with the specified role
        UserProfile profile = new UserProfile();
        profile.setUser(savedUser);
        profile.setRole(request.getRole()); // Store role in uppercase
        profile.setNickname(request.getRoleProfile().getNickname());
        profile.setBio(request.getRoleProfile().getBio());
        profile.setEnabled(true);

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

}