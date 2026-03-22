package rent_a_space_api_clone.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import rent_a_space_api_clone.entity.Role;
import rent_a_space_api_clone.entity.UserProfile;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    boolean existsByUserIdAndRole(Long userId, Role role);

    @Modifying
    @Query(value = "UPDATE users_profiles p SET enabled = true WHERE p.role = 'ADMIN' AND p.user_id = :id"
            , nativeQuery = true)
    void enableAdminRole(Long id);
    Optional<UserProfile> findByUserEmailAndRole(String email, Role role);

    UserProfile findByUserEmail(String email);
}