package dev.tower.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the Tower application data directory.
 *
 * ADR-009: application data lives in a directory named .tower within the user's home
 * directory, outside the repository working tree. The TOWER_HOME environment variable
 * overrides the location, which tests and CI use to avoid touching a real home directory.
 */
public final class TowerHome {

    public static final String TOWER_HOME_ENV = "TOWER_HOME";

    private static final String DEFAULT_DIRECTORY_NAME = ".tower";

    private TowerHome() {
    }

    /** Resolves the data directory from the environment. Does not create it. */
    public static Path resolve() {
        return resolve(System.getenv(TOWER_HOME_ENV), System.getProperty("user.home"));
    }

    static Path resolve(String towerHomeEnv, String userHome) {
        if (towerHomeEnv != null && !towerHomeEnv.isBlank()) {
            return Paths.get(towerHomeEnv);
        }
        if (userHome == null || userHome.isBlank()) {
            throw new IllegalStateException(
                    "Cannot resolve Tower data directory: neither TOWER_HOME nor user.home is set");
        }
        return Paths.get(userHome, DEFAULT_DIRECTORY_NAME);
    }
}
