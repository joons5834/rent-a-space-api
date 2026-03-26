package rent_a_space_api_clone.dto;

public record ReservationResponse(ReservationData data) {
    public record ReservationData(Long id) {}
}
