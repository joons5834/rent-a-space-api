package rent_a_space_api_clone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class CreateSpaceRequest {
    @NotBlank
    @JsonProperty("category")
    private String category;

    @NotBlank
    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("is_open_24")
    private Boolean isOpen24;

    @JsonProperty("opens_at")
    private String opensAt;

    @JsonProperty("closes_at")
    private String closesAt;

    @JsonProperty("main_image_url")
    private String mainImageUrl;

    @JsonProperty("images_urls")
    private List<String> imagesUrls;

    @JsonProperty("phone1")
    private String phone1;

    @JsonProperty("phone2")
    private String phone2;

    @Email
    @JsonProperty("email")
    private String email;

    @JsonProperty("is_closed_on_public_holidays")
    private Boolean isClosedOnPublicHolidays;

    @JsonProperty("closes_on_every")
    private ClosesOnEvery closesOnEvery;

    @JsonProperty("closes_on")
    private List<ClosesOn> closesOn;

    @JsonProperty("is_visible")
    private Boolean isVisible;

    @Data
    public static class ClosesOnEvery {
        @JsonProperty("type")
        private String type;
        @JsonProperty("days")
        private List<String> days;
    }

    @Data
    public static class ClosesOn {
        @JsonProperty("name")
        private String name;
        @JsonProperty("start_date")
        private String startDate;
        @JsonProperty("last_date")
        private String lastDate;
        @JsonProperty("days")
        private List<String> days;
    }
}