package rent_a_space_api_clone.dto;

import java.util.List;

public record UnavailableDatesResponse(UnavailableDatesData data) {
    public record  UnavailableDatesData (
            List<Integer> unavailableDates
    ) {}
}
