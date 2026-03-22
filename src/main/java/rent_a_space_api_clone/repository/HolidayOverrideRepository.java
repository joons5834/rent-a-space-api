package rent_a_space_api_clone.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rent_a_space_api_clone.entity.HolidayOverride;
import rent_a_space_api_clone.entity.Space;

import java.util.List;

public interface HolidayOverrideRepository extends JpaRepository<HolidayOverride, Long> {
    List<HolidayOverride> findAllBySpace(Space space);
    void deleteBySpace(Space space);
}