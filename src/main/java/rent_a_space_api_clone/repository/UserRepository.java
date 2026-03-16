package rent_a_space_api_clone.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rent_a_space_api_clone.entity.User;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.profiles WHERE u.email = :email")
    Optional<User> findByEmailWithProfiles(@Param("email") String email);
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
}