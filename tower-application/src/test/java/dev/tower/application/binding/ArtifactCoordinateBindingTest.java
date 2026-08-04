package dev.tower.application.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.tower.application.service.InvalidRequestException;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersion;

@DisplayName("An artifact coordinate binding")
class ArtifactCoordinateBindingTest {

    private static final String COMMIT = "abc1234def5678901234567890abcdef12345678";

    private static ArtifactCoordinateBinding binding(String template) {
        return binding(template, 0);
    }

    private static ArtifactCoordinateBinding binding(String template, int shortCommitLength) {
        return new ArtifactCoordinateBinding(ApplicationId.newId(), "artifactory", "image",
                "https://artifacts.example.com", template, shortCommitLength);
    }

    private static ApplicationVersion version(String number, String commit) {
        return ApplicationVersion.create(ApplicationId.newId(), number, null, null, commit, null);
    }

    @Nested
    @DisplayName("composes a coordinate from what Tower already holds")
    class Composing {

        @Test
        void substitutes_the_version_and_the_short_commit() {
            // The whole reason this Connector can be read-only with a direct GET:
            // ADR-021 records that the team's tagging convention makes the
            // coordinate a function of two fields Tower has carried since
            // Milestone 1, so nothing has to be searched for.
            assertThat(binding("docker-local/acme/api:{version}-{shortCommit}")
                    .compose(version("2.5.0", COMMIT)))
                    .contains("docker-local/acme/api:2.5.0-abc1234");
        }

        @Test
        void substitutes_the_full_commit_where_the_template_asks_for_one() {
            assertThat(binding("generic-local/api-{version}-{commit}.tar.gz")
                    .compose(version("2.5.0", COMMIT)))
                    .contains("generic-local/api-2.5.0-" + COMMIT + ".tar.gz");
        }

        @Test
        void takes_seven_characters_of_the_commit_by_default() {
            assertThat(binding("{shortCommit}").compose(version("2.5.0", COMMIT)))
                    .contains("abc1234");
        }

        @Test
        void honours_a_configured_short_commit_length() {
            // Seven is what git gives by default, not a guarantee: git lengthens
            // it where seven would be ambiguous, and a pipeline may have pinned
            // another length years ago.
            assertThat(binding("{shortCommit}", 10).compose(version("2.5.0", COMMIT)))
                    .contains("abc1234def");
        }

        @Test
        void takes_the_whole_commit_when_it_is_shorter_than_the_configured_length() {
            assertThat(binding("{shortCommit}", 12).compose(version("2.5.0", "abc1234")))
                    .contains("abc1234");
        }

        @Test
        void substitutes_the_same_token_twice() {
            assertThat(binding("charts/api-{version}/api-{version}.tgz").compose(version("2.5.0", COMMIT)))
                    .contains("charts/api-2.5.0/api-2.5.0.tgz");
        }

        @Test
        void returns_empty_when_the_version_does_not_carry_what_the_template_asks_for() {
            // Empty is an answer rather than a failure, exactly as it is for a
            // version pattern that did not match. Composing something with a hole
            // in it would ask the repository about a coordinate no build ever
            // wrote.
            assertThat(binding("{version}-{shortCommit}").compose(version("2.5.0", null))).isEmpty();
        }

        @Test
        void composes_regardless_of_a_missing_commit_when_the_template_does_not_want_one() {
            assertThat(binding("charts/api-{version}.tgz").compose(version("2.5.0", null)))
                    .contains("charts/api-2.5.0.tgz");
        }

        @Test
        void reports_which_fields_the_template_needs() {
            assertThat(binding("{version}-{shortCommit}").requiredFields())
                    .containsExactly("version", "shortCommit");
        }
    }

    @Nested
    @DisplayName("refuses a template it could only mis-compose")
    class Validation {

        @Test
        void rejects_a_token_it_does_not_recognise() {
            // Left to stand for itself, {shortcommit} would become a literal, the
            // repository would truthfully answer that no such artifact exists,
            // and the report would be useless. Refusing it here turns a mystery
            // into a message.
            assertThatThrownBy(() -> binding("api:{version}-{shortcommit}"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("{shortcommit}")
                    .hasMessageContaining("{shortCommit}");
        }

        @Test
        void names_every_unrecognised_token_rather_than_only_the_first() {
            assertThatThrownBy(() -> binding("{sha}/api:{tag}"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("{sha}")
                    .hasMessageContaining("{tag}");
        }

        @Test
        void rejects_a_template_that_names_no_part_of_the_version() {
            // Every version would resolve to the same artifact, which is never
            // what somebody meant.
            assertThatThrownBy(() -> binding("docker-local/acme/api:latest"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("every version would resolve to the same artifact");
        }

        @Test
        void rejects_a_blank_template() {
            assertThatThrownBy(() -> binding("  "))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("template");
        }

        @Test
        void rejects_a_short_commit_length_no_git_would_produce() {
            assertThatThrownBy(() -> binding("{shortCommit}", 41))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("between 4 and 40");
            assertThatThrownBy(() -> binding("{shortCommit}", 2))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        void requires_a_kind() {
            assertThatThrownBy(() -> new ArtifactCoordinateBinding(ApplicationId.newId(),
                    "artifactory", " ", "https://artifacts.example.com", "{version}", 0))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("artifact kind");
        }
    }

    @Nested
    @DisplayName("treats a kind as a key a person types twice")
    class Kinds {

        @Test
        void folds_the_case_of_a_kind() {
            // "Image" beside "image" would be two rows in the same table saying
            // the same thing, and V14's primary key carries the kind.
            assertThat(new ArtifactCoordinateBinding(ApplicationId.newId(), "artifactory", "Image",
                    "https://artifacts.example.com", "{version}", 0).kind()).isEqualTo("image");
        }

        @Test
        void keeps_the_team_s_own_word_otherwise() {
            // Tower never interprets a kind (FR-083). "helm-chart" is not
            // rewritten to "chart", and a fourth kind nobody anticipated works.
            assertThat(new ArtifactCoordinateBinding(ApplicationId.newId(), "artifactory",
                    "helm-chart", "https://artifacts.example.com", "{version}", 0).kind())
                    .isEqualTo("helm-chart");
        }
    }
}
