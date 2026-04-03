package rent_a_space_api_clone.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import rent_a_space_api_clone.dto.*;
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

    @GetMapping("/v0/spaces")
    public ResponseEntity<SpacesListResponse> getSpaces(
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "10") int limit,
            @RequestParam(required = false) String cursor
    ) {
        SpacesListResponse response = spaceService.getSpacesList(category, limit, cursor);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/v0/host/spaces/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('HOST') and @spaceService.isSpaceOwner(#id, authentication.name))")
    public ResponseEntity<SpaceResponse> getSpaceById(@PathVariable Long id) {
        SpaceResponse response = spaceService.buildSpaceResponse(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/v0/spaces/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('HOST') and @spaceService.isSpaceOwner(#id, authentication.name))")
    public ResponseEntity<SpaceResponse> updateSpace(@PathVariable Long id, @RequestBody UpdateSpaceRequest request) {
        if (request.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        spaceService.updateSpace(id, request);
        SpaceResponse response = spaceService.buildSpaceResponse(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/v0/spaces/{id}/subspaces")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('HOST') and @spaceService.isSpaceOwner(#id, authentication.name))")
    public ResponseEntity<SubspaceResponse> createSubspace(@PathVariable Long id, @Valid @RequestBody CreateSubspaceRequest request) {
        Long subspaceId = spaceService.createSubspace(id, request);
        SubspaceResponse response = spaceService.buildSubspaceResponse(subspaceId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}