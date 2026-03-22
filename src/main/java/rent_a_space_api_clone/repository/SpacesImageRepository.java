package rent_a_space_api_clone.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rent_a_space_api_clone.entity.Space;
import rent_a_space_api_clone.entity.SpacesImage;

import java.util.List;

public interface SpacesImageRepository extends JpaRepository<SpacesImage, Long> {
    List<SpacesImage> findAllBySpace(Space space);
    void deleteBySpace(Space space);
}