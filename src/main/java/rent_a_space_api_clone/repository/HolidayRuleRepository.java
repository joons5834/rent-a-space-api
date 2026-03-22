package rent_a_space_api_clone.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rent_a_space_api_clone.entity.HolidayRule;
import rent_a_space_api_clone.entity.Space;

import java.util.List;

public interface HolidayRuleRepository extends JpaRepository<HolidayRule, Long> {
    List<HolidayRule> findAllBySpace(Space space);
    void deleteBySpace(Space space);
}