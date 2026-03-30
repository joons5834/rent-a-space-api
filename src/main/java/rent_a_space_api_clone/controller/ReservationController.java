package rent_a_space_api_clone.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import rent_a_space_api_clone.dto.CancelReservationRequest;
import rent_a_space_api_clone.dto.CreateReservationRequest;
import rent_a_space_api_clone.dto.HostReservationResponse;
import rent_a_space_api_clone.dto.ReservationResponse;
import rent_a_space_api_clone.enums.ReservationStatus;
import rent_a_space_api_clone.service.ReservationService;

@RestController
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @GetMapping("/v0/host/reservations")
    @PreAuthorize("hasRole('HOST')")
    public ResponseEntity<HostReservationResponse> getHostReservations(
            @RequestParam(required = false, defaultValue = "id") String orderBy,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        HostReservationResponse response = reservationService.getHostReservations(orderBy, status, cursor, limit);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/v0/reservation")
    @PreAuthorize("hasRole('RENTER')")
    public ResponseEntity<ReservationResponse> createReservation(@Valid @RequestBody CreateReservationRequest request) {
        ReservationResponse response = reservationService.createReservation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/v0/reservation/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> cancelReservation(@PathVariable Long id,
                                                  @Valid @RequestBody CancelReservationRequest request) {
        reservationService.cancelReservation(id, request);
        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
