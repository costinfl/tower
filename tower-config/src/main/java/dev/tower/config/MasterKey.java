package dev.tower.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The key that encrypts stored Connector credentials.
 *
 * <p>Resolution order, highest first:
 *
 * <ol>
 *   <li>the {@code TOWER_MASTER_KEY} environment variable;
 *   <li>a key file in the Tower data directory, generated once on first use.
 * </ol>
 *
 * <p>Implementation-Plan.md / Credential Handling records what the second option
 * costs. A key sitting beside the ciphertext it protects defends against a stray
 * backup, a synced folder or a shared disk image; it does not defend against
 * anyone who can already read the data directory. It is chosen because Tower is
 * a local-first application (ADR-009) that must start without ceremony, and it
 * is written down rather than left for someone to infer.
 *
 * <p>The environment variable keeps precedence, so a developer who wants the
 * stronger arrangement gets it by setting one variable and deleting the file.
 *
 * <p>The key itself is never logged. The log says which source supplied it,
 * because that materially changes the threat model and an operator should be
 * able to tell which one is in force.
 */
public final class MasterKey {

    public static final String ENV_VARIABLE = "TOWER_MASTER_KEY";

    private static final Logger log = LoggerFactory.getLogger(MasterKey.class);
    private static final String OWNER_ONLY_FILE = "rw-------";
    private static final int KEY_BYTES = 32;

    private final char[] value;
    private final Source source;

    /** Where the key in force came from, which decides how much the encryption is worth. */
    public enum Source {
        ENVIRONMENT,
        GENERATED_FILE
    }

    private MasterKey(char[] value, Source source) {
        this.value = value;
        this.source = source;
    }

    /**
     * The key in force, generating and persisting one if neither the environment
     * nor a previous run supplied it.
     */
    public static MasterKey resolve(Path keyFile) throws IOException {
        return resolve(keyFile, System.getenv(ENV_VARIABLE));
    }

    // Visible for testing: the environment cannot be set from a unit test.
    static MasterKey resolve(Path keyFile, String fromEnvironment) throws IOException {
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            log.info("Credential encryption key taken from {}.", ENV_VARIABLE);
            return new MasterKey(fromEnvironment.trim().toCharArray(), Source.ENVIRONMENT);
        }
        if (Files.exists(keyFile)) {
            String stored = Files.readString(keyFile, StandardCharsets.UTF_8).trim();
            if (!stored.isEmpty()) {
                log.info("Credential encryption key read from {}.", keyFile);
                return new MasterKey(stored.toCharArray(), Source.GENERATED_FILE);
            }
            // An empty key file is a half-written one. Replacing it is safe: an
            // empty key never encrypted anything.
        }
        return new MasterKey(generateInto(keyFile), Source.GENERATED_FILE);
    }

    private static char[] generateInto(Path keyFile) throws IOException {
        byte[] random = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(random);
        String generated = Base64.getEncoder().encodeToString(random);

        TowerHomeInitializer.ensureCreated(keyFile.getParent());
        Files.writeString(keyFile, generated, StandardCharsets.UTF_8);
        restrictToOwner(keyFile);

        log.warn("Generated a credential encryption key at {}. It protects stored credentials from "
                + "a stray backup or a shared disk, not from anyone who can read this directory. "
                + "Set {} to supply the key yourself.", keyFile, ENV_VARIABLE);
        return generated.toCharArray();
    }

    /**
     * Best-effort owner-only permissions, matching how {@link TowerHomeInitializer}
     * treats the data directory: a filesystem without POSIX permissions is not a
     * failure, it simply cannot offer this protection.
     */
    private static void restrictToOwner(Path file) throws IOException {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(OWNER_ONLY_FILE));
        }
    }

    char[] value() {
        return value;
    }

    public Source source() {
        return source;
    }

    /** Never renders the key, so it cannot reach a log or an error message by accident. */
    @Override
    public String toString() {
        return "MasterKey[source=" + source + "]";
    }
}
