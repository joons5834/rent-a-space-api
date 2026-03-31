package rent_a_space_api_clone.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonProperty;
import rent_a_space_api_clone.enums.Role;

public record AddRoleRequest(
        @NotNull(message = "Role is required") Role role,
        @NotNull(message = "Role profile is required")
        @Valid
        @JsonProperty("role_profile") RoleProfileRequest roleProfile
) {}