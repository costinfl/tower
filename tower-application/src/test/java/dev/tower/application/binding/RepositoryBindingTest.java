package dev.tower.application.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.tower.application.binding.RepositoryBinding.RefSelection;
import dev.tower.application.service.InvalidRequestException;
import dev.tower.domain.application.ApplicationId;

@DisplayName("A repository binding")
class RepositoryBindingTest {

    private static RepositoryBinding binding(RefSelection selection, String pattern) {
        return new RepositoryBinding(
                ApplicationId.newId(), "git", "https://example.com/acme/api.git", selection, pattern);
    }

    @Nested
    @DisplayName("turns a ref name into a version")
    class Resolving {

        @Test
        void takes_the_whole_ref_name_by_default() {
            assertThat(binding(RefSelection.TAGS, null).resolveVersion("2.5.0")).contains("2.5.0");
        }

        @Test
        void applies_the_capturing_group_when_one_is_given() {
            assertThat(binding(RefSelection.TAGS, "^v(.+)$").resolveVersion("v2.5.0")).contains("2.5.0");
        }

        @Test
        void returns_empty_for_a_ref_the_pattern_does_not_recognise() {
            // A normal answer, not a failure: this is how a main branch or a
            // sandbox tag is passed over without anyone being told something
            // went wrong.
            assertThat(binding(RefSelection.TAGS, "^v(.+)$").resolveVersion("main")).isEmpty();
        }

        @Test
        void matches_the_whole_name_rather_than_a_substring() {
            // ^v(.+)$ must not accept "prerelease-v2.5.0" and yield "2.5.0" —
            // that would register a version from a ref nobody meant.
            assertThat(binding(RefSelection.TAGS, "^v(.+)$").resolveVersion("prerelease-v2.5.0"))
                    .isEmpty();
        }

        @Test
        void returns_empty_for_a_blank_ref_name() {
            assertThat(binding(RefSelection.TAGS, null).resolveVersion("  ")).isEmpty();
            assertThat(binding(RefSelection.TAGS, null).resolveVersion(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("rejects configuration that would fail later instead")
    class Validation {

        @Test
        void rejects_a_pattern_that_does_not_compile() {
            assertThatThrownBy(() -> binding(RefSelection.TAGS, "^v(.+$"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("not a valid regular expression");
        }

        @Test
        void rejects_a_pattern_with_no_capturing_group() {
            assertThatThrownBy(() -> binding(RefSelection.TAGS, "^v.+$"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("capturing group");
        }

        @Test
        void rejects_a_binding_with_no_repository() {
            assertThatThrownBy(() -> new RepositoryBinding(
                    ApplicationId.newId(), "git", "  ", RefSelection.TAGS, null))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("repository URL");
        }

        @Test
        void defaults_to_tags_when_no_selection_is_given() {
            // Reading branches as well when a team only tags fills the candidate
            // list with every feature branch.
            assertThat(binding(null, null).refSelection()).isEqualTo(RefSelection.TAGS);
        }
    }

    @Nested
    @DisplayName("parses a ref selection")
    class Selections {

        @Test
        void accepts_the_known_values_in_any_case() {
            assertThat(RefSelection.parse("tags")).isEqualTo(RefSelection.TAGS);
            assertThat(RefSelection.parse(" BRANCHES ")).isEqualTo(RefSelection.BRANCHES);
            assertThat(RefSelection.parse("All")).isEqualTo(RefSelection.ALL);
        }

        @Test
        void defaults_to_tags_when_absent() {
            assertThat(RefSelection.parse(null)).isEqualTo(RefSelection.TAGS);
            assertThat(RefSelection.parse("")).isEqualTo(RefSelection.TAGS);
        }

        @Test
        void rejects_an_unknown_value_by_name() {
            assertThatThrownBy(() -> RefSelection.parse("EVERYTHING"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("EVERYTHING");
        }

        @Test
        void says_which_kinds_it_includes() {
            assertThat(RefSelection.TAGS.includesTags()).isTrue();
            assertThat(RefSelection.TAGS.includesBranches()).isFalse();
            assertThat(RefSelection.BRANCHES.includesBranches()).isTrue();
            assertThat(RefSelection.BRANCHES.includesTags()).isFalse();
            assertThat(RefSelection.ALL.includesTags()).isTrue();
            assertThat(RefSelection.ALL.includesBranches()).isTrue();
        }
    }
}
