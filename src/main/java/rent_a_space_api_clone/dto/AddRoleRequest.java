package rent_a_space_api_clone.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import rent_a_space_api_clone.entity.Role;

@Data
public class AddRoleRequest {
    @NotNull(message = "Role is required")
    private Role role;

    @NotNull(message = "Role profile is required")
    @Valid
    @JsonProperty("role_profile")
    private RoleProfileRequest roleProfile;
}