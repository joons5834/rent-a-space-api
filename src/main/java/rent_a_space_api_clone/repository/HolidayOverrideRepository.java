package rent_a_space_api_clone.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rent_a_space_api_clone.entity.HolidayOverride;

public interface HolidayOverrideRepository extends JpaRepository<HolidayOverride, Long> {
}