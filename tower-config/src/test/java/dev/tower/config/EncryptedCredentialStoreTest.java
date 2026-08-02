package dev.tower.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.tower.application.service.CredentialsUnreadableException;

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

            assertThatThrownBy(() -> readWithADifferentKey().secretFor(CONNECTOR, TARGET))
                    .isInstanceOf(CredentialsUnreadableException.class)
                    .hasMessageContaining("cannot be decrypted");
        }

        /**
         * The test that would have caught this, and the reason the one above is
         * not enough on its own.
         *
         * <p>The algorithm here is AES-CBC with PKCS#5 padding and no
         * authentication. A wrong key does not fail; it produces random bytes,
         * and whether that looks like a failure depends on whether those bytes
         * happen to end in valid padding — which they do about once in every 256
         * attempts. Measured before the fix: 2000 reads with a wrong key threw
         * 1993 times and returned garbage 7 times.
         *
         * <p>So a single attempt passed 99.65% of the time and failed the rest,
         * which is exactly what an intermittent test looks like, and exactly how
         * this was first written off as flakiness. Two hundred attempts turn a
         * 0.35% defect into a 50-50 coin toss, and five hundred into a near
         * certainty. Reading garbage even once is a failure, because that garbage
         * would have been presented to an External System as somebody's token.
         */
        @Test
        void never_returns_a_credential_a_wrong_key_could_not_have_read() throws IOException {
            var wrongKey = readWithADifferentKey();

            for (int attempt = 0; attempt < 500; attempt++) {
                store.store(CONNECTOR, TARGET, TOKEN.toCharArray());

                assertThatThrownBy(() -> wrongKey.secretFor(CONNECTOR, TARGET))
                        .as("attempt %d read a credential the key could not have decrypted", attempt)
                        .isInstanceOf(CredentialsUnreadableException.class);
            }
        }

        @Test
        void refuses_to_write_into_a_file_an_earlier_key_wrote() throws IOException {
            // Allowing it would leave entries under one key beside a key check
            // under another, which puts every older entry back on the padding
            // lottery — silently, and only for the credentials already there.
            store.store(CONNECTOR, TARGET, TOKEN.toCharArray());

            assertThatThrownBy(() -> readWithADifferentKey()
                    .store(CONNECTOR, "https://elsewhere.example:6443", TOKEN.toCharArray()))
                    .isInstanceOf(CredentialsUnreadableException.class)
                    // Describes what saving would do, not what reading would fail
                    // at: the caller was storing, and a message about decrypting
                    // the credential they are supplying would name the wrong thing.
                    .hasMessageContaining("would leave the credentials already there unreadable");
        }

        @Test
        void names_both_ways_out_rather_than_only_the_one_that_needs_a_lost_file() throws IOException {
            store.store(CONNECTOR, TARGET, TOKEN.toCharArray());

            assertThatThrownBy(() -> readWithADifferentKey().secretFor(CONNECTOR, TARGET))
                    .hasMessageContaining(MasterKey.ENV_VARIABLE)
                    .hasMessageContaining("delete the credentials file");
        }

        @Test
        void reads_normally_when_the_key_is_the_one_that_wrote_the_file() throws IOException {
            // The check must not fire for the ordinary case, which is the whole
            // of normal use.
            store.store(CONNECTOR, TARGET, TOKEN.toCharArray());

            var sameKeyAgain = new EncryptedCredentialStore(
                    credentialsFile, MasterKey.resolve(home.resolve("master.key"), null));

            assertThat(sameKeyAgain.secretFor(CONNECTOR, TARGET)).hasValueSatisfying(
                    secret -> assertThat(secret).containsExactly(TOKEN.toCharArray()));
        }

        @Test
        void still_reads_a_file_written_before_the_key_check_existed() throws IOException {
            store.store(CONNECTOR, TARGET, TOKEN.toCharArray());
            removeTheKeyCheck();

            // Nothing can be verified about such a file, and refusing to read it
            // would lock somebody out of credentials that are almost certainly
            // fine. The older, weaker behaviour stands until the next store.
            assertThat(store.secretFor(CONNECTOR, TARGET)).hasValueSatisfying(
                    secret -> assertThat(secret).containsExactly(TOKEN.toCharArray()));
        }

        @Test
        void gives_a_file_written_before_the_key_check_one_on_the_next_store() throws IOException {
            store.store(CONNECTOR, TARGET, TOKEN.toCharArray());
            removeTheKeyCheck();

            store.store(CONNECTOR, "https://other.example:6443", TOKEN.toCharArray());

            assertThat(propertiesOnDisk().getProperty("tower.keyCheck")).isNotNull();
            // And the verification is live from that moment on.
            assertThatThrownBy(() -> readWithADifferentKey().secretFor(CONNECTOR, TARGET))
                    .isInstanceOf(CredentialsUnreadableException.class);
        }

        private java.util.Properties propertiesOnDisk() throws IOException {
            var properties = new java.util.Properties();
            try (var reader = Files.newBufferedReader(credentialsFile, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            return properties;
        }

        /** The file as it would have been written before this check existed. */
        private void removeTheKeyCheck() throws IOException {
            var properties = propertiesOnDisk();
            assertThat(properties.remove("tower.keyCheck"))
                    .as("the store should have written a key check to remove")
                    .isNotNull();
            try (var writer = Files.newBufferedWriter(credentialsFile, StandardCharsets.UTF_8)) {
                properties.store(writer, null);
            }
        }

        private EncryptedCredentialStore readWithADifferentKey() throws IOException {
            return new EncryptedCredentialStore(
                    credentialsFile, MasterKey.resolve(home.resolve("other.key"), "a-different-key"));
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
