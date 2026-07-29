package dev.tower.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

@DisplayName("The encrypted credential store")
class EncryptedCredentialStoreTest {

    private static final String CONNECTOR = "kubernetes";
    private static final String TARGET = "https://api.cluster.example:6443";
    private static final String TOKEN = "sha256~a-real-looking-bearer-token";

    @TempDir
    Path home;

    private Path credentialsFile;
    private EncryptedCredentialStore store;

    @BeforeEach
    void setUp() throws IOException {
        credentialsFile = home.resolve("credentials.properties");
        store = new EncryptedCredentialStore(credentialsFile, MasterKey.resolve(home.resolve("master.key"), null));
    }

    @Nested
    @DisplayName("keeps the secret out of the file it writes")
    class AtRest {

        @Test
        void writes_no_plaintext_token_to_disk() throws IOException {
            store.store(CONNECTOR, TARGET, TOKEN.toCharArray());

            assertThat(Files.readString(credentialsFile, StandardCharsets.UTF_8))
                    .doesNotContain(TOKEN);
        }

        @Test
        void encrypts_the_same_token_differently_each_time_so_reuse_is_not_visible() throws IOException {
            String otherTarget = "https://other.example:6443";
            store.store(CONNECTOR, TARGET, TOKEN.toCharArray());
            store.store(CONNECTOR, otherTarget, TOKEN.toCharArray());

            var stored = new java.util.Properties();
            try (var reader = Files.newBufferedReader(credentialsFile, StandardCharsets.UTF_8)) {
                stored.load(reader);
            }
            String first = stored.getProperty(CONNECTOR + "@" + TARGET + ".secret");
            String second = stored.getProperty(CONNECTOR + "@" + otherTarget + ".secret");

            assertThat(first).isNotNull().isNotEqualTo(second);
            assertThat(second).isNotNull();
        }

        @Test
        void writes_the_file_readable_only_by_its_owner() throws IOException {
            assumeThat(FileSystems.getDefault().supportedFileAttributeViews()).contains("posix");

            store.store(CONNECTOR, TARGET, TOKEN.toCharArray());

            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(credentialsFile)))
                    .isEqualTo("rw-------");
        }
    }

    @Nested
    @DisplayName("returns what was stored")
    class RoundTrip {

        @Test
        void gives_back_the_token_a_collector_needs() {
            store.store(CONNECTOR, TARGET, TOKEN.toCharArray());

            assertThat(store.secretFor(CONNECTOR, TARGET)).hasValueSatisfying(
                    secret -> assertThat(secret).containsExactly(TOKEN.toCharArray()));
        }

        @Test
        void survives_a_restart_because_the_key_is_reused() throws IOException {
            store.store(CONNECTOR, TARGET, TOKEN.toCharArray());

            var afterRestart = new EncryptedCredentialStore(
                    credentialsFile, MasterKey.resolve(home.resolve("master.key"), null));

            assertThat(afterRestart.secretFor(CONNECTOR, TARGET)).hasValueSatisfying(
                    secret -> assertThat(secret).containsExactly(TOKEN.toCharArray()));
        }

        @Test
        void keeps_credentials_for_different_targets_apart() {
            store.store(CONNECTOR, TARGET, "token-a".toCharArray());
            store.store(CONNECTOR, "https://other.example:6443", "token-b".toCharArray());

            assertThat(store.secretFor(CONNECTOR, TARGET)).hasValueSatisfying(
                    secret -> assertThat(secret).containsExactly("token-a".toCharArray()));
            assertThat(store.secretFor(CONNECTOR, "https://other.example:6443")).hasValueSatisfying(
                    secret -> assertThat(secret).containsExactly("token-b".toCharArray()));
        }

        @Test
        void replaces_rather_than_duplicates_when_a_token_is_rotated() {
            store.store(CONNECTOR, TARGET, "old".toCharArray());
            store.store(CONNECTOR, TARGET, "new".toCharArray());

            assertThat(store.secretFor(CONNECTOR, TARGET)).hasValueSatisfying(
                    secret -> assertThat(secret).containsExactly("new".toCharArray()));
        }

        @Test
        void is_empty_when_nothing_was_ever_stored() {
            assertThat(store.secretFor(CONNECTOR, TARGET)).isEmpty();
        }
    }

    @Nested
    @DisplayName("reports configuration without disclosing anything")
    class Status {

        @Test
        void says_a_credential_is_absent_before_one_is_saved() {
            var status = store.status(CONNECTOR, TARGET);

            assertThat(status.configured()).isFalse();
            assertThat(status.updatedAt()).isNull();
        }

        @Test
        void says_a_credential_is_configured_and_when_it_was_written() {
            store.store(CONNECTOR, TARGET, TOKEN.toCharArray());

            var status = store.status(CONNECTOR, TARGET);

            assertThat(status.configured()).isTrue();
            assertThat(status.updatedAt()).isNotNull();
        }

        @Test
        void carries_no_field_that_could_leak_the_token() {
            store.store(CONNECTOR, TARGET, TOKEN.toCharArray());

            assertThat(store.status(CONNECTOR, TARGET).toString()).doesNotContain(TOKEN);
        }
    }

    @Nested
    @DisplayName("forgets on request")
    class Forget {

        @Test
        void removes_the_stored_secret() {
            store.store(CONNECTOR, TARGET, TOKEN.toCharArray());

            store.forget(CONNECTOR, TARGET);

            assertThat(store.secretFor(CONNECTOR, TARGET)).isEmpty();
            assertThat(store.status(CONNECTOR, TARGET).configured()).isFalse();
        }

        @Test
        void leaves_other_credentials_alone() {
            store.store(CONNECTOR, TARGET, "token-a".toCharArray());
            store.store(CONNECTOR, "https://other.example:6443", "token-b".toCharArray());

            store.forget(CONNECTOR, TARGET);

            assertThat(store.secretFor(CONNECTOR, "https://other.example:6443")).isPresent();
        }

        @Test
        void is_silent_when_there_was_nothing_to_forget() {
            store.forget(CONNECTOR, TARGET);

            assertThat(credentialsFile).doesNotExist();
        }
    }

    @Nested
    @DisplayName("fails loudly when the key changed")
    class WrongKey {

        @Test
        void says_the_credential_cannot_be_decrypted_rather_than_looking_unconfigured() throws IOException {
            store.store(CONNECTOR, TARGET, TOKEN.toCharArray());

            var withDifferentKey = new EncryptedCredentialStore(
                    credentialsFile, MasterKey.resolve(home.resolve("other.key"), "a-different-key"));

            assertThatThrownBy(() -> withDifferentKey.secretFor(CONNECTOR, TARGET))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot be decrypted");
        }
    }

    @Nested
    @DisplayName("refuses input it cannot store meaningfully")
    class Validation {

        @Test
        void rejects_an_empty_secret() {
            assertThatThrownBy(() -> store.store(CONNECTOR, TARGET, new char[0]))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejects_a_missing_target() {
            assertThatThrownBy(() -> store.store(CONNECTOR, " ", TOKEN.toCharArray()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("target");
        }
    }
}
