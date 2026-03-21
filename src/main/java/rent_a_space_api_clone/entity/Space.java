package rent_a_space_api_clone.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Entity
@Table(name = "spaces")
@Data
@NoArgsConstructor
public class Space {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_profile_id")
    private UserProfile hostProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    private String description;

    private String phone1;

    private String phone2;

    private String email;

    @Column(name = "close_start")
    private LocalTime closeStart;

    @Column(name = "close_end")
    private LocalTime closeEnd;

    @Column(name = "is_closed_at_public_holidays")
    private Boolean isClosedAtPublicHolidays;

    @Column(name = "is_visible")
    private Boolean isVisible;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "space", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SpacesImage> images;

    @OneToMany(mappedBy = "space", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HolidayRule> holidayRules;

    @OneToMany(mappedBy = "space", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HolidayOverride> holidayOverrides;
}