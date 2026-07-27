package dev.tower.domain.promotionpath;

import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.environment.Stage;
import dev.tower.domain.shared.DomainException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromotionPathTest {

    private static final Instant NOW = Instant.parse("2026-07-27T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-08-01T10:00:00Z");

    private final Environment dev1 = Environment.create("Dev1", Stage.DEVELOPMENT);
    private final Environment devHotfix = Environment.create("Dev-Hotfix", Stage.DEVELOPMENT);
    private final Environment sit1 = Environment.create("SIT1", Stage.VALIDATION);
    private final Environment uat = Environment.create("UAT", Stage.VALIDATION);
    private final Environment preProd = Environment.create("PreProd", Stage.PRE_PRODUCTION);
    private final Environment production = Environment.create("Production", Stage.PRODUCTION);

    private List<EnvironmentId> regularPath() {
        return List.of(dev1.id(), sit1.id(), uat.id(), preProd.id(), production.id());
    }

    private List<EnvironmentId> hotfixPath() {
        return List.of(devHotfix.id(), sit1.id(), uat.id(), production.id());
    }

    @Nested
    class SharedEnvironments {

        /**
         * ADR-005. The Regular and Hotfix paths in Scenarios.md both reach UAT and
         * Production. Before ADR-005 the Domain Model said an Environment belongs
         * to one Promotion Path, which made the documented scenarios illegal.
         */
        @Test
        void two_paths_may_converge_on_the_same_environments() {
            PromotionPath regular = PromotionPath.create("Regular", regularPath(), NOW);
            PromotionPath hotfix = PromotionPath.create("Hotfix", hotfixPath(), NOW);

            assertThat(regular.referencesEnvironment(uat.id())).isTrue();
            assertThat(hotfix.referencesEnvironment(uat.id())).isTrue();
            assertThat(regular.referencesEnvironment(production.id())).isTrue();
            assertThat(hotfix.referencesEnvironment(production.id())).isTrue();

            // Convergence must not imply equivalence: the paths reach the shared
            // Environment from different upstream topologies, and which pack
            // proceeds past it stays a human decision (ADR-005, ADR-001).
            assertThat(regular.currentVersion().positionOf(uat.id())).isEqualTo(2);
            assertThat(hotfix.currentVersion().positionOf(uat.id())).isEqualTo(2);
            assertThat(regular.currentVersion().length()).isNotEqualTo(hotfix.currentVersion().length());
        }

        @Test
        void an_environment_may_not_appear_twice_within_one_path() {
            assertThatThrownBy(() -> PromotionPath.create("Broken", List.of(dev1.id(), uat.id(), uat.id()), NOW))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("more than once");
        }
    }

    @Nested
    class Versioning {

        /**
         * ADR-007. Editing publishes a new version; the old one is untouched so a
         * Release Pack referencing it still reports the topology it followed.
         */
        @Test
        void editing_publishes_a_new_version_and_leaves_earlier_versions_intact() {
            PromotionPath path = PromotionPath.create("Regular", regularPath(), NOW);
            PromotionPathVersion original = path.currentVersion();

            PromotionPath edited = path.withNewVersion(
                    List.of(dev1.id(), sit1.id(), uat.id(), production.id()), LATER);

            assertThat(edited.versions()).hasSize(2);
            assertThat(edited.currentVersion().number()).isEqualTo(2);
            assertThat(edited.currentVersion().length()).isEqualTo(4);

            // The whole point of ADR-007: history did not change under us.
            assertThat(edited.version(1)).contains(original);
            assertThat(edited.version(1).orElseThrow().length()).isEqualTo(5);
            assertThat(edited.version(1).orElseThrow().contains(preProd.id())).isTrue();
        }

        @Test
        void identity_survives_editing() {
            PromotionPath path = PromotionPath.create("Regular", regularPath(), NOW);
            PromotionPath edited = path.withNewVersion(List.of(dev1.id(), production.id()), LATER);

            assertThat(edited.id()).isEqualTo(path.id());
            assertThat(edited).isEqualTo(path);
        }

        @Test
        void versions_are_numbered_consecutively_from_one() {
            PromotionPath path = PromotionPath.create("Regular", regularPath(), NOW)
                    .withNewVersion(List.of(dev1.id(), uat.id()), LATER);

            assertThat(path.versions()).extracting(PromotionPathVersion::number).containsExactly(1, 2);
        }

        @Test
        void reconstitution_rejects_a_gap_in_version_numbering() {
            PromotionPathVersion first = new PromotionPathVersion(1, List.of(dev1.id()), NOW);
            PromotionPathVersion third = new PromotionPathVersion(3, List.of(uat.id()), LATER);

            assertThatThrownBy(() -> PromotionPath.reconstitute(
                    PromotionPathId.newId(), "Broken", List.of(first, third), false))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("consecutively");
        }
    }

    @Nested
    class Archival {

        /** ADR-007 replaces deletion with archival once a path is referenced. */
        @Test
        void an_archived_path_accepts_no_new_versions() {
            PromotionPath archived = PromotionPath.create("Regular", regularPath(), NOW).archive();

            assertThat(archived.isArchived()).isTrue();
            assertThatThrownBy(() -> archived.withNewVersion(List.of(dev1.id()), LATER))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("archived");
        }

        @Test
        void an_archived_path_remains_readable() {
            PromotionPath archived = PromotionPath.create("Regular", regularPath(), NOW).archive();

            assertThat(archived.currentVersion().length()).isEqualTo(5);
            assertThat(archived.referencesEnvironment(uat.id())).isTrue();
        }

        @Test
        void archival_can_be_undone() {
            PromotionPath restored = PromotionPath.create("Regular", regularPath(), NOW).archive().restore();

            assertThat(restored.isArchived()).isFalse();
            assertThat(restored.withNewVersion(List.of(dev1.id()), LATER).versions()).hasSize(2);
        }
    }

    @Nested
    class Validation {

        @Test
        void a_path_requires_at_least_one_environment() {
            assertThatThrownBy(() -> PromotionPath.create("Empty", List.of(), NOW))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("at least one Environment");
        }

        @Test
        void a_path_requires_a_name() {
            assertThatThrownBy(() -> PromotionPath.create("  ", regularPath(), NOW))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("name is required");
        }

        @Test
        void the_environment_sequence_is_immutable_once_published() {
            PromotionPath path = PromotionPath.create("Regular", regularPath(), NOW);

            assertThatThrownBy(() -> path.currentVersion().environments().add(dev1.id()))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void first_and_last_describe_the_sequence_ends() {
            PromotionPathVersion version = PromotionPath.create("Regular", regularPath(), NOW).currentVersion();

            assertThat(version.first()).isEqualTo(dev1.id());
            assertThat(version.last()).isEqualTo(production.id());
            assertThat(version.positionOf(EnvironmentId.newId())).isEqualTo(-1);
        }
    }
}
