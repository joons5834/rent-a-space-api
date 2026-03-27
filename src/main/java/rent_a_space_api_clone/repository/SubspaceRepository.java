package rent_a_space_api_clone.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rent_a_space_api_clone.entity.Subspace;

import java.util.Optional;

public interface SubspaceRepository extends JpaRepository<Subspace, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Subspace s WHERE s.id = :id")
    Optional<Subspace> findByIdForUpdate(@Param("id") Long id);
}
