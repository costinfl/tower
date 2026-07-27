package dev.tower.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

class TowerConfigEnvironmentPostProcessorTest {

    private final TowerConfigEnvironmentPostProcessor processor = new TowerConfigEnvironmentPostProcessor();

    @TempDir
    Path tempDir;

    @Test
    void createsDataDirectoryAndSucceedsWithoutConfigFile() {
        Path home = tempDir.resolve("fresh-home");
        StandardEnvironment environment = new StandardEnvironment();

        processor.process(environment, home);

        assertThat(Files.isDirectory(home)).isTrue();
        assertThat(environment.getProperty("tower.home")).isEqualTo(home.toString());
    }

    @Test
    void loadsOptionalConfigFileWhenPresent() throws IOException {
        Path home = tempDir.resolve("with-config");
        Files.createDirectories(home);
        Files.writeString(home.resolve("config.yml"), "tower:\n  example-setting: hello\n");
        StandardEnvironment environment = new StandardEnvironment();

        processor.process(environment, home);

        assertThat(environment.getProperty("tower.example-setting")).isEqualTo("hello");
    }

    @Test
    void missingConfigFileIsNotAnError() {
        Path home = tempDir.resolve("no-config-here");
        StandardEnvironment environment = new StandardEnvironment();

        processor.process(environment, home);

        assertThat(Files.exists(home.resolve("config.yml"))).isFalse();
    }

    /**
     * Regression test for a CI failure.
     *
     * <p>Spring's relaxed binding maps the TOWER_HOME environment variable onto the property
     * name tower.home, so a build that exports TOWER_HOME — as the CI workflow does, to keep
     * the runner's home directory untouched — used to shadow the resolved value with the raw
     * environment string. Anything Paths.get normalises made the two disagree.
     *
     * <p>StandardEnvironment already carries a systemEnvironment source; this adds a stand-in
     * with the same shape so the precedence is asserted without mutating the real environment.
     */
    @Test
    void theResolvedHomeWinsOverAnAmbientTowerHomeVariable() {
        Path home = tempDir.resolve("resolved-home");
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(
                new MapPropertySource("ambientEnvironment", Map.of("tower.home", "/somewhere/else/entirely")));

        processor.process(environment, home);

        assertThat(environment.getProperty("tower.home")).isEqualTo(home.toString());
    }

    /** config.yml is parsed from the directory, so it cannot redefine where that directory is. */
    @Test
    void configFileCannotRedefineTheHomeDirectory() throws IOException {
        Path home = tempDir.resolve("self-referential");
        Files.createDirectories(home);
        Files.writeString(home.resolve("config.yml"), "tower:\n  home: /invented/by/config\n");
        StandardEnvironment environment = new StandardEnvironment();

        processor.process(environment, home);

        assertThat(environment.getProperty("tower.home")).isEqualTo(home.toString());
    }
}
