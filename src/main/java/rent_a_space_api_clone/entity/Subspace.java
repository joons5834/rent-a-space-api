package rent_a_space_api_clone.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "subspaces")
@Data
@NoArgsConstructor
@ToString(exclude = "images")
public class Subspace {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "space_id")
    private Space space;

    private String name;

    private String description;

    @Column(name = "min_hours")
    private Integer minHours;

    @Column(name = "max_hours")
    private Integer maxHours;

    @Column(name = "is_visible")
    private Boolean isVisible;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @OneToMany(mappedBy = "subspace")
    private List<SubspaceImage> images;
}
