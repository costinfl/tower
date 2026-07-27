package dev.tower.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

/**
 * Loads {@code <data-dir>/config.yml} as an optional Spring property source.
 *
 * Issue #36 / Implementation-Plan.md "Credential Handling": configuration is read from the
 * .tower directory and never from src/main/resources. A first run with no config file must
 * still succeed with defaults, so the file's absence is not an error.
 *
 * Registered via META-INF/spring.factories so it runs before bean definitions are processed,
 * which lets other configuration classes and @ConfigurationProperties see values from
 * config.yml.
 */
public class TowerConfigEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        process(environment, TowerHome.resolve());
    }

    /** Package-visible so tests can supply an explicit directory instead of mutating real env vars. */
    void process(ConfigurableEnvironment environment, Path home) {
        try {
            TowerHomeInitializer.ensureCreated(home);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create Tower data directory at " + home, e);
        }

        MutablePropertySources propertySources = environment.getPropertySources();

        // Expose the resolved home directory so it can be bound (e.g. by tower-persistence)
        // without every consumer re-deriving TOWER_HOME resolution logic.
        propertySources.addLast(new MapPropertySource("towerHome", Map.of("tower.home", home.toString())));

        Path configFile = home.resolve("config.yml");
        if (!Files.isRegularFile(configFile)) {
            return;
        }
        try {
            List<PropertySource<?>> loaded =
                    new YamlPropertySourceLoader().load("towerConfig", new FileSystemResource(configFile));
            for (PropertySource<?> source : loaded) {
                propertySources.addFirst(source);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load Tower configuration file " + configFile, e);
        }
    }
}
