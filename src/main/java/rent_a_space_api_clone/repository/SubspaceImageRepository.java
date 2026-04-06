package rent_a_space_api_clone.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rent_a_space_api_clone.entity.Subspace;
import rent_a_space_api_clone.entity.SubspaceImage;

import java.util.List;
import java.util.Optional;

public interface SubspaceImageRepository extends JpaRepository<SubspaceImage, Long> {
    void deleteBySubspace(Subspace subspace);

    @Query("SELECT i.fullUrl FROM SubspaceImage si " +
            "JOIN si.image i " +
            "WHERE si.subspace.id = :subspaceId " +
            "AND si.orderSeq = 0")
    Optional<String> findMainImageUrlOfSubspace(@Param("subspaceId") Long subspaceId);

    @Query("SELECT i.fullUrl FROM SubspaceImage si " +
            "JOIN si.image i " +
            "WHERE si.subspace.id = :subspaceId " +
            "AND si.orderSeq != 0 " +
            "ORDER BY si.orderSeq ASC")
    Optional<List<String>> findSubImagesUrlsOfSubspace(@Param("subspaceId") Long subspaceId);
}
