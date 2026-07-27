package dev.tower.domain.observation;

import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.environment.Stage;
import dev.tower.domain.releasepack.ReleasePackState;
import dev.tower.domain.shared.DomainException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservationTest {

    private static final Instant MONDAY = Instant.parse("2026-08-03T09:00:00Z");
    private static final Instant TUESDAY = Instant.parse("2026-08-04T09:00:00Z");
    private static final Instant WEDNESDAY = Instant.parse("2026-08-05T09:00:00Z");

    private final EnvironmentId uat = EnvironmentId.newId();
    private final EnvironmentId production = EnvironmentId.newId();
    private final ApplicationId customerApi = ApplicationId.newId();
    private final ApplicationId ordersApi = ApplicationId.newId();
    private final ApplicationVersionId customerV1 = ApplicationVersionId.newId();
    private final ApplicationVersionId customerV2 = ApplicationVersionId.newId();
    private final ApplicationVersionId ordersV1 = ApplicationVersionId.newId();

    private Observation seen(EnvironmentId env, ApplicationId app, ApplicationVersionId version, Instant at) {
        return Observation.record(env, app, version, at, ObservationSource.manual("costin"));
    }

    @Nested
    class Immutability {

        /** BR-02, IA-01, SM-06, FR-023 all say the same thing: never edited. */
        @Test
        void an_observation_exposes_no_mutator() {
            assertThat(Observation.class.getMethods())
                    .extracting(java.lang.reflect.Method::getName)
                    .noneMatch(name -> name.startsWith("set") || name.startsWith("with")
                            || name.startsWith("update"));
        }

        @Test
        void an_observation_must_record_when_and_where_it_came_from() {
            assertThatThrownBy(() -> Observation.record(uat, customerApi, customerV1, null,
                    ObservationSource.manual("costin")))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("FR-021");

            assertThatThrownBy(() -> Observation.record(uat, customerApi, customerV1, MONDAY, null))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("FR-022");
        }
    }

    @Nested
    class Provenance {

        /** ADR-006: manual entry names a person rather than a system. */
        @Test
        void a_manual_observation_names_its_collector_and_actor() {
            ObservationSource source = ObservationSource.manual("costin");

            assertThat(source.collector()).isEqualTo("manual");
            assertThat(source.actor()).isEqualTo("costin");
            assertThat(source.isManual()).isTrue();
            assertThat(source.isImported()).isFalse();
        }

        /** ADR-010: an imported fact keeps naming the instance that saw it. */
        @Test
        void an_imported_observation_still_names_its_origin() {
            ObservationSource imported = new ObservationSource("kubernetes", null, "alice-laptop");

            assertThat(imported.isImported()).isTrue();
            assertThat(imported.originInstance()).isEqualTo("alice-laptop");
        }

        @Test
        void an_observation_must_name_a_collector() {
            assertThatThrownBy(() -> new ObservationSource("  ", null, null))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("must name the Collector");
        }
    }

    @Nested
    class DerivedEnvironmentState {

        @Test
        void the_most_recent_observation_of_each_application_wins() {
            EnvironmentState state = EnvironmentState.from(uat, List.of(
                    seen(uat, customerApi, customerV1, MONDAY),
                    seen(uat, customerApi, customerV2, WEDNESDAY),
                    seen(uat, ordersApi, ordersV1, TUESDAY)));

            assertThat(state.deployed()).hasSize(2);
            assertThat(state.deploymentOf(customerApi).orElseThrow().applicationVersionId())
                    .isEqualTo(customerV2);
            assertThat(state.lastObservedAt()).contains(WEDNESDAY);
        }

        @Test
        void observations_from_other_environments_are_ignored() {
            EnvironmentState state = EnvironmentState.from(uat, List.of(
                    seen(uat, customerApi, customerV1, MONDAY),
                    seen(production, ordersApi, ordersV1, WEDNESDAY)));

            assertThat(state.deployed()).hasSize(1);
            assertThat(state.deploymentOf(ordersApi)).isEmpty();
        }

        /** Scenario 4: Tower does not estimate missing information. */
        @Test
        void an_unobserved_environment_says_so_rather_than_looking_empty() {
            EnvironmentState state = EnvironmentState.from(uat, List.of());

            assertThat(state.hasBeenObserved()).isFalse();
            assertThat(state.lastObservedAt()).isEmpty();
        }

        /** Replaying the same facts must not reorder what Tower reports. */
        @Test
        void equal_timestamps_leave_the_incumbent_in_place() {
            Observation first = seen(uat, customerApi, customerV1, MONDAY);
            Observation sameInstant = seen(uat, customerApi, customerV2, MONDAY);

            EnvironmentState state = EnvironmentState.from(uat, List.of(first, sameInstant));

            assertThat(state.deploymentOf(customerApi).orElseThrow().applicationVersionId())
                    .isEqualTo(customerV1);
        }

        /** Every displayed value stays traceable to the fact behind it (FR-031). */
        @Test
        void deployment_carries_the_observation_that_produced_it() {
            Observation observation = seen(uat, customerApi, customerV1, MONDAY);

            EnvironmentState state = EnvironmentState.from(uat, List.of(observation));

            EnvironmentState.DeployedApplication deployed = state.deployed().get(0);
            assertThat(deployed.observationId()).isEqualTo(observation.id());
            assertThat(deployed.source().actor()).isEqualTo("costin");
            assertThat(deployed.observedAt()).isEqualTo(MONDAY);
        }
    }

    @Nested
    class DerivedReleasePackState {

        @Test
        void a_pack_nobody_has_observed_is_planned() {
            assertThat(ReleasePackState.derive(Set.of(customerV1), List.of()))
                    .isEqualTo(ReleasePackState.PLANNED);
            assertThat(ReleasePackState.derive(Set.of(), List.of(
                    new ReleasePackState.Sighting(customerV1, Stage.PRODUCTION))))
                    .isEqualTo(ReleasePackState.PLANNED);
        }

        @Test
        void the_highest_stage_observed_wins_regardless_of_order() {
            ReleasePackState state = ReleasePackState.derive(Set.of(customerV1, ordersV1), List.of(
                    new ReleasePackState.Sighting(customerV1, Stage.PRODUCTION),
                    new ReleasePackState.Sighting(ordersV1, Stage.DEVELOPMENT)));

            assertThat(state).isEqualTo(ReleasePackState.PRODUCTION);
        }

        /**
         * The defect ADR-008 exists to fix. Under the original State Model,
         * Pre-Production was the only documented route into Production, so the
         * Hotfix path — which has no pre-production Environment — could never
         * legally reach Production.
         */
        @Test
        void a_hotfix_path_without_pre_production_still_reaches_production() {
            ReleasePackState state = ReleasePackState.derive(Set.of(customerV1), List.of(
                    new ReleasePackState.Sighting(customerV1, Stage.DEVELOPMENT),
                    new ReleasePackState.Sighting(customerV1, Stage.VALIDATION),
                    new ReleasePackState.Sighting(customerV1, Stage.PRODUCTION)));

            assertThat(state).isEqualTo(ReleasePackState.PRODUCTION);
        }

        @Test
        void sightings_of_versions_outside_the_pack_are_ignored() {
            ReleasePackState state = ReleasePackState.derive(Set.of(customerV1), List.of(
                    new ReleasePackState.Sighting(customerV1, Stage.DEVELOPMENT),
                    new ReleasePackState.Sighting(ordersV1, Stage.PRODUCTION)));

            assertThat(state).isEqualTo(ReleasePackState.DEVELOPMENT);
        }

        /** Archived is Intent, not an observed state, so it is not in this enum. */
        @Test
        void archived_is_not_a_derived_state() {
            assertThat(ReleasePackState.values())
                    .extracting(Enum::name)
                    .doesNotContain("ARCHIVED");
        }
    }
}
