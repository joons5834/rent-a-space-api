package rent_a_space_api_clone.service;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import rent_a_space_api_clone.dto.*;
import rent_a_space_api_clone.entity.Reservation;
import rent_a_space_api_clone.entity.Space;
import rent_a_space_api_clone.entity.Subspace;
import rent_a_space_api_clone.entity.UserProfile;
import rent_a_space_api_clone.enums.ReservationStatus;
import rent_a_space_api_clone.enums.Role;
import rent_a_space_api_clone.exception.PermissionDeniedException;
import rent_a_space_api_clone.exception.ResourceNotFoundException;
import rent_a_space_api_clone.repository.ReservationRepository;
import rent_a_space_api_clone.repository.SpaceRepository;
import rent_a_space_api_clone.repository.SubspaceRepository;
import rent_a_space_api_clone.repository.UserProfileRepository;

import java.time.*;
import java.util.List;

import static rent_a_space_api_clone.enums.ReservationStatus.CANCELLED;
import static rent_a_space_api_clone.enums.Role.*;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final SubspaceRepository subspaceRepository;
    private final UserProfileRepository userProfileRepository;
    private final HolidayGeneratorService holidayGeneratorService;

    private final Clock clock;

    @Transactional(readOnly = true)
    public ReservationListResponse getReservations(
            String orderBy,
            ReservationStatus status,
            String cursor,
            int limit,
            Role role) {

        if (role != HOST && role != RENTER) {
            throw new PermissionDeniedException(
                    "Reservation list is only available for host and renter profiles"
            );
        }

        UserProfile userProfile = getEnabledUserProfile(role);

        List<Reservation> reservations;
        Pageable pageable = PageRequest.of(0, limit);

        Long hostProfileId = HOST.equals(role) ? userProfile.getId() : null;
        Long renterProfileId = RENTER.equals(role) ? userProfile.getId() : null;

        if ("starts_at".equals(orderBy)) {
            ZonedDateTime cursorStartsAt = null;
            Long cursorId = null;
            if (cursor != null && !cursor.isEmpty()) {
                String[] parts = cursor.split("\\|");
                if (parts.length == 2) {
                    cursorStartsAt = ZonedDateTime.parse(parts[0]);
                    cursorId = Long.parseLong(parts[1]);
                }
            }
            reservations = reservationRepository.findReservationsOrderByStartsAt(
                    hostProfileId,
                    renterProfileId,
                    status, cursorStartsAt, cursorId, pageable);
        } else {
            // Default: orderBy=id
            Long cursorId = (cursor != null && !cursor.isEmpty()) ? Long.parseLong(cursor) : null;
            reservations = reservationRepository.findReservationsOrderById(
                    hostProfileId,
                    renterProfileId,
                    status, cursorId, pageable);
        }

        String nextCursor = null;
        if (reservations.size() == limit) {
            Reservation last = reservations.get(reservations.size() - 1);
            if ("starts_at".equals(orderBy)) {
                nextCursor = last.getStartsAt().toOffsetDateTime() + "|" + last.getId();
            } else {
                nextCursor = last.getId().toString();
            }
        }

        List<ReservationListResponse.ReservationInfo> reservationInfos = reservations.stream()
                .map(r -> new ReservationListResponse.ReservationInfo(
                        r.getId(),
                        r.getTimezone(),
                        r.getStartsAt().withZoneSameInstant(r.getTimezone()).toLocalDateTime(),
                        r.getEndsAt().withZoneSameInstant(r.getTimezone()).toLocalDateTime(),
                        r.getStatus(),
                        r.getRenterName(),
                        r.getSubspace() != null ? r.getSubspace().getName() : null,
                        (r.getSubspace() != null && r.getSubspace().getSpace() != null)
                                ? r.getSubspace().getSpace().getName() : null
                ))
                .toList();

        return new ReservationListResponse(reservationInfos, nextCursor);
    }

    @Transactional
    public ReservationResponse createReservation(CreateReservationRequest request) {
        Subspace subspace = subspaceRepository.findByIdForUpdate(request.subspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Subspace not found with id: " + request.subspaceId()));

        // Check soft-delete
        if (subspace.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Subspace not found with id: " + request.subspaceId());
        }

        Space space = subspace.getSpace();

        // Check visibility
        if (Boolean.FALSE.equals(space.getIsVisible())) {
            throw new IllegalStateException("Space is not available for reservation");
        }
        if (Boolean.FALSE.equals(subspace.getIsVisible())) {
            throw new IllegalStateException("Subspace is not available for reservation");
        }

        ZoneId spaceTimezone = space.getTimezone();

        ZonedDateTime startInSpaceTz = request.startsAt().atZone(spaceTimezone);
        ZonedDateTime endInSpaceTz = request.endsAt().atZone(spaceTimezone);

        // Check start time is in the future (in the space's timezone)
        ZonedDateTime nowInSpaceTz = ZonedDateTime.now(clock);
        if (!startInSpaceTz.isAfter(nowInSpaceTz)) {
            throw new IllegalArgumentException("Reservation start time must be in the future");
        }

        validateNotOverlapping(subspace.getId(), startInSpaceTz, endInSpaceTz);
        validateSpaceOpen(space, startInSpaceTz, endInSpaceTz);
        validateNotHoliday(space, startInSpaceTz, endInSpaceTz);
        validateDuration(subspace, startInSpaceTz, endInSpaceTz);

        UserProfile renterProfile = getEnabledUserProfile(RENTER);

        // Check renter is enabled
        if (Boolean.FALSE.equals(renterProfile.getEnabled())) {
            throw new IllegalStateException("Renter profile is not active");
        }

        Reservation reservation = new Reservation();
        reservation.setSubspace(subspace);
        reservation.setRenterProfile(renterProfile);
        reservation.setTimezone(spaceTimezone);
        reservation.setStartsAt(startInSpaceTz);
        reservation.setEndsAt(endInSpaceTz);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setRenterName(request.renterName());
        reservation.setRenterPhone(request.renterPhone());
        reservation.setRenterEmail(request.renterEmail());
        reservation.setCustomRequest(request.customRequest());
        reservation.setCreatedAt(OffsetDateTime.now(clock));

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

        // Fast Path: Any event 24 hours or longer guarantees an overlap
        if (Duration.between(start, end).compareTo(Duration.ofHours(24)) >= 0) {
            throw new IllegalStateException("Space is closed during the requested time window");
        }

        if (closeStart == null || closeEnd == null) {
            throw new IllegalStateException("Either one of `opens_at` or `closes_at` is not defined.");
        }

        if (closeStart.equals(closeEnd)) {
            return;
        }

        ZoneId zone = start.getZone();
        LocalDate startDate = start.toLocalDate();

        // Evaluate absolute closing intervals for Yesterday, Today, and Tomorrow.
        // We check yesterday in case an overnight closing window bleeds into today's start time.
        for (int i = -1; i <= 1; i++) {
            LocalDate evalDate = startDate.plusDays(i);
            ZonedDateTime cStart = ZonedDateTime.of(evalDate, closeStart, zone);
            ZonedDateTime cEnd;

            if (closeStart.isBefore(closeEnd)) {
                // Standard closing hours (e.g., Closed 09:00 to 17:00 same day)
                cEnd = ZonedDateTime.of(evalDate, closeEnd, zone);
            } else {
                // Overnight closing hours (e.g., Closed 22:00 to 06:00 the next day)
                cEnd = ZonedDateTime.of(evalDate.plusDays(1), closeEnd, zone);
            }

            // Interval overlap formula: StartA < EndB && StartB < EndA
            if (start.isBefore(cEnd) && cStart.isBefore(end)) {
                throw new IllegalStateException("Space is closed during the requested time window");
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

    private UserProfile getEnabledUserProfile(Role role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        return userProfileRepository
                .findByUserEmailAndRole(email, role)
                .filter(UserProfile::getEnabled)
                .orElseThrow(
                        () -> new PermissionDeniedException("Enabled user profile not found"));
    }

    private List<Role> getRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getAuthorities()
                .stream()
                .map((grantedAuthority) -> grantedAuthority.getAuthority())
                .filter((authority) -> authority.startsWith("ROLE_"))
                .map((authority) -> Role.valueOf(authority.substring(5)) )
                .toList();
    }

    @Transactional
    public void cancelReservation(Long id, @Valid CancelReservationRequest request) {
        OffsetDateTime now = OffsetDateTime.now(clock);

        Reservation reservation = reservationRepository.findByIdForUpdate(id).orElseThrow();

        if (CANCELLED.equals(reservation.getStatus())) {
            throw new IllegalStateException("Reservation already cancelled");
        }

        if (now.toZonedDateTime().isAfter(reservation.getStartsAt())) {
            throw new IllegalStateException("Only able to cancel future reservations.");
        }

        Role role = request.role();
        UserProfile cancellingUser = getEnabledUserProfile(role);
        if (HOST.equals(role)) {
            if (!hostOwnsTheSpace(reservation, cancellingUser)){
                throw new PermissionDeniedException("The host doesn't own the space");
            }
        } else if (RENTER.equals(role)) {
            if (!renterMadeTheReservation(reservation, cancellingUser)) {
                throw new PermissionDeniedException("The renter doesn't own the reservation");
            }
        }

        reservation.setStatus(CANCELLED);
        reservation.setCancellationReason(request.cancellationReason());
        reservation.setCancelledAt(now);
        reservation.setCancelledByProfile(cancellingUser);
    }

    private boolean renterMadeTheReservation(Reservation reservation, UserProfile userProfile) {
        Long renterProfileId = reservation.getRenterProfile().getId();
        Long userProfileId = userProfile.getId();
        return renterProfileId.equals(userProfileId);
    }

    private boolean hostOwnsTheSpace(Reservation reservation, UserProfile userProfile) {
        Long reservationId = reservation.getId();
        Long userProfileId = userProfile.getId();
        Long hostProfileId = reservationRepository.findHostProfileIdById(reservationId);

        return userProfileId.equals(hostProfileId);
    }

    @Transactional
    public ReservationDetailResponse getReservationDetail(Long id) {

        boolean isAllowed = false;

        List<Role> roles = getRoles();

        Reservation reservation = reservationRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Reservation id " + id + "not found"));

        if (roles.contains(ADMIN)) {
            getEnabledUserProfile(ADMIN);
            isAllowed = true;
        } else if (roles.contains(HOST)
                && hostOwnsTheSpace(reservation, getEnabledUserProfile(HOST))) {
            isAllowed = true;
        } else if (roles.contains(RENTER)
                && renterMadeTheReservation(reservation, getEnabledUserProfile(RENTER))) {
            isAllowed = true;
        }

        if (!isAllowed) {
            throw new PermissionDeniedException("Not permitted to see reservation detail.");
        }


        ReservationDetailResponse.ReservationDetail reservationDetail = new ReservationDetailResponse.ReservationDetail(
                reservation.getId(), reservation.getStatus(), reservation.getCreatedAt(),
                reservation.getSubspace().getSpace().getName(), reservation.getSubspace().getName(),
                reservation.getTimezone(),
                reservation.getStartsAt().withZoneSameInstant(reservation.getTimezone()).toLocalDateTime(),
                reservation.getEndsAt().withZoneSameInstant(reservation.getTimezone()).toLocalDateTime(),
                reservation.getCustomRequest(), reservation.getRenterName(), reservation.getRenterEmail(),
                reservation.getRenterPhone());
        return new ReservationDetailResponse(reservationDetail);

    }
}
