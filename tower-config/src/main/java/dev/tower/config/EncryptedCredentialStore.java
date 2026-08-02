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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.tower.application.port.out.ConnectorCredentialsPort;
import dev.tower.application.port.out.CredentialStatus;
import dev.tower.application.service.CredentialsUnreadableException;

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

    /**
     * Where the key check value is kept.
     *
     * <p>Not a credential, so it is deliberately not under a connector's key: it
     * belongs to the file, not to any one entry.
     */
    private static final String KEY_CHECK = "tower.keyCheck";

    /**
     * What the key check value decrypts to when the key is right.
     *
     * <p>Any fixed string would do. This one says what it is to anyone who
     * decrypts the file by hand and wonders why an entry has no connector.
     */
    private static final String KEY_CHECK_PLAINTEXT = "tower-credential-store-key-check";

    private final Path credentialsFile;
    private final StandardPBEStringEncryptor encryptor;

    /**
     * Annotated because the class declares a second, package-private constructor
     * for tests. With more than one declared constructor and none marked, Spring
     * cannot choose and falls back to a no-argument constructor that does not
     * exist — a failure that appears only when the context starts.
     */
    @Autowired
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
        // Refused rather than allowed, because allowing it is what would bring
        // the defect back. A file holding entries under one key and a key check
        // under another puts every older entry back on the padding lottery this
        // class exists to avoid, and it would do so silently.
        if (!keyWroteThisFile(properties)) {
            throw new CredentialsUnreadableException(
                    "The credentials file was written with a different key, so saving into it would"
                            + " leave the credentials already there unreadable. Restore the previous "
                            + MasterKey.ENV_VARIABLE + " or key file, or delete the credentials file"
                            + " and save every credential again.", null);
        }
        String key = keyFor(connectorId, target);
        properties.setProperty(key + SECRET_SUFFIX, encryptor.encrypt(new String(secret)));
        properties.setProperty(key + UPDATED_SUFFIX, Instant.now().toString());
        // Written on every save, so a file that predates the key check acquires
        // one the first time a credential is stored.
        properties.setProperty(KEY_CHECK, encryptor.encrypt(KEY_CHECK_PLAINTEXT));
        save(properties);
    }

    @Override
    public synchronized Optional<char[]> secretFor(String connectorId, String target) {
        Properties properties = load();
        String stored = properties.getProperty(keyFor(connectorId, target) + SECRET_SUFFIX);
        if (stored == null) {
            return Optional.empty();
        }
        // Before the credential, and this is the part that has to be right: see
        // keyWroteThisFile.
        if (!keyWroteThisFile(properties)) {
            throw wrongKey(connectorId, target, null);
        }
        try {
            return Optional.of(encryptor.decrypt(stored).toCharArray());
        } catch (EncryptionOperationNotPossibleException e) {
            throw wrongKey(connectorId, target, e);
        }
    }

    /**
     * Whether the key in force is the one that wrote this file.
     *
     * <p>Not belt and braces. The algorithm here is AES in CBC mode with PKCS#5
     * padding and no authentication, so a wrong key does not fail — it produces
     * random bytes, and whether that <em>looks</em> like a failure depends on
     * whether those bytes happen to end in valid padding. They do roughly once in
     * every 256 attempts.
     *
     * <p>Measured rather than reasoned about: 2000 reads with a wrong key threw
     * 1993 times and returned garbage 7 times, 0.35%. This was first seen as an
     * intermittent test failure and mistaken for flakiness. It is not. Once in
     * every few hundred reads, a Connector would have been handed a string of
     * random bytes as a bearer token, presented it, and reported that the
     * External System refused the credential — sending somebody to mint a new
     * token when what had actually changed was their master key. The message
     * below exists to prevent exactly that, and was the one they would not get.
     *
     * <p>So the key is checked against a value it must decrypt to <em>exactly</em>.
     * A wrong key would have to produce that string byte for byte, which is not a
     * one-in-256 event.
     *
     * <p>A file written before this check has no key check value in it. Nothing
     * can be verified about such a file, so the older behaviour stands until the
     * next {@link #store} writes one — better than refusing to read credentials
     * that are almost certainly fine.
     */
    private boolean keyWroteThisFile(Properties properties) {
        String keyCheck = properties.getProperty(KEY_CHECK);
        if (keyCheck == null) {
            return true;
        }
        try {
            return KEY_CHECK_PLAINTEXT.equals(encryptor.decrypt(keyCheck));
        } catch (EncryptionOperationNotPossibleException e) {
            // The 99.6% case for a wrong key. The comparison above is what makes
            // the remaining 0.4% fail too.
            return false;
        }
    }

    /**
     * The key in force cannot read what an earlier key wrote.
     *
     * <p>Said in words rather than returned as empty, which would look like "not
     * configured" and send the user to re-enter a credential that is already
     * there.
     */
    private static CredentialsUnreadableException wrongKey(
            String connectorId, String target, Throwable cause) {
        return new CredentialsUnreadableException(
                "The stored credential for " + connectorId + " at " + target + " cannot be decrypted "
                        + "with the current key. Restore the previous " + MasterKey.ENV_VARIABLE
                        + " or key file, or delete the credentials file and save every credential"
                        + " again. Nothing in it can be read with the key now in force.", cause);
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
                // Null suppresses a custom header only; Properties.store always
                // writes its own date line. Harmless here — this file is read by
                // Tower rather than compared byte for byte, unlike generated
                // documentation, where NFR-025 does require determinism.
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
