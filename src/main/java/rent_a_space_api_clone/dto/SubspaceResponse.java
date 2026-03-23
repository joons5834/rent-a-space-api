package rent_a_space_api_clone.dto;

import java.util.List;

public record SubspaceResponse(SubspaceData data) {
    public record SubspaceData(
        Long id,
        String name,
        String description,
        String mainImageUrl,
        List<String> imagesUrls,
        Integer minHours,
        Integer maxHours,
        Boolean isVisible
    ) {}
}
