package dev.tower.config;

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
import static org.assertj.core.api.Assumptions.assumeThat;

@DisplayName("The credential encryption key")
class MasterKeyTest {

    @TempDir
    Path home;

    private Path keyFile() {
        return home.resolve("master.key");
    }

    @Nested
    @DisplayName("prefers the environment, so the stronger arrangement wins")
    class Precedence {

        @Test
        void uses_the_environment_variable_when_it_is_set() throws IOException {
            var key = MasterKey.resolve(keyFile(), "a-supplied-key");

            assertThat(key.source()).isEqualTo(MasterKey.Source.ENVIRONMENT);
            assertThat(key.value()).containsExactly("a-supplied-key".toCharArray());
        }

        @Test
        void writes_no_key_file_when_the_environment_supplies_one() throws IOException {
            MasterKey.resolve(keyFile(), "a-supplied-key");

            assertThat(keyFile()).doesNotExist();
        }

        @Test
        void ignores_a_blank_environment_variable_rather_than_encrypting_with_nothing() throws IOException {
            var key = MasterKey.resolve(keyFile(), "   ");

            assertThat(key.source()).isEqualTo(MasterKey.Source.GENERATED_FILE);
        }
    }

    @Nested
    @DisplayName("generates once and reuses thereafter")
    class Generation {

        @Test
        void creates_a_key_file_on_first_use() throws IOException {
            var key = MasterKey.resolve(keyFile(), null);

            assertThat(keyFile()).exists();
            assertThat(key.source()).isEqualTo(MasterKey.Source.GENERATED_FILE);
            assertThat(key.value()).isNotEmpty();
        }

        @Test
        void returns_the_same_key_on_the_next_run_or_stored_credentials_become_unreadable() throws IOException {
            var first = MasterKey.resolve(keyFile(), null);
            var second = MasterKey.resolve(keyFile(), null);

            assertThat(second.value()).containsExactly(first.value());
        }

        @Test
        void replaces_an_empty_key_file_because_an_empty_key_encrypted_nothing() throws IOException {
            Files.writeString(keyFile(), "", StandardCharsets.UTF_8);

            var key = MasterKey.resolve(keyFile(), null);

            assertThat(key.value()).isNotEmpty();
            assertThat(Files.readString(keyFile())).isNotBlank();
        }

        @Test
        void writes_the_key_file_readable_only_by_its_owner() throws IOException {
            assumeThat(FileSystems.getDefault().supportedFileAttributeViews()).contains("posix");

            MasterKey.resolve(keyFile(), null);

            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(keyFile())))
                    .isEqualTo("rw-------");
        }
    }

    @Test
    void never_renders_the_key_when_printed_so_it_cannot_reach_a_log() throws IOException {
        var key = MasterKey.resolve(keyFile(), "sensitive-key-material");

        assertThat(key).hasToString("MasterKey[source=ENVIRONMENT]");
        assertThat(key.toString()).doesNotContain("sensitive-key-material");
    }
}
