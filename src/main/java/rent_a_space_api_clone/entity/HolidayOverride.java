package rent_a_space_api_clone.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "holiday_override")
@Data
@NoArgsConstructor
public class HolidayOverride {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "space_id")
    private Space space;

    @Column(name = "starts_at")
    private LocalDate startsAt;

    @Column(name = "ends_at")
    private LocalDate endsAt;

    @Column(name = "is_closed")
    private Boolean isClosed;

    @Column(name = "day_mask")
    private Short dayMask;

    @Column(name = "priority_weight")
    private Integer priorityWeight;
}