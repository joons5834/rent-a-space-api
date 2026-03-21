package rent_a_space_api_clone.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "holiday_rule")
@Data
@NoArgsConstructor
public class HolidayRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "space_id")
    private Space space;

    @Column(name = "frequency_type")
    private String frequencyType;

    @Column(name = "day_mask")
    private Short dayMask;

    @Column(name = "nth_occurrence")
    private Short nthOccurrence;
}