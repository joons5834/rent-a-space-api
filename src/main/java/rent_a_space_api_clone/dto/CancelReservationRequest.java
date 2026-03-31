package rent_a_space_api_clone.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import rent_a_space_api_clone.enums.Role;

public record CancelReservationRequest(
        @NotNull
        Role role,
        @NotBlank
        @Size(max = 254, message = "Cancellation reason must not exceed 254 characters")
        String cancellationReason
) {}
