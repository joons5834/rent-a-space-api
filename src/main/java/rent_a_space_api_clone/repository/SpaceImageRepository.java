package rent_a_space_api_clone.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rent_a_space_api_clone.entity.Space;
import rent_a_space_api_clone.entity.SpaceImage;

import java.util.List;

public interface SpaceImageRepository extends JpaRepository<SpaceImage, Long> {
    List<SpaceImage> findAllBySpace(Space space);
    void deleteBySpace(Space space);
}