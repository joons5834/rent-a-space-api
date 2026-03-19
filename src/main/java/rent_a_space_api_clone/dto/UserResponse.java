package rent_a_space_api_clone.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NoArgsConstructor;
import rent_a_space_api_clone.entity.Role;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    private Long id;
    private String email;
    private String phone;
    private Boolean enabled;
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    private Role role;
    @JsonProperty("role_profile")
    private RoleProfileResponse roleProfile;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RoleProfileResponse {
        private String nickname;
        private String bio;
    }
}