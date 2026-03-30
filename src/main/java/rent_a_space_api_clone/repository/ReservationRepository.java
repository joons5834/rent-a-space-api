package rent_a_space_api_clone.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rent_a_space_api_clone.entity.Reservation;

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
}
