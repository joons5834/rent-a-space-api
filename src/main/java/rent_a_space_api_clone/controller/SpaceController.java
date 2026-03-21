package rent_a_space_api_clone.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import rent_a_space_api_clone.dto.CreateSpaceRequest;
import rent_a_space_api_clone.dto.SpaceResponse;
import rent_a_space_api_clone.entity.Space;
import rent_a_space_api_clone.service.SpaceService;

@RestController
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;

    @PostMapping("/v0/spaces")
    @PreAuthorize("hasAnyRole('ADMIN', 'HOST')")
    public ResponseEntity<SpaceResponse> createSpace(@Valid @RequestBody CreateSpaceRequest request) {
        Long spaceId = spaceService.createSpace(request);
        SpaceResponse response = spaceService.buildSpaceResponse(spaceId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}