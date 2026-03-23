package rent_a_space_api_clone.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import rent_a_space_api_clone.enums.Role;

@Entity
@Table(name = "users_profiles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_role",
                columnNames = {"user_id", "role"}
        ))
@Data
@NoArgsConstructor
@ToString(exclude = "user")  // Exclude to prevent infinite recursion
public class UserProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    private Role role;
    private String nickname;
    private String bio;
    private Boolean enabled;

    public UserProfile(User user, Role role) {
        this.user = user;
        this.role = role;
        this.enabled = true;
    }
}