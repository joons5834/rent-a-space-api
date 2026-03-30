package rent_a_space_api_clone.dto;

import rent_a_space_api_clone.enums.ReservationStatus;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record ReservationDetailResponse(ReservationDetail data) {
    public record ReservationDetail(
            Long id,
            ReservationStatus status,
            OffsetDateTime reservedAt,
            String spaceName,
            String subspaceName,
            ZoneId timezone,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            String customRequest,
            String renterName,
            String renterEmail,
            String renterPhone
    ) {}
}
