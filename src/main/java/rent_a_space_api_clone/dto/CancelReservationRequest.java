package rent_a_space_api_clone.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import rent_a_space_api_clone.enums.Role;

@Data
public class CancelReservationRequest {
    @NotNull
    Role role;

    @NotBlank
    @Size(max = 254, message = "Cancellation reason must not exceed 254 characters")
    String cancellationReason;
}
