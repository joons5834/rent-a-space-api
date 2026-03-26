package rent_a_space_api_clone.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rent_a_space_api_clone.dto.CreateReservationRequest;
import rent_a_space_api_clone.dto.ReservationResponse;
import rent_a_space_api_clone.entity.Reservation;
import rent_a_space_api_clone.entity.Space;
import rent_a_space_api_clone.entity.Subspace;
import rent_a_space_api_clone.entity.UserProfile;
import rent_a_space_api_clone.enums.Role;
import rent_a_space_api_clone.exception.ResourceNotFoundException;
import rent_a_space_api_clone.repository.ReservationRepository;
import rent_a_space_api_clone.repository.SpaceRepository;
import rent_a_space_api_clone.repository.SubspaceRepository;
import rent_a_space_api_clone.repository.UserProfileRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final SubspaceRepository subspaceRepository;
    private final SpaceRepository spaceRepository;
    private final UserProfileRepository userProfileRepository;
    private final HolidayGeneratorService holidayGeneratorService;

    @Transactional
    public ReservationResponse createReservation(CreateReservationRequest request) {
        Subspace subspace = subspaceRepository.findById(request.subspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Subspace not found with id: " + request.subspaceId()));

        Space space = subspace.getSpace();
        ZoneId spaceTimezone = space.getTimezone();

        ZonedDateTime startInSpaceTz = request.startsAt().atZone(spaceTimezone);
        ZonedDateTime endInSpaceTz = request.endsAt().atZone(spaceTimezone);

        validateNotOverlapping(subspace.getId(), startInSpaceTz, endInSpaceTz);
        validateSpaceOpen(space, startInSpaceTz, endInSpaceTz);
        validateNotHoliday(space, startInSpaceTz, endInSpaceTz);
        validateDuration(subspace, startInSpaceTz, endInSpaceTz);

        UserProfile renterProfile = getRenterProfile();

        Reservation reservation = new Reservation();
        reservation.setSubspace(subspace);
        reservation.setRenterProfile(renterProfile);
        reservation.setTimezone(spaceTimezone.getId());
        reservation.setStartsAt(startInSpaceTz);
        reservation.setEndsAt(endInSpaceTz);
        reservation.setStatus("confirmed");
        reservation.setRenterName(request.renterName());
        reservation.setRenterPhone(request.renterPhone());
        reservation.setRenterEmail(request.renterEmail());
        reservation.setCustomRequest(request.customRequest());
        reservation.setCreatedAt(OffsetDateTime.now());

        Reservation saved = reservationRepository.save(reservation);
        return new ReservationResponse(new ReservationResponse.ReservationData(saved.getId()));
    }

    private void validateNotOverlapping(Long subspaceId, ZonedDateTime start, ZonedDateTime end) {
        List<Reservation> overlapping = reservationRepository.findOverlappingReservations(subspaceId, start, end);
        if (!overlapping.isEmpty()) {
            throw new IllegalStateException("Time window overlaps with existing reservations");
        }
    }

    private void validateSpaceOpen(Space space, ZonedDateTime start, ZonedDateTime end) {
        LocalTime closeStart = space.getCloseStart();
        LocalTime closeEnd = space.getCloseEnd();

        if (closeStart != null && closeEnd != null) {
            if (closeStart.equals(closeEnd)) {
                return;
            }

            LocalTime reservationStartTime = start.toLocalTime();
            LocalTime reservationEndTime = end.toLocalTime();

            boolean isOpen24 = closeStart.equals(LocalTime.MIDNIGHT) && closeEnd.equals(LocalTime.MIDNIGHT);
            if (isOpen24) {
                return;
            }

            if (closeStart.isBefore(closeEnd)) {
                if (reservationStartTime.isBefore(closeEnd) && reservationEndTime.isAfter(closeStart)) {
                    throw new IllegalStateException("Space is closed during the requested time window");
                }
            } else {
                boolean overlapsClosingPeriod = !reservationStartTime.isBefore(closeStart) || !reservationEndTime.isBefore(closeStart);
                boolean overlapsOpeningPeriod = !reservationStartTime.isBefore(closeEnd) && !reservationEndTime.isAfter(LocalTime.MIDNIGHT);
                if (reservationEndTime.isAfter(closeStart) || reservationStartTime.isBefore(closeEnd)) {
                    throw new IllegalStateException("Space is closed during the requested time window");
                }
            }
        }
    }

    private void validateNotHoliday(Space space, ZonedDateTime start, ZonedDateTime end) {
        LocalDate startDate = start.toLocalDate();
        LocalDate endDate = end.toLocalDate();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            boolean isHoliday = holidayGeneratorService.isHoliday(
                    date,
                    space.getHolidayRules(),
                    space.getHolidayOverrides()
            );
            if (isHoliday) {
                throw new IllegalStateException("Space is closed on holiday for the requested date");
            }
        }
    }

    private void validateDuration(Subspace subspace, ZonedDateTime start, ZonedDateTime end) {
        Duration duration = Duration.between(start, end);
        long hours = duration.toHours();

        if (subspace.getMinHours() != null && hours < subspace.getMinHours()) {
            throw new IllegalStateException("Reservation duration is less than minimum hours allowed: " + subspace.getMinHours());
        }

        if (subspace.getMaxHours() != null && hours > subspace.getMaxHours()) {
            throw new IllegalStateException("Reservation duration exceeds maximum hours allowed: " + subspace.getMaxHours());
        }
    }

    private UserProfile getRenterProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        return userProfileRepository
                .findByUserEmailAndRole(email, Role.RENTER)
                .orElseThrow(
                        () -> new IllegalStateException("User profile not found"));
    }
}
