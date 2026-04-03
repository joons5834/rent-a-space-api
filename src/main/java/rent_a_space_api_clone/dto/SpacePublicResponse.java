package rent_a_space_api_clone.dto;

import java.time.ZoneId;
import java.util.List;

public record SpacePublicResponse(SpacePublicData data) {
    public record SpacePublicData(
            Long id,
            String name,
            String description,
            List<String> imagesUrls,
            ZoneId timezone,
            List<SubspaceBrief> subspaces
    ) {}

    public record SubspaceBrief(
            Long id,
            String name
    ){}
}
