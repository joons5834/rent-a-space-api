package rent_a_space_api_clone.dto;

import java.time.LocalTime;
import java.util.List;

public record UnavailableHoursResponse(UnavailableHoursData data) {
    public record UnavailableHoursData(
            List<TimeSpan> unavailableHours
    ) {}

    public record TimeSpan(
            LocalTime start,
            LocalTime end
    ) {}

}
