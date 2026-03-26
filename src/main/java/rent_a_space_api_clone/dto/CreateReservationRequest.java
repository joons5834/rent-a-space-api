package rent_a_space_api_clone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateReservationRequest(
        @NotNull
        @JsonProperty("subspace_id")
        Long subspaceId,

        @NotNull
        @JsonProperty("starts_at")
        LocalDateTime startsAt,

        @NotNull
        @JsonProperty("ends_at")
        LocalDateTime endsAt,

        @NotBlank
        @JsonProperty("renter_name")
        String renterName,

        @NotBlank
        @JsonProperty("renter_phone")
        String renterPhone,

        @NotBlank
        @Email
        @JsonProperty("renter_email")
        String renterEmail,

        @JsonProperty("custom_request")
        String customRequest
) {}
