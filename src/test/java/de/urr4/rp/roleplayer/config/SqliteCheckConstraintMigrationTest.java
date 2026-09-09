package de.urr4.rp.roleplayer.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqliteCheckConstraintMigrationTest {

    private Path databaseFile;
    private SQLiteDataSource dataSource;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        databaseFile = Files.createTempFile("check-constraint-migration-test", ".db");
        Files.deleteIfExists(databaseFile);
        dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + databaseFile);

        // Mirrors the real schema Hibernate generates for the "adventures"
        // table, but with the stale 4-value constraint that predates
        // DRAFT_READY/PUSHING being added to the WorldExtractionStatus enum.
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE adventures (id varchar(255) not null, chronicle_id varchar(255), created_at timestamp,
                    draft_facts_text clob, ended_at timestamp, name varchar(255), started_at timestamp,
                    status varchar(255) check (status in ('PLANNED','ACTIVE','COMPLETED')),
                    world_extraction_error varchar(255),
                    world_extraction_status varchar(255) check (world_extraction_status in ('NONE','PENDING','DONE','FAILED')),
                    primary key (id))
                    """);
            statement.execute("""
                    INSERT INTO adventures (id, chronicle_id, name, status, world_extraction_status)
                    VALUES ('adv-1', 'chr-1', 'Existing Adventure', 'ACTIVE', 'PENDING')
                    """);
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(databaseFile);
    }

    @Test
    void repairsStaleConstraintAndPreservesExistingData() throws SQLException {
        // Before the repair, saving DRAFT_READY/PUSHING must fail exactly
        // like it does against a real un-migrated production database.
        assertThatThrownBy(() -> updateWorldExtractionStatus("adv-1", "DRAFT_READY"))
                .isInstanceOf(SQLException.class);

        new SqliteCheckConstraintMigration(dataSource).repairCheckConstraint("adventures", "world_extraction_status",
                List.of("NONE", "PENDING", "DRAFT_READY", "PUSHING", "DONE", "FAILED"));

        updateWorldExtractionStatus("adv-1", "DRAFT_READY");
        updateWorldExtractionStatus("adv-1", "PUSHING");

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT chronicle_id, name, status, world_extraction_status FROM adventures WHERE id='adv-1'")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("chronicle_id")).isEqualTo("chr-1");
            assertThat(resultSet.getString("name")).isEqualTo("Existing Adventure");
            assertThat(resultSet.getString("status")).isEqualTo("ACTIVE");
            assertThat(resultSet.getString("world_extraction_status")).isEqualTo("PUSHING");
        }
    }

    @Test
    void isANoOpWhenTheConstraintIsAlreadyUpToDate() throws SQLException {
        new SqliteCheckConstraintMigration(dataSource).repairCheckConstraint("adventures", "world_extraction_status",
                List.of("NONE", "PENDING", "DRAFT_READY", "PUSHING", "DONE", "FAILED"));

        // Running it again against an already-repaired table must not fail
        // or duplicate/lose anything.
        new SqliteCheckConstraintMigration(dataSource).repairCheckConstraint("adventures", "world_extraction_status",
                List.of("NONE", "PENDING", "DRAFT_READY", "PUSHING", "DONE", "FAILED"));

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT count(*) AS total FROM adventures")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt("total")).isEqualTo(1);
        }
    }

    @Test
    void isANoOpWhenTheTableDoesNotExistYet() throws SQLException {
        // Should not throw even for a table that Hibernate hasn't created
        // yet (e.g. a brand-new database on first startup).
        new SqliteCheckConstraintMigration(dataSource).repairCheckConstraint("does_not_exist", "some_column",
                List.of("A", "B"));
    }

    private void updateWorldExtractionStatus(String id, String status) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE adventures SET world_extraction_status = ? WHERE id = ?")) {
            statement.setString(1, status);
            statement.setString(2, id);
            statement.executeUpdate();
        }
    }
}
