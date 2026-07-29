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

    /**
     * Encrypted Connector credentials.
     *
     * <p>Deliberately separate from {@link #configFile()}. That file is read by
     * {@link TowerConfigEnvironmentPostProcessor} while Spring's environment is being built;
     * rewriting it at runtime to save a token would mean editing the file the application boots
     * from, which is a hazard for no benefit. Credentials get their own file, written and read
     * only by the credential store.
     */
    public Path credentialsFile() {
        return home.resolve("credentials.properties");
    }

    /**
     * The key that encrypts {@link #credentialsFile()}, when it is not supplied through the
     * environment.
     *
     * <p>Implementation-Plan.md / Credential Handling records the trade-off: a key file beside
     * the ciphertext protects a stray backup or a synced folder, not someone who can already read
     * this directory.
     */
    public Path masterKeyFile() {
        return home.resolve("master.key");
    }
}
