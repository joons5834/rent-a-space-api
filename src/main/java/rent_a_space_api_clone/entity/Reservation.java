package rent_a_space_api_clone.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import rent_a_space_api_clone.enums.ReservationStatus;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

@Entity
@Table(name = "reservations")
@Data
@NoArgsConstructor
@ToString(exclude = "subspace")
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subspace_id")
    private Subspace subspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "renter_profile_id")
    private UserProfile renterProfile;

    private String timezone;

    @Column(name = "starts_at")
    private ZonedDateTime startsAt;

    @Column(name = "ends_at")
    private ZonedDateTime endsAt;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    @Column(name = "renter_name")
    private String renterName;

    @Column(name = "renter_phone")
    private String renterPhone;

    @Column(name = "renter_email")
    private String renterEmail;

    @Column(name = "custom_request")
    private String customRequest;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "status_updated_at")
    private OffsetDateTime statusUpdatedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private UserProfile cancelledByProfile;
}
