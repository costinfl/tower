package dev.tower.persistence.config;

import java.io.IOException;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.tower.config.TowerHomeInitializer;
import dev.tower.config.TowerPaths;

/**
 * Configures the embedded database.
 *
 * ADR-009 / Implementation-Plan.md "Module Responsibilities - tower-persistence": H2 runs in
 * file mode under {@code <data-dir>/db} so data survives restarts, and in PostgreSQL
 * compatibility mode so the deferred move to PostgreSQL stays contained to this module.
 *
 * The DataSource is assembled by driver class name only, never importing an org.h2.* type, in
 * keeping with the Enforced Boundaries rule that no module outside tower-persistence references
 * H2 types - the type exposed here is the standard {@link DataSource}.
 */
@Configuration
public class TowerDataSourceConfiguration {

    private static final String JDBC_URL_TEMPLATE = "jdbc:h2:file:%s;MODE=PostgreSQL";
    private static final String DATABASE_FILE_NAME = "tower";

    @Bean
    public DataSource dataSource(TowerPaths towerPaths) throws IOException {
        Path databaseDirectory = towerPaths.databaseDirectory();
        TowerHomeInitializer.ensureCreated(databaseDirectory);

        String url = JDBC_URL_TEMPLATE.formatted(databaseDirectory.resolve(DATABASE_FILE_NAME));

        return DataSourceBuilder.create()
                .driverClassName("org.h2.Driver")
                .url(url)
                .username("sa")
                .password("")
                .build();
    }
}
