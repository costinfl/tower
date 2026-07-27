package dev.tower.config;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Creates the Tower data directory on first run.
 *
 * Implementation-Plan.md / Credential Handling: the directory that will hold config.yml and
 * the database file is created with owner-only permissions where the filesystem supports POSIX
 * permissions, and degrades gracefully (best-effort, no failure) where it does not.
 */
public final class TowerHomeInitializer {

    private static final String OWNER_ONLY = "rwx------";

    private TowerHomeInitializer() {
    }

    /** Ensures {@code dir} exists, creating it (and any missing parents) with owner-only permissions if possible. */
    public static Path ensureCreated(Path dir) throws IOException {
        if (Files.exists(dir)) {
            return dir;
        }
        if (supportsPosixPermissions(dir)) {
            FileAttribute<?> ownerOnly = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(OWNER_ONLY));
            Files.createDirectories(dir, ownerOnly);
        } else {
            // Non-POSIX filesystem (e.g. Windows): fall back to default permissions.
            Files.createDirectories(dir);
        }
        return dir;
    }

    private static boolean supportsPosixPermissions(Path dir) {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }
}
