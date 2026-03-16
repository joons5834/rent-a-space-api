package rent_a_space_api_clone.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoleProfileRequest {
    @NotBlank(message = "Nickname is required")
    @Size(min = 1, max = 15, message = "Nickname must be between 1 and 15 characters")
    private String nickname;

    @Size(max = 255, message = "Bio must not exceed 255 characters")
    private String bio;
}