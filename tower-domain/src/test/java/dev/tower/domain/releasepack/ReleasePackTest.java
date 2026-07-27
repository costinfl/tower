package dev.tower.domain.releasepack;

import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.handover.Handover;
import dev.tower.domain.iteration.Iteration;
import dev.tower.domain.promotionpath.PromotionPathId;
import dev.tower.domain.shared.DomainException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReleasePackTest {

    private static final Instant NOW = Instant.parse("2026-07-27T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-07-29T16:00:00Z");

    private final ApplicationId customerApi = ApplicationId.newId();
    private final ApplicationId ordersApi = ApplicationId.newId();
    private final ApplicationVersionId customerV1 = ApplicationVersionId.newId();
    private final ApplicationVersionId customerV2 = ApplicationVersionId.newId();
    private final ApplicationVersionId ordersV1 = ApplicationVersionId.newId();

    private ReleasePack pack() {
        return ReleasePack.create("Release 2026.08", "August business features");
    }

    @Nested
    class Contents {

        @Test
        void groups_versions_of_different_applications() {
            ReleasePack pack = pack()
                    .addApplicationVersion(customerApi, customerV1)
                    .addApplicationVersion(ordersApi, ordersV1);

            assertThat(pack.contents()).hasSize(2);
            assertThat(pack.contains(customerV1)).isTrue();
            assertThat(pack.versionsByApplication())
                    .containsEntry(customerApi, customerV1)
                    .containsEntry(ordersApi, ordersV1);
        }

        /**
         * A Release Pack describes what is delivered together, and two versions
         * of one Application cannot be — one would simply overwrite the other.
         */
        @Test
        void refuses_two_versions_of_the_same_application() {
            ReleasePack pack = pack().addApplicationVersion(customerApi, customerV1);

            assertThatThrownBy(() -> pack.addApplicationVersion(customerApi, customerV2))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("already contains a different version");
        }

        @Test
        void swapping_a_version_requires_removing_the_old_one_first() {
            ReleasePack pack = pack()
                    .addApplicationVersion(customerApi, customerV1)
                    .removeApplicationVersion(customerV1)
                    .addApplicationVersion(customerApi, customerV2);

            assertThat(pack.versionsByApplication()).containsEntry(customerApi, customerV2);
            assertThat(pack.contains(customerV1)).isFalse();
        }

        @Test
        void refuses_the_same_version_twice() {
            ReleasePack pack = pack().addApplicationVersion(customerApi, customerV1);

            assertThatThrownBy(() -> pack.addApplicationVersion(customerApi, customerV1))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("already in the Release Pack");
        }

        @Test
        void removing_something_absent_is_reported_rather_than_ignored() {
            assertThatThrownBy(() -> pack().removeApplicationVersion(ordersV1))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("not in the Release Pack");
        }
    }

    @Nested
    class PromotionPathPinning {

        /**
         * ADR-007. The pack pins a path <em>version</em>. Editing that path later
         * publishes a new version and leaves this pack pointing at the topology
         * it was planned against.
         */
        @Test
        void pins_a_specific_promotion_path_version() {
            PromotionPathId regular = PromotionPathId.newId();

            ReleasePack pack = pack().assignPromotionPath(regular, 1);

            assertThat(pack.promotionPath()).isPresent();
            assertThat(pack.promotionPath().orElseThrow().pathId()).isEqualTo(regular);
            assertThat(pack.promotionPath().orElseThrow().versionNumber()).isEqualTo(1);
        }

        @Test
        void a_pack_may_be_repointed_at_a_newer_version() {
            PromotionPathId regular = PromotionPathId.newId();

            ReleasePack pack = pack().assignPromotionPath(regular, 1).assignPromotionPath(regular, 2);

            assertThat(pack.promotionPath().orElseThrow().versionNumber()).isEqualTo(2);
        }

        @Test
        void a_version_number_below_one_is_rejected() {
            assertThatThrownBy(() -> pack().assignPromotionPath(PromotionPathId.newId(), 0))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("starts at 1");
        }

        /** SM-05: a Release Pack may exist before any deployment, or any plan. */
        @Test
        void a_pack_may_exist_with_no_promotion_path_at_all() {
            assertThat(pack().promotionPath()).isEmpty();
        }
    }

    @Nested
    class HandoverAndIterations {

        @Test
        void a_new_pack_starts_with_empty_handover() {
            assertThat(pack().handover().isEmpty()).isTrue();
        }

        @Test
        void handover_records_what_another_team_must_run() {
            Handover handover = new Handover(
                    "Deploy customer-api before orders-api.",
                    "kubectl rollout status deploy/customer-api",
                    "V37__add_customer_index.sql",
                    "Roll back orders-api first.",
                    "Smoke test the checkout journey.",
                    "Expect elevated latency for ten minutes.");

            ReleasePack pack = pack().updateHandover(handover);

            assertThat(pack.handover().isEmpty()).isFalse();
            assertThat(pack.handover().shellCommands()).contains("kubectl rollout status");
        }

        @Test
        void iterations_track_validation_cycles() {
            ReleasePack pack = pack()
                    .startIteration("SIT Iteration 1", NOW, "First pass")
                    .startIteration("UAT Iteration", LATER, "");

            assertThat(pack.iterations()).hasSize(2);
            assertThat(pack.iterations().get(0).isComplete()).isFalse();
        }

        @Test
        void an_iteration_can_be_completed_and_reopened() {
            ReleasePack pack = pack().startIteration("SIT Iteration 1", NOW, "");
            Iteration started = pack.iterations().get(0);

            ReleasePack completed = pack.replaceIteration(started.complete(LATER));
            assertThat(completed.iterations().get(0).isComplete()).isTrue();

            ReleasePack reopened = completed.replaceIteration(completed.iterations().get(0).reopen());
            assertThat(reopened.iterations().get(0).isComplete()).isFalse();
        }

        @Test
        void an_iteration_cannot_complete_before_it_started() {
            Iteration started = Iteration.start("SIT Iteration 1", LATER, "");

            assertThatThrownBy(() -> started.complete(NOW))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("cannot complete before it started");
        }

        @Test
        void an_iteration_from_another_pack_is_rejected() {
            Iteration foreign = Iteration.start("SIT Iteration 1", NOW, "");

            assertThatThrownBy(() -> pack().replaceIteration(foreign))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("does not belong");
        }
    }

    @Nested
    class Lifecycle {

        /**
         * ADR-008 separates the archived flag from derived observed state.
         * Archiving says the team stopped working the release. It erases nothing.
         */
        @Test
        void archiving_preserves_everything_the_pack_recorded() {
            ReleasePack archived = pack()
                    .addApplicationVersion(customerApi, customerV1)
                    .assignPromotionPath(PromotionPathId.newId(), 1)
                    .startIteration("UAT Iteration", NOW, "signed off")
                    .archive();

            assertThat(archived.isArchived()).isTrue();
            assertThat(archived.contents()).hasSize(1);
            assertThat(archived.iterations()).hasSize(1);
            assertThat(archived.promotionPath()).isPresent();
        }

        @Test
        void an_archived_pack_refuses_changes_until_restored() {
            ReleasePack archived = pack().archive();

            assertThatThrownBy(() -> archived.addApplicationVersion(customerApi, customerV1))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("Restore it first");

            assertThat(archived.restore().addApplicationVersion(customerApi, customerV1).contents()).hasSize(1);
        }

        @Test
        void identity_survives_every_change() {
            ReleasePack original = pack();
            ReleasePack changed = original
                    .updateMetadata("Renamed", "New description")
                    .addApplicationVersion(customerApi, customerV1)
                    .archive();

            assertThat(changed.id()).isEqualTo(original.id());
            assertThat(changed).isEqualTo(original);
        }
    }

    @Nested
    class ApplicationVersionImmutability {

        /** BR-01: Application Versions are immutable, so no mutator exists. */
        @Test
        void an_application_version_exposes_no_mutator() {
            ApplicationVersion version = ApplicationVersion.create(
                    customerApi, "2.5.0", "release/2.5", "v2.5.0", "abc1234", "build-991");

            assertThat(version.version()).isEqualTo("2.5.0");
            assertThat(ApplicationVersion.class.getMethods())
                    .extracting(java.lang.reflect.Method::getName)
                    .noneMatch(methodName -> methodName.startsWith("set")
                            || methodName.startsWith("with")
                            || methodName.startsWith("update"));
        }

        @Test
        void optional_identifying_attributes_may_be_absent() {
            ApplicationVersion version = ApplicationVersion.create(customerApi, "2.5.0", null, "  ", null, null);

            assertThat(version.branch()).isNull();
            assertThat(version.tag()).isNull();
            assertThat(version.buildIdentifier()).isNull();
        }

        @Test
        void a_version_requires_an_application_and_a_version() {
            assertThatThrownBy(() -> ApplicationVersion.create(null, "2.5.0", null, null, null, null))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("must belong to an Application");

            assertThatThrownBy(() -> ApplicationVersion.create(customerApi, "  ", null, null, null, null))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("requires a version");
        }
    }
}
