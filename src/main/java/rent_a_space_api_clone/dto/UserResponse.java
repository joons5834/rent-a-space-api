package rent_a_space_api_clone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import rent_a_space_api_clone.enums.Role;

import java.time.OffsetDateTime;

public record UserResponse(
        Long id,
        String email,
        String phone,
        Boolean enabled,
        @JsonProperty("created_at")
        OffsetDateTime createdAt,
        Role role,
        @JsonProperty("role_profile")
        RoleProfileResponse roleProfile
) {

    public record RoleProfileResponse(
            String nickname,
            String bio
        ) {}
}