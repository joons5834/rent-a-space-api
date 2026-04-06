package rent_a_space_api_clone.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import rent_a_space_api_clone.dto.*;
import rent_a_space_api_clone.entity.Space;
import rent_a_space_api_clone.service.SpaceService;

import java.time.LocalDate;

@RestController
@Validated
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

    @GetMapping("/v0/spaces/{id}")
    public ResponseEntity<SpacePublicResponse> getSpacePublicViewById(@PathVariable Long id) {
        SpacePublicResponse response = spaceService.buildSpacePublicResponse(id);
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

    @PatchMapping("/v0/subspaces/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('HOST') and @spaceService.isSubspaceOwner(#id, authentication.name))")
    public ResponseEntity<SubspaceResponse> updateSubspace(@PathVariable Long id, @Valid @RequestBody UpdateSubspaceRequest request) {
        if (request.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        spaceService.updateSubspace(id, request);
        SubspaceResponse response = spaceService.buildSubspaceResponse(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/v0/subspaces/{id}")
    public ResponseEntity<SubspacePublicResponse> getSubspacePublicView(@PathVariable Long id) {
        SubspacePublicResponse response = spaceService.buildPublicSubspaceResponse(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/v0/subspaces/{id}/unavailable-dates")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UnavailableDatesResponse> getUnavailableDates(@PathVariable Long id,
                                                                @RequestParam @Min(1) @Max(9999) int year,
                                                                @RequestParam @Min(1) @Max(12) int month) {
        UnavailableDatesResponse unavailableDatesResponse = spaceService.getHolidaysForSubspace(id, year, month);
        return ResponseEntity.ok(unavailableDatesResponse);
    }

    @GetMapping("/v0/subspaces/{id}/unavailable-hours")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UnavailableHoursResponse> getUnavailableHours(@PathVariable Long id,
                                                                       @RequestParam LocalDate date) {
        UnavailableHoursResponse response = spaceService.getUnavailableHoursForSubspace(id, date);
        return ResponseEntity.ok(response);
    }
}