package dev.tower.application.binding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.tower.application.service.InvalidRequestException;
import dev.tower.domain.application.ApplicationId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("An Application binding")
class ApplicationBindingTest {

    private static ApplicationBinding withPattern(String pattern) {
        return new ApplicationBinding(ApplicationId.newId(), "kubernetes",
                "registry.example/acme/customer-api", pattern);
    }

    @Nested
    @DisplayName("derives the Application Version from the image tag")
    class VersionResolution {

        @Test
        void treats_the_whole_tag_as_the_version_by_default() {
            assertThat(withPattern(null).resolveVersion("2026.08.1")).contains("2026.08.1");
        }

        @Test
        void extracts_the_captured_group_when_the_team_prefixes_its_tags() {
            assertThat(withPattern("^release-(.+)$").resolveVersion("release-2026.08.1"))
                    .contains("2026.08.1");
        }

        @Test
        void ignores_a_build_suffix_when_the_pattern_says_to() {
            assertThat(withPattern("^(\\d+\\.\\d+\\.\\d+)-build\\d+$").resolveVersion("1.4.2-build77"))
                    .contains("1.4.2");
        }

        @Test
        void is_empty_when_the_tag_does_not_match_rather_than_guessing() {
            assertThat(withPattern("^release-(.+)$").resolveVersion("latest")).isEmpty();
        }

        @Test
        void is_empty_for_a_missing_tag_so_a_digest_pinned_image_is_reported_not_attributed() {
            assertThat(withPattern(null).resolveVersion(null)).isEmpty();
            assertThat(withPattern(null).resolveVersion("  ")).isEmpty();
        }

        @Test
        void matches_the_whole_tag_rather_than_a_substring() {
            // ^(\d+)$ against "12abc" must not quietly yield "12".
            assertThat(withPattern("^(\\d+)$").resolveVersion("12abc")).isEmpty();
        }
    }

    @Nested
    @DisplayName("rejects a pattern that cannot produce a version")
    class PatternValidation {

        @Test
        void refuses_a_regular_expression_that_does_not_compile() {
            assertThatThrownBy(() -> withPattern("^release-(.+$"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("not a valid regular expression");
        }

        @Test
        void refuses_a_pattern_with_no_capturing_group_because_nothing_marks_the_version() {
            assertThatThrownBy(() -> withPattern("^release-.+$"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("capturing group");
        }

        @Test
        void accepts_a_blank_pattern_as_meaning_the_whole_tag() {
            assertThat(withPattern("   ").versionPattern()).isEqualTo(ApplicationBinding.WHOLE_TAG);
        }
    }

    @Nested
    @DisplayName("recognises the image it was bound to")
    class ImageMatching {

        @Test
        void matches_the_exact_image_reference() {
            assertThat(withPattern(null).matchesImage("registry.example/acme/customer-api")).isTrue();
        }

        @Test
        void does_not_match_a_different_image() {
            assertThat(withPattern(null).matchesImage("registry.example/acme/orders-api")).isFalse();
        }

        @Test
        void does_not_match_null() {
            assertThat(withPattern(null).matchesImage(null)).isFalse();
        }
    }

    @Test
    void requires_the_application_it_recognises() {
        assertThatThrownBy(() -> new ApplicationBinding(null, "kubernetes", "acme/api", null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void requires_an_image() {
        assertThatThrownBy(() -> new ApplicationBinding(ApplicationId.newId(), "kubernetes", " ", null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("image");
    }
}
