package dev.tower.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.exceptions.EncryptionOperationNotPossibleException;
import org.jasypt.iv.RandomIvGenerator;
import org.springframework.stereotype.Component;

import dev.tower.application.port.out.ConnectorCredentialsPort;
import dev.tower.application.port.out.CredentialStatus;

/**
 * Stores Connector credentials encrypted at rest (NFR-028, ADR-009).
 *
 * <p>This is the only class in Tower that handles credential material. An
 * architecture test confines Jasypt to this module precisely so that the answer
 * to "where are secrets touched" is one file rather than a search.
 *
 * <p>Values are encrypted individually and written to a properties file with
 * owner-only permissions. The file is rewritten whole on each change, which is
 * correct for a handful of credentials on a single-user local instance
 * (ADR-009) and would need revisiting only if that stopped being true.
 *
 * <p>The encrypted value and the time it was written are stored together, so
 * {@link #status} can answer without decrypting anything. That matters: the
 * screen that renders "configured" should never cause a decryption, and with
 * this layout it cannot.
 */
@Component
public class EncryptedCredentialStore implements ConnectorCredentialsPort {

    private static final String ALGORITHM = "PBEWITHHMACSHA512ANDAES_256";
    private static final String OWNER_ONLY_FILE = "rw-------";
    private static final String SECRET_SUFFIX = ".secret";
    private static final String UPDATED_SUFFIX = ".updatedAt";

    private final Path credentialsFile;
    private final StandardPBEStringEncryptor encryptor;

    public EncryptedCredentialStore(TowerPaths paths) throws IOException {
        this(paths.credentialsFile(), MasterKey.resolve(paths.masterKeyFile()));
    }

    EncryptedCredentialStore(Path credentialsFile, MasterKey masterKey) {
        this.credentialsFile = credentialsFile;
        this.encryptor = new StandardPBEStringEncryptor();
        this.encryptor.setPasswordCharArray(masterKey.value());
        this.encryptor.setAlgorithm(ALGORITHM);
        // A random IV per encryption, so storing the same token twice does not
        // produce the same ciphertext and reveal that fact to anyone reading the file.
        this.encryptor.setIvGenerator(new RandomIvGenerator());
    }

    @Override
    public synchronized void store(String connectorId, String target, char[] secret) {
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException("A credential must not be empty.");
        }
        Properties properties = load();
        String key = keyFor(connectorId, target);
        properties.setProperty(key + SECRET_SUFFIX, encryptor.encrypt(new String(secret)));
        properties.setProperty(key + UPDATED_SUFFIX, Instant.now().toString());
        save(properties);
    }

    @Override
    public synchronized Optional<char[]> secretFor(String connectorId, String target) {
        String stored = load().getProperty(keyFor(connectorId, target) + SECRET_SUFFIX);
        if (stored == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(encryptor.decrypt(stored).toCharArray());
        } catch (EncryptionOperationNotPossibleException e) {
            // The key in force cannot read what an earlier key wrote. Saying so
            // beats returning empty, which would look like "not configured" and
            // send the user to re-enter a credential that is already there.
            throw new IllegalStateException(
                    "The stored credential for " + connectorId + " at " + target + " cannot be decrypted "
                            + "with the current key. Restore the previous " + MasterKey.ENV_VARIABLE
                            + " or key file, or save the credential again.", e);
        }
    }

    @Override
    public synchronized CredentialStatus status(String connectorId, String target) {
        Properties properties = load();
        String key = keyFor(connectorId, target);
        if (properties.getProperty(key + SECRET_SUFFIX) == null) {
            return CredentialStatus.absent(connectorId, target);
        }
        return CredentialStatus.configuredAt(connectorId, target, readUpdatedAt(properties, key));
    }

    @Override
    public synchronized void forget(String connectorId, String target) {
        Properties properties = load();
        String key = keyFor(connectorId, target);
        boolean removed = properties.remove(key + SECRET_SUFFIX) != null;
        properties.remove(key + UPDATED_SUFFIX);
        if (removed) {
            save(properties);
        }
    }

    private static Instant readUpdatedAt(Properties properties, String key) {
        String written = properties.getProperty(key + UPDATED_SUFFIX);
        if (written == null) {
            return null;
        }
        try {
            return Instant.parse(written);
        } catch (java.time.format.DateTimeParseException e) {
            // A hand-edited file should not make the screen fail. The credential
            // is still there; only the "last written" caption is unavailable.
            return null;
        }
    }

    /**
     * Identifies a credential by the Connector that presents it and the endpoint
     * it authenticates against — the same target an External Binding names, so
     * the user never has to invent a name for a connection.
     */
    private static String keyFor(String connectorId, String target) {
        return requireText(connectorId, "connectorId") + "@" + requireText(target, "target");
    }

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A credential must name its " + what + ".");
        }
        return value.trim();
    }

    private Properties load() {
        Properties properties = new Properties();
        if (!Files.exists(credentialsFile)) {
            return properties;
        }
        try (var reader = Files.newBufferedReader(credentialsFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + credentialsFile, e);
        }
        return properties;
    }

    private void save(Properties properties) {
        try {
            TowerHomeInitializer.ensureCreated(credentialsFile.getParent());
            try (var writer = Files.newBufferedWriter(credentialsFile, StandardCharsets.UTF_8)) {
                // No comment line: Properties.store writes a timestamp comment,
                // and a file that changes when nothing changed is noise.
                properties.store(writer, null);
            }
            restrictToOwner(credentialsFile);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + credentialsFile, e);
        }
    }

    private static void restrictToOwner(Path file) throws IOException {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(OWNER_ONLY_FILE));
        }
    }

}
