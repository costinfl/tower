package dev.tower.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.StandardEnvironment;

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
}
