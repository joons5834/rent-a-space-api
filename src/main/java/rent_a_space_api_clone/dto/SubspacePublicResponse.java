package rent_a_space_api_clone.dto;

import java.util.List;

public record SubspacePublicResponse(SubspacePublicData data) {
    public record SubspacePublicData(
            Long id,
            String name,
            String description,
            List<String> imagesUrls,
            Integer minHours,
            Integer maxHours
    ) {}
}
