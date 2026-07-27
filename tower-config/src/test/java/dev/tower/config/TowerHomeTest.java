package dev.tower.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

class TowerHomeTest {

    @Test
    void defaultsToDotTowerUnderUserHomeWhenEnvVarAbsent() {
        Path resolved = TowerHome.resolve(null, "/home/someone");

        assertThat(resolved).isEqualTo(Paths.get("/home/someone", ".tower"));
    }

    @Test
    void blankEnvVarFallsBackToUserHome() {
        Path resolved = TowerHome.resolve("   ", "/home/someone");

        assertThat(resolved).isEqualTo(Paths.get("/home/someone", ".tower"));
    }

    @Test
    void towerHomeEnvVarOverridesDefault() {
        Path resolved = TowerHome.resolve("/tmp/tower-test", "/home/someone");

        assertThat(resolved).isEqualTo(Paths.get("/tmp/tower-test"));
    }

    @Test
    void requiresUserHomeWhenNoOverrideGiven() {
        assertThatThrownBy(() -> TowerHome.resolve(null, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
