package dev.tower.config;

import java.nio.file.Path;

/**
 * Resolved locations within the Tower data directory, injectable by other modules.
 *
 * ADR-009: application data (configuration, database) lives under this single directory,
 * outside the repository working tree. tower-persistence uses {@link #databaseDirectory()}
 * for the embedded H2 database file (issue #3).
 */
public class TowerPaths {

    private final Path home;

    public TowerPaths(Path home) {
        this.home = home;
    }

    /** The Tower data directory itself (~/.tower, or TOWER_HOME when set). */
    public Path home() {
        return home;
    }

    /** Optional configuration file. Its absence is not an error; defaults apply. */
    public Path configFile() {
        return home.resolve("config.yml");
    }

    /** Directory holding the embedded database files. */
    public Path databaseDirectory() {
        return home.resolve("db");
    }
}
