package rent_a_space_api_clone.dto;

import rent_a_space_api_clone.entity.Role;

import java.util.List;

public record LoginResponse(LoginData data) {
    public record LoginData(UserData user) {
    }

    public record UserData(
            Long id,
            String email,
            List<RoleData> roles
    ) {
    }

    public record RoleData(
            Role role_name,
            String nickname
    ) {
    }
}