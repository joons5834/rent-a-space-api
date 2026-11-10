package rent_a_space_api_clone.schema;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Boots a throwaway PostgreSQL with the *production* DDL
 * (src/main/resources/rent-a-space.sql) and asserts the guarantees H2 could
 * never check: btree_gist, the EXCLUDE constraint, and its predicate.
 *
 * Deliberately does not use Spring: this is a contract test for the SQL file,
 * not for the application.
 */
class ProductionSchemaContractTest {

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("schema_contract")
                    .withUsername("test")
                    .withPassword("test")
                    .withInitScript("rent-a-space.sql");

    private static Connection connection;
    private static long subspaceId;
    private static long renterProfileId;

    @BeforeAll
    static void startDatabase() throws SQLException {
        POSTGRES.start();
        connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());

        long categoryId = insertReturningId(
                "INSERT INTO categories (name) VALUES ('meeting') RETURNING id");
        long hostUserId = insertReturningId(
                "INSERT INTO users (email, password) VALUES ('host@example.com', 'pw') RETURNING id");
        long hostProfileId = insertReturningId(
                "INSERT INTO users_profiles (user_id, role) VALUES (?, 'HOST') RETURNING id", hostUserId);
        long renterUserId = insertReturningId(
                "INSERT INTO users (email, password) VALUES ('renter@example.com', 'pw') RETURNING id");
        renterProfileId = insertReturningId(
                "INSERT INTO users_profiles (user_id, role) VALUES (?, 'RENTER') RETURNING id", renterUserId);
        long spaceId = insertReturningId(
                "INSERT INTO spaces (name, host_profile_id, category_id) VALUES ('Contract Space', ?, ?) RETURNING id",
                hostProfileId, categoryId);
        subspaceId = insertReturningId(
                "INSERT INTO subspaces (name, space_id) VALUES ('Contract Subspace', ?) RETURNING id", spaceId);
    }

    @AfterAll
    static void stopDatabase() throws SQLException {
        if (connection != null) {
            connection.close();
        }
        POSTGRES.stop();
    }

    @BeforeEach
    void clearReservations() throws SQLException {
        execute("DELETE FROM reservations");
    }

    @Test
    @DisplayName("production DDL installs btree_gist and the no_overlapping_reservations EXCLUDE constraint")
    void installsExclusionConstraint() throws SQLException {
        assertThat(scalarString("SELECT extname FROM pg_extension WHERE extname = 'btree_gist'"))
                .isEqualTo("btree_gist");
        assertThat(scalarString(
                "SELECT contype::text FROM pg_constraint WHERE conname = 'no_overlapping_reservations'"))
                .as("contype 'x' means exclusion constraint")
                .isEqualTo("x");
    }

    @Test
    @DisplayName("overlapping CONFIRMED reservations are rejected by the database")
    void rejectsOverlappingConfirmedReservations() throws SQLException {
        insertReservation("2026-03-01T10:00:00+09:00", "2026-03-01T11:00:00+09:00", "CONFIRMED");

        assertThatThrownBy(() -> insertReservation(
                "2026-03-01T10:30:00+09:00", "2026-03-01T11:30:00+09:00", "CONFIRMED"))
                .isInstanceOf(SQLException.class)
                .satisfies(ex -> assertThat(((SQLException) ex).getSQLState())
                        .as("exclusion_violation")
                        .isEqualTo("23P01"));
    }

    @Test
    @DisplayName("a cancelled reservation must not block the same window")
    void cancelledReservationDoesNotBlockItsWindow() throws SQLException {
        long reservationId = insertReservation(
                "2026-03-01T10:00:00+09:00", "2026-03-01T11:00:00+09:00", "CONFIRMED");
        execute("UPDATE reservations SET status = 'CANCELLED' WHERE id = " + reservationId);

        long rebookedId = insertReservation(
                "2026-03-01T10:00:00+09:00", "2026-03-01T11:00:00+09:00", "CONFIRMED");

        assertThat(rebookedId).isPositive();
    }

    @Test
    @DisplayName("back-to-back reservations do not overlap")
    void allowsAdjacentReservations() throws SQLException {
        insertReservation("2026-03-01T10:00:00+09:00", "2026-03-01T11:00:00+09:00", "CONFIRMED");

        assertThat(insertReservation(
                "2026-03-01T11:00:00+09:00", "2026-03-01T12:00:00+09:00", "CONFIRMED"))
                .isPositive();
    }

    private static long insertReservation(String startsAt, String endsAt, String status)
            throws SQLException {
        return insertReturningId(
                "INSERT INTO reservations (subspace_id, renter_profile_id, timezone, starts_at, ends_at, status, created_at) "
                        + "VALUES (?, ?, 'Asia/Seoul', ?, ?, ?, now()) RETURNING id",
                subspaceId, renterProfileId,
                OffsetDateTime.parse(startsAt), OffsetDateTime.parse(endsAt), status);
    }

    private static long insertReturningId(String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private static void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String scalarString(String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
