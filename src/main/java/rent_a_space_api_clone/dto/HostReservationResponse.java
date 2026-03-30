package rent_a_space_api_clone.dto;

import rent_a_space_api_clone.enums.ReservationStatus;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public record HostReservationResponse(
    List<ReservationInfo> reservations,
    String nextCursor
) {
    public record ReservationInfo(
        Long id,
        ZoneId timezone,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        ReservationStatus status,
        String renterName,
        String subspaceName,
        String spaceName
    ) {}
}
