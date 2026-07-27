package dev.tower.persistence.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.tower.config.TowerPaths;

class TowerDataSourceConfigurationTest {

    private final TowerDataSourceConfiguration configuration = new TowerDataSourceConfiguration();

    @TempDir
    Path tempDir;

    @Test
    void createsDatabaseDirectoryAndFileUnderDataDirectory() throws IOException, SQLException {
        TowerPaths paths = new TowerPaths(tempDir.resolve("data"));

        DataSource dataSource = configuration.dataSource(paths);
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.isValid(2)).isTrue();
        }

        assertThat(Files.isDirectory(paths.databaseDirectory())).isTrue();
        try (var files = Files.list(paths.databaseDirectory())) {
            assertThat(files.anyMatch(p -> p.getFileName().toString().startsWith("tower"))).isTrue();
        }
    }

    @Test
    void runsInPostgresCompatibilityMode() throws IOException, SQLException {
        TowerPaths paths = new TowerPaths(tempDir.resolve("data"));
        DataSource dataSource = configuration.dataSource(paths);

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME = 'MODE'")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString(1)).isEqualTo("PostgreSQL");
        }
    }

    @Test
    void dataSurvivesAcrossDataSourceRecreation() throws Exception {
        TowerPaths paths = new TowerPaths(tempDir.resolve("data"));

        DataSource firstRun = configuration.dataSource(paths);
        try (Connection connection = firstRun.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE restart_probe (id INT PRIMARY KEY)");
            statement.execute("INSERT INTO restart_probe (id) VALUES (1)");
        } finally {
            closeIfCloseable(firstRun);
        }

        // Simulate a restart: build a brand new DataSource (and connection pool) against the
        // same directory, only after the first one has released the H2 file lock.
        DataSource secondRun = configuration.dataSource(paths);
        try (Connection connection = secondRun.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM restart_probe")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(1);
        } finally {
            closeIfCloseable(secondRun);
        }
    }

    private static void closeIfCloseable(DataSource dataSource) throws Exception {
        if (dataSource instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }
}
