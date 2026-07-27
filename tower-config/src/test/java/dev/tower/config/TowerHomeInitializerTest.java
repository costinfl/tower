package dev.tower.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TowerHomeInitializerTest {

    @TempDir
    Path tempDir;

    @Test
    void createsDirectoryWhenAbsent() throws IOException {
        Path dataDir = tempDir.resolve("does-not-exist-yet");

        TowerHomeInitializer.ensureCreated(dataDir);

        assertThat(Files.isDirectory(dataDir)).isTrue();
    }

    @Test
    void createsMissingParentDirectories() throws IOException {
        Path dataDir = tempDir.resolve("nested/does/not/exist");

        TowerHomeInitializer.ensureCreated(dataDir);

        assertThat(Files.isDirectory(dataDir)).isTrue();
    }

    @Test
    void isIdempotentWhenDirectoryAlreadyExists() throws IOException {
        Path dataDir = tempDir.resolve("already-there");
        Files.createDirectories(dataDir);

        TowerHomeInitializer.ensureCreated(dataDir);

        assertThat(Files.isDirectory(dataDir)).isTrue();
    }

    @Test
    void createsDirectoryWithOwnerOnlyPermissionsOnPosixFilesystems() throws IOException {
        assumeSupportsPosix();
        Path dataDir = tempDir.resolve("owner-only");

        TowerHomeInitializer.ensureCreated(dataDir);

        assertThat(Files.getPosixFilePermissions(dataDir))
                .isEqualTo(PosixFilePermissions.fromString("rwx------"));
    }

    private static void assumeSupportsPosix() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
    }
}
