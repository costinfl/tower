package dev.tower.persistence.support;

import java.io.IOException;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.tower.config.TowerPaths;
import dev.tower.persistence.config.TowerDataSourceConfiguration;

/**
 * Builds a fully migrated, per-test H2 DataSource/JdbcClient pair the same way the application
 * assembles one at runtime (TowerDataSourceConfiguration + Flyway), without requiring a Spring
 * ApplicationContext in the persistence adapter tests.
 */
public final class PersistenceTestSupport {

    private PersistenceTestSupport() {
    }

    public static JdbcClient migratedJdbcClient(Path tempDir) throws IOException {
        return JdbcClient.create(migratedDataSource(tempDir));
    }

    public static DataSource migratedDataSource(Path tempDir) throws IOException {
        TowerPaths paths = new TowerPaths(tempDir.resolve("data"));
        DataSource dataSource = new TowerDataSourceConfiguration().dataSource(paths);
        Flyway.configure().dataSource(dataSource).load().migrate();
        return dataSource;
    }
}
