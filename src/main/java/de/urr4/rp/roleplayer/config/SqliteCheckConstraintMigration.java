package de.urr4.rp.roleplayer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SQLite has no {@code ALTER TABLE ... DROP/ADD CONSTRAINT}, so a CHECK
 * constraint that Hibernate's SQLite dialect bakes into a table at creation
 * time (to emulate an enum column) is never updated by
 * {@code ddl-auto=update} when the corresponding Java enum later gains new
 * values. Existing databases then fail every save with
 * {@code SQLITE_CONSTRAINT_CHECK} as soon as the application tries to persist
 * one of the new values - this happened for {@code world_extraction_status}
 * once {@code DRAFT_READY}/{@code PUSHING} were added to
 * {@link de.urr4.rp.roleplayer.domain.model.WorldExtractionStatus}.
 * <p>
 * This runs once at startup, after Hibernate has created/updated the schema,
 * and transparently repairs any stale CHECK constraint by following SQLite's
 * documented "recreate the table" procedure: rename the table, recreate it
 * with the corrected constraint, copy the data across, then drop the old
 * table. No data is lost; this is a no-op if the constraint is already
 * up to date (e.g. on a fresh database).
 */
@Component
public class SqliteCheckConstraintMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SqliteCheckConstraintMigration.class);

    private final DataSource dataSource;

    public SqliteCheckConstraintMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        // world_extraction_status gained DRAFT_READY/PUSHING after some
        // databases were already created with the older 4-value constraint.
        repairCheckConstraint("adventures", "world_extraction_status",
                List.of("NONE", "PENDING", "DRAFT_READY", "PUSHING", "DONE", "FAILED"));
    }

    void repairCheckConstraint(String tableName, String columnName, List<String> allowedValues) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            String createSql = fetchTableDdl(connection, tableName);
            if (createSql == null) {
                // Table doesn't exist yet - Hibernate will create it fresh
                // with a constraint matching the current enum values.
                return;
            }

            Pattern checkPattern = Pattern.compile(
                    "check\\s*\\(\\s*\"?" + Pattern.quote(columnName) + "\"?\\s+in\\s*\\(([^)]*)\\)\\s*\\)",
                    Pattern.CASE_INSENSITIVE);
            Matcher matcher = checkPattern.matcher(createSql);
            if (!matcher.find()) {
                // No CHECK constraint on this column - nothing to repair.
                return;
            }

            String currentValues = matcher.group(1);
            boolean upToDate = allowedValues.stream().allMatch(value -> currentValues.contains("'" + value + "'"));
            if (upToDate) {
                return;
            }

            log.warn("Repairing stale CHECK constraint on {}.{}: found ({}), expected ({})",
                    tableName, columnName, currentValues.trim(), String.join(",", allowedValues));

            String newValueList = allowedValues.stream().map(value -> "'" + value + "'").collect(Collectors.joining(","));
            String newCreateSql = matcher.replaceFirst(Matcher.quoteReplacement(
                    "check (" + columnName + " in (" + newValueList + "))"));

            String tmpTableName = tableName + "_constraint_migration_tmp";
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS " + tmpTableName);
                statement.execute("ALTER TABLE " + tableName + " RENAME TO " + tmpTableName);
                statement.execute(newCreateSql);
                statement.execute("INSERT INTO " + tableName + " SELECT * FROM " + tmpTableName);
                statement.execute("DROP TABLE " + tmpTableName);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }

            log.info("Successfully repaired CHECK constraint on {}.{}", tableName, columnName);
        }
    }

    private String fetchTableDdl(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT sql FROM sqlite_master WHERE type='table' AND name='" + tableName + "'")) {
            return resultSet.next() ? resultSet.getString("sql") : null;
        }
    }
}
