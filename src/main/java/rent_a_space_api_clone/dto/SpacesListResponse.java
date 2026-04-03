package rent_a_space_api_clone.dto;

import java.util.List;

public record SpacesListResponse(SpacesData data) {
    public record SpacesData(
            List<SpaceBrief> spaces,
            String nextCursor
    ) {
        public record SpaceBrief(
                Long id,
                String name,
                String category,
                String thumbImageUrl
        ) {}
    }
}
