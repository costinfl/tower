package dev.tower.persistence.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.tower.config.TowerPaths;

/**
 * Confirms the baseline migration (V1__baseline.sql, on the module classpath) applies cleanly
 * against the H2 DataSource this module builds. Issue #3: Flyway migrations under
 * tower-persistence/src/main/resources/db/migration.
 */
class FlywayBaselineMigrationTest {

    private final TowerDataSourceConfiguration configuration = new TowerDataSourceConfiguration();

    @TempDir
    Path tempDir;

    @Test
    void appliesBaselineMigration() throws Exception {
        TowerPaths paths = new TowerPaths(tempDir.resolve("data"));
        DataSource dataSource = configuration.dataSource(paths);

        Flyway flyway = Flyway.configure().dataSource(dataSource).load();
        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(1);

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT \"version\" FROM \"flyway_schema_history\" WHERE \"version\" = '1'")) {
            assertThat(resultSet.next()).isTrue();
        }
    }
}
