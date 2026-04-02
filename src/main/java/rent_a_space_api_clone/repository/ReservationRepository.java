package rent_a_space_api_clone.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rent_a_space_api_clone.entity.Reservation;
import rent_a_space_api_clone.enums.ReservationStatus;

import java.time.ZonedDateTime;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("SELECT r FROM Reservation r WHERE r.subspace.id = :subspaceId " +
           "AND r.status != 'cancelled' " +
           "AND r.startsAt < :end AND r.endsAt > :start")
    List<Reservation> findOverlappingReservations(
            @Param("subspaceId") Long subspaceId,
            @Param("start") ZonedDateTime start,
            @Param("end") ZonedDateTime end);

    @Query("SELECT h.id FROM Reservation r left join r.subspace ss " +
            "left join ss.space s " +
            "left join s.hostProfile h " +
            "WHERE r.id = :id")
    Long findHostProfileIdById(@Param("id") Long reservationId);

    @Query("SELECT r FROM Reservation r " +
            "JOIN FETCH r.subspace ss " +
            "JOIN FETCH ss.space s " +
            "WHERE (cast(:hostProfileId as long) IS NULL OR s.hostProfile.id = :hostProfileId) " +
            "AND (cast(:renterProfileId as long) IS NULL OR r.renterProfile.id = :renterProfileId) " +
            "AND (cast(:status as string) IS NULL OR r.status = :status) " +
            "AND (cast(:cursorId as long) IS NULL OR r.id < :cursorId) " +
            "ORDER BY r.id DESC")
    List<Reservation> findReservationsOrderById(
            @Param("hostProfileId") Long hostProfileId,
            @Param("renterProfileId") Long renterProfileId,
            @Param("status") ReservationStatus status,
            @Param("cursorId") Long cursorId,
            org.springframework.data.domain.Pageable pageable);

    @Query("SELECT r FROM Reservation r " +
            "JOIN FETCH r.subspace ss " +
            "JOIN FETCH ss.space s " +
            "WHERE (cast(:hostProfileId as long) IS NULL OR s.hostProfile.id = :hostProfileId) " +
            "AND (cast(:renterProfileId as long) IS NULL OR r.renterProfile.id = :renterProfileId) " +
            "AND (cast(:status as string) IS NULL OR r.status = :status) " +
            "AND (cast(:cursorStartsAt as timestamp) IS NULL OR (r.startsAt < :cursorStartsAt OR (r.startsAt = :cursorStartsAt AND r.id < :cursorId))) " +
            "ORDER BY r.startsAt DESC, r.id DESC")
    List<Reservation> findReservationsOrderByStartsAt(
            @Param("hostProfileId") Long hostProfileId,
            @Param("renterProfileId") Long renterProfileId,
            @Param("status") ReservationStatus status,
            @Param("cursorStartsAt") ZonedDateTime cursorStartsAt,
            @Param("cursorId") Long cursorId,
            org.springframework.data.domain.Pageable pageable);
}
