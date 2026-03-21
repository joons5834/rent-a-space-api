package rent_a_space_api_clone.dto;

import java.util.List;

public record SpaceResponse(SpaceData data) {
    public record SpaceData(
            String id,
            String category,
            String name,
            String description,
            Boolean is_open_24,
            String opens_at,
            String closes_at,
            String main_image_url,
            List<String> images_urls,
            String phone1,
            String phone2,
            String email,
            Boolean is_closed_on_public_holidays,
            ClosesOnEvery closes_on_every,
            ClosesOn closes_on
    ) {}

    public record ClosesOnEvery(
            String type,
            List<String> days
    ) {}

    public record ClosesOn(
            String name,
            String start_date,
            String last_date,
            List<String> days
    ) {}
}