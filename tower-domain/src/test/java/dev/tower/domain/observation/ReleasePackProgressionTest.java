package dev.tower.domain.observation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.releasepack.ReleasePackId;

/**
 * Milestone 4: where a Release Pack got to, and when (ADR-017).
 *
 * <p>The case that shapes the design is a release arriving piecemeal. A single
 * "arrived at" instant would have to choose between the moment the first version
 * appeared and the moment the last one did, and those can be days apart.
 */
@DisplayName("Release Pack progression")
class ReleasePackProgressionTest {

    private static final ReleasePackId PACK = ReleasePackId.newId();

    private static final EnvironmentId SIT = EnvironmentId.newId();
    private static final EnvironmentId UAT = EnvironmentId.newId();

    private static final ApplicationId CUSTOMER = ApplicationId.newId();
    private static final ApplicationId ORDERS = ApplicationId.newId();
    private static final ApplicationVersionId CUSTOMER_250 = ApplicationVersionId.newId();
    private static final ApplicationVersionId ORDERS_190 = ApplicationVersionId.newId();
    private static final ApplicationVersionId NOT_IN_PACK = ApplicationVersionId.newId();

    private static final List<ApplicationVersionId> CONTENTS = List.of(CUSTOMER_250, ORDERS_190);

    private static final Instant MON = Instant.parse("2026-08-03T09:00:00Z");
    private static final Instant TUE = Instant.parse("2026-08-04T09:00:00Z");
    private static final Instant WED = Instant.parse("2026-08-05T09:00:00Z");

    private static Observation seen(
            EnvironmentId environment, ApplicationId application,
            ApplicationVersionId version, Instant at) {
        return Observation.record(environment, application, version, at,
                ObservationSource.manual("costin"));
    }

    @Nested
    @DisplayName("reports when a release arrived, whole or in part")
    class Arrival {

        @Test
        void a_release_seen_in_full_reports_both_instants() {
            var progression = ReleasePackProgression.from(PACK, CONTENTS, List.of(
                    seen(SIT, CUSTOMER, CUSTOMER_250, MON),
                    seen(SIT, ORDERS, ORDERS_190, WED)));

            assertThat(progression.in(SIT)).get().satisfies(arrival -> {
                assertThat(arrival.firstObservedAt()).isEqualTo(MON);
                // When the last piece landed, not the first — that is when the
                // release as a whole was there.
                assertThat(arrival.completeAt()).isEqualTo(WED);
                assertThat(arrival.isComplete()).isTrue();
                assertThat(arrival.missing()).isEmpty();
            });
        }

        @Test
        void a_release_still_arriving_is_partial_and_names_what_is_missing() {
            // Counting alone would say "1 of 2" and leave the reader to work out
            // which one they are waiting for.
            var progression = ReleasePackProgression.from(PACK, CONTENTS, List.of(
                    seen(UAT, CUSTOMER, CUSTOMER_250, TUE)));

            assertThat(progression.in(UAT)).get().satisfies(arrival -> {
                assertThat(arrival.isComplete()).isFalse();
                assertThat(arrival.isPartial()).isTrue();
                assertThat(arrival.completeAt()).isNull();
                assertThat(arrival.observedCount()).isEqualTo(1);
                assertThat(arrival.packedCount()).isEqualTo(2);
                assertThat(arrival.missing()).containsExactly(ORDERS_190);
            });
        }

        @Test
        void takes_the_earliest_sighting_of_each_version_rather_than_the_latest() {
            // A version re-observed later did not arrive later.
            var progression = ReleasePackProgression.from(PACK, CONTENTS, List.of(
                    seen(SIT, CUSTOMER, CUSTOMER_250, MON),
                    seen(SIT, CUSTOMER, CUSTOMER_250, WED),
                    seen(SIT, ORDERS, ORDERS_190, TUE)));

            assertThat(progression.in(SIT)).get().satisfies(arrival -> {
                assertThat(arrival.firstObservedAt()).isEqualTo(MON);
                assertThat(arrival.completeAt()).isEqualTo(TUE);
            });
        }

        @Test
        void orders_environments_by_when_the_release_reached_them() {
            // The order it actually travelled in, which is the story a
            // progression is asked for.
            var progression = ReleasePackProgression.from(PACK, CONTENTS, List.of(
                    seen(UAT, CUSTOMER, CUSTOMER_250, WED),
                    seen(SIT, CUSTOMER, CUSTOMER_250, MON)));

            assertThat(progression.arrivals())
                    .extracting(ReleasePackProgression.EnvironmentArrival::environmentId)
                    .containsExactly(SIT, UAT);
        }
    }

    @Nested
    @DisplayName("reports only what it was told")
    class Honesty {

        @Test
        void an_environment_the_release_was_never_seen_in_does_not_appear() {
            // Absence of an Observation is not evidence the release is absent —
            // it may mean nobody looked (Scenario 4).
            var progression = ReleasePackProgression.from(PACK, CONTENTS, List.of(
                    seen(SIT, CUSTOMER, CUSTOMER_250, MON)));

            assertThat(progression.in(UAT)).isEmpty();
        }

        @Test
        void ignores_observations_of_versions_the_pack_does_not_contain() {
            var progression = ReleasePackProgression.from(PACK, CONTENTS, List.of(
                    seen(SIT, CUSTOMER, NOT_IN_PACK, MON)));

            assertThat(progression.hasBeenObserved()).isFalse();
        }

        @Test
        void a_release_never_observed_anywhere_reports_nothing_rather_than_failing() {
            var progression = ReleasePackProgression.from(PACK, CONTENTS, List.of());

            assertThat(progression.hasBeenObserved()).isFalse();
            assertThat(progression.arrivals()).isEmpty();
        }

        @Test
        void an_empty_release_pack_is_complete_nowhere_rather_than_everywhere() {
            // A pack with no contents has nothing to arrive. Treating "no missing
            // versions" as complete would report it as fully deployed in an
            // Environment it was never seen in.
            var progression = ReleasePackProgression.from(PACK, List.of(), List.of(
                    seen(SIT, CUSTOMER, CUSTOMER_250, MON)));

            assertThat(progression.arrivals()).isEmpty();
        }
    }
}
