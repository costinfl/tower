package dev.tower.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

class TowerPathsTest {

    private final TowerPaths paths = new TowerPaths(Paths.get("/tmp/tower-home"));

    @Test
    void resolvesHomeDirectory() {
        assertThat(paths.home()).isEqualTo(Path.of("/tmp/tower-home"));
    }

    @Test
    void resolvesConfigFileUnderHome() {
        assertThat(paths.configFile()).isEqualTo(Path.of("/tmp/tower-home/config.yml"));
    }

    @Test
    void resolvesDatabaseDirectoryUnderHome() {
        assertThat(paths.databaseDirectory()).isEqualTo(Path.of("/tmp/tower-home/db"));
    }
}
