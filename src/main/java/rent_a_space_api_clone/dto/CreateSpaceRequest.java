package rent_a_space_api_clone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.time.ZoneId;
import java.util.List;

public record CreateSpaceRequest(
        @NotBlank
        @JsonProperty("category")
        String category,
        @NotBlank
        @JsonProperty("name")
        String name,
        @JsonProperty("description")
        String description,
        @JsonProperty("is_open_24")
        Boolean isOpen24,
        @JsonProperty("opens_at")
        String opensAt,
        @JsonProperty("closes_at")
        String closesAt,
        @JsonProperty("main_image_url")
        String mainImageUrl,
        @JsonProperty("images_urls")
        List<String> imagesUrls,
        @JsonProperty("phone1")
        String phone1,
        @JsonProperty("phone2")
        String phone2,
        @Email
        @JsonProperty("email")
        String email,
        @JsonProperty("is_closed_on_public_holidays")
        Boolean isClosedOnPublicHolidays,
        @JsonProperty("closes_on_every")
        ClosesOnEvery closesOnEvery,
        @JsonProperty("closes_on")
        List<ClosesOn> closesOn,
        @JsonProperty("is_visible")
        Boolean isVisible,
        @JsonProperty("timezone")
        ZoneId timezone
) {
    public record ClosesOnEvery(
            @JsonProperty("type")
            String type,
            @JsonProperty("days")
            List<String> days
    ) {}

    public record ClosesOn(
            @JsonProperty("name")
            String name,
            @JsonProperty("start_date")
            String startDate,
            @JsonProperty("last_date")
            String lastDate,
            @JsonProperty("days")
            List<String> days
    ) {}
}