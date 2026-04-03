package rent_a_space_api_clone.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rent_a_space_api_clone.entity.Space;

import java.util.List;

public interface SpaceRepository extends JpaRepository<Space, Long> {
    @Query("SELECT s FROM Space s " +
            "JOIN s.category c " +
            "WHERE (cast(:category as string) IS NULL OR c.name = :category) " +
            "AND (cast(:cursorId as long) IS NULL OR s.id < :cursorId ) " +
            "ORDER BY s.id DESC")
    List<Space> findSpacesByCategory(
            @Param("category") String category,
            @Param("cursorId") Long cursorId,
            Pageable pageable);
}