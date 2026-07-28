package dev.tower.persistence.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.tower.config.TowerPaths;

/**
 * Confirms every migration on the module classpath applies cleanly against the H2 DataSource this
 * module builds (issue #3).
 *
 * <p>Nothing here is pinned to a migration count or to a specific version number. An earlier
 * revision asserted "exactly N migrations ran", which failed every time a correct new migration
 * was added and had already been bumped three times. A test that cries wolf on correct changes
 * teaches people to edit the number without reading the failure, which is worse than no test.
 *
 * <p>What matters is the property, not the count: whatever migrations exist all apply, and none
 * are left pending afterwards.
 */
class FlywayBaselineMigrationTest {

    private final TowerDataSourceConfiguration configuration = new TowerDataSourceConfiguration();

    @TempDir
    Path tempDir;

    @Test
    void appliesEveryMigrationOnTheClasspath() throws Exception {
        TowerPaths paths = new TowerPaths(tempDir.resolve("data"));
        DataSource dataSource = configuration.dataSource(paths);

        Flyway flyway = Flyway.configure().dataSource(dataSource).load();
        int discovered = flyway.info().all().length;

        // Guards against passing vacuously if migrations ever stop being found at all.
        assertThat(discovered)
                .as("migrations discovered on the classpath")
                .isPositive();

        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted)
                .as("every discovered migration should have been applied")
                .isEqualTo(discovered);
        assertThat(flyway.info().pending())
                .as("no migration should remain pending after migrating")
                .isEmpty();
        assertThat(flyway.info().applied())
                .extracting(MigrationInfo::getVersion)
                .doesNotContainNull();
    }

    @Test
    void migratingAnAlreadyMigratedDatabaseIsANoOp() throws Exception {
        TowerPaths paths = new TowerPaths(tempDir.resolve("repeat"));
        DataSource dataSource = configuration.dataSource(paths);

        Flyway flyway = Flyway.configure().dataSource(dataSource).load();
        flyway.migrate();

        MigrateResult second = flyway.migrate();

        assertThat(second.success).isTrue();
        assertThat(second.migrationsExecuted)
                .as("a second migrate on an up-to-date schema should apply nothing")
                .isZero();
    }
}
