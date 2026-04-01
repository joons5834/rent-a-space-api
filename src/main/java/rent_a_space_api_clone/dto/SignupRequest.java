package rent_a_space_api_clone.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonProperty;
import rent_a_space_api_clone.enums.Role;

public record SignupRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email should be valid")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        String email,

        @Size(min = 10, max = 15, message = "Phone must be between 10 and 15 characters")
        String phone,

        @NotBlank(message = "Password is required")
        @Size(min = 10, max = 128, message = "Password must be between 10 and 128 characters")
        String password,

        @NotNull(message = "Role is required")
        Role role,

        @NotNull(message = "Role profile is required")
        @Valid
        @JsonProperty("role_profile")
        RoleProfileRequest roleProfile
) {
    public SignupRequest withRole(Role role) {
        return new SignupRequest(this.email, this.phone, this.password,
                role, this.roleProfile);
    }

    public SignupRequest withPassword(String password) {
        return new SignupRequest(this.email, this.phone, password,
                this.role, this.roleProfile);
    }

    public SignupRequest withPhone(String phone) {
        return new SignupRequest(this.email, phone, this.password,
                this.role, this.roleProfile);
    }

    public SignupRequest withRoleProfile(RoleProfileRequest roleProfile) {
        return new SignupRequest(this.email, this.phone, this.password,
                this.role, roleProfile);
    }

    public SignupRequest withEmail(String email) {
        return new SignupRequest(email, this.phone, this.password,
                this.role, this.roleProfile);
    }
}