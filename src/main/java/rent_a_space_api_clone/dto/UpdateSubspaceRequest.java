package rent_a_space_api_clone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record UpdateSubspaceRequest(
        String name,
        String description,
        @JsonProperty("main_image_url")
        String mainImageUrl,
        @JsonProperty("images_urls")
        List<String> imagesUrls,
        @JsonProperty("min_hours")
        Integer minHours,
        @JsonProperty("max_hours")
        Integer maxHours,
        @JsonProperty("is_visible")
        Boolean isVisible
) {
        public boolean isEmpty() {
                return name == null && description == null && mainImageUrl == null
                        && imagesUrls == null && minHours == null && maxHours == null
                        && isVisible == null;
        }
}
