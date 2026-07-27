package dev.tower.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes {@link TowerPaths} as a bean so other modules (e.g. tower-persistence) can inject
 * the resolved data directory instead of re-implementing TOWER_HOME resolution. Picked up by
 * tower-api's component scan of the dev.tower base package.
 */
@Configuration
public class TowerHomeAutoConfiguration {

    @Bean
    public TowerPaths towerPaths() throws IOException {
        var home = TowerHome.resolve();
        TowerHomeInitializer.ensureCreated(home);
        return new TowerPaths(home);
    }
}
