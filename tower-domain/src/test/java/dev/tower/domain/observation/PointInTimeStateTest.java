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

/**
 * Milestone 4, ADR-017: a Snapshot is derived, not stored.
 *
 * <p>What is under test is that the Observation stream already holds the
 * history — that reconstructing a past state needs nothing but the same fold
 * with an upper bound, and that any instant is answerable rather than only the
 * ones somebody captured.
 */
@DisplayName("State at a point in time")
class PointInTimeStateTest {

    private static final EnvironmentId UAT = EnvironmentId.newId();
    private static final ApplicationId CUSTOMER = ApplicationId.newId();
    private static final ApplicationId ORDERS = ApplicationId.newId();

    private static final ApplicationVersionId CUSTOMER_240 = ApplicationVersionId.newId();
    private static final ApplicationVersionId CUSTOMER_250 = ApplicationVersionId.newId();
    private static final ApplicationVersionId ORDERS_190 = ApplicationVersionId.newId();

    private static final Instant MARCH = Instant.parse("2026-03-15T10:00:00Z");
    private static final Instant APRIL = Instant.parse("2026-04-20T10:00:00Z");
    private static final Instant MAY = Instant.parse("2026-05-05T10:00:00Z");

    private static Observation seen(
            EnvironmentId environment, ApplicationId application,
            ApplicationVersionId version, Instant at) {
        return Observation.record(environment, application, version, at,
                ObservationSource.manual("costin"));
    }

    /**
     * Customer API 2.4.0 in March, upgraded to 2.5.0 in May. Orders API arrives
     * in April.
     *
     * <p>Built once and shared, because Observation.record mints a fresh id per
     * call — rebuilding it per assertion would make two derivations of "the same"
     * stream differ by id alone, which is a property of the fixture rather than
     * of the derivation.
     */
    private static final List<Observation> STREAM = List.of(
            seen(UAT, CUSTOMER, CUSTOMER_240, MARCH),
            seen(UAT, ORDERS, ORDERS_190, APRIL),
            seen(UAT, CUSTOMER, CUSTOMER_250, MAY));

    private static List<Observation> stream() {
        return STREAM;
    }

    @Nested
    @DisplayName("reconstructs what was deployed then")
    class Reconstructing {

        @Test
        void answers_for_an_instant_nobody_captured() {
            // The whole argument for deriving. Nobody took a snapshot on 1 April,
            // and the question is still answerable.
            EnvironmentState april = EnvironmentState.asOf(
                    UAT, stream(), Instant.parse("2026-04-01T00:00:00Z"));

            assertThat(april.deploymentOf(CUSTOMER))
                    .get().extracting(EnvironmentState.DeployedApplication::applicationVersionId)
                    .isEqualTo(CUSTOMER_240);
            assertThat(april.deploymentOf(ORDERS)).isEmpty();
        }

        @Test
        void excludes_what_had_not_been_observed_yet() {
            EnvironmentState march = EnvironmentState.asOf(UAT, stream(), MARCH);

            assertThat(march.deployed()).hasSize(1);
            assertThat(march.deploymentOf(ORDERS)).isEmpty();
        }

        @Test
        void includes_an_observation_made_exactly_at_the_instant() {
            // At, not before: asking "as of 15 March 10:00" should include what
            // was observed at 15 March 10:00.
            assertThat(EnvironmentState.asOf(UAT, stream(), MARCH).deployed()).hasSize(1);
        }

        @Test
        void takes_the_newest_version_at_or_before_the_instant() {
            EnvironmentState afterUpgrade = EnvironmentState.asOf(
                    UAT, stream(), Instant.parse("2026-06-01T00:00:00Z"));

            assertThat(afterUpgrade.deploymentOf(CUSTOMER))
                    .get().extracting(EnvironmentState.DeployedApplication::applicationVersionId)
                    .isEqualTo(CUSTOMER_250);
        }

        @Test
        void an_instant_before_anything_was_observed_is_empty_rather_than_current() {
            EnvironmentState before = EnvironmentState.asOf(
                    UAT, stream(), Instant.parse("2026-01-01T00:00:00Z"));

            assertThat(before.hasBeenObserved()).isFalse();
        }

        @Test
        void a_null_instant_is_current_state() {
            // from() and asOf(null) must agree, because from() now delegates.
            assertThat(EnvironmentState.asOf(UAT, stream(), null))
                    .isEqualTo(EnvironmentState.from(UAT, stream()));
        }

        @Test
        void still_cites_the_observation_behind_each_value() {
            // FR-031: a historical answer that cannot be traced back to the fact
            // behind it invites a decision nobody can check.
            EnvironmentState march = EnvironmentState.asOf(UAT, stream(), MARCH);

            assertThat(march.deployed()).allSatisfy(deployed -> {
                assertThat(deployed.observationId()).isNotNull();
                assertThat(deployed.source()).isNotNull();
            });
        }

        @Test
        void asking_twice_gives_the_same_answer() {
            // Immutable in the sense the Glossary requires: the Observations
            // behind it are immutable, so the derivation is stable.
            assertThat(EnvironmentState.asOf(UAT, stream(), APRIL))
                    .isEqualTo(EnvironmentState.asOf(UAT, stream(), APRIL));
        }
    }

    @Nested
    @DisplayName("compares two derived states")
    class Comparing {

        @Test
        void reports_an_application_whose_version_changed() {
            var before = EnvironmentState.asOf(UAT, stream(), MARCH);
            var after = EnvironmentState.asOf(UAT, stream(), MAY);

            StateComparison comparison = StateComparison.between(before, after);

            assertThat(comparison.differences()).anySatisfy(difference -> {
                assertThat(difference.applicationId()).isEqualTo(CUSTOMER);
                assertThat(difference.leftVersion()).isEqualTo(CUSTOMER_240);
                assertThat(difference.rightVersion()).isEqualTo(CUSTOMER_250);
                assertThat(difference.changed()).isTrue();
            });
        }

        @Test
        void distinguishes_arrived_from_changed() {
            // Orders API was not observed in March and was in May. That is a
            // different fact from a version changing, and a comparison that
            // showed only versions would conflate them.
            var before = EnvironmentState.asOf(UAT, stream(), MARCH);
            var after = EnvironmentState.asOf(UAT, stream(), MAY);

            assertThat(StateComparison.between(before, after).differences())
                    .filteredOn(d -> d.applicationId().equals(ORDERS))
                    .singleElement()
                    .satisfies(difference -> {
                        assertThat(difference.onlyOnRight()).isTrue();
                        assertThat(difference.changed()).isFalse();
                        assertThat(difference.leftVersion()).isNull();
                    });
        }

        @Test
        void reports_what_matched_rather_than_only_what_differed() {
            // The question behind a comparison is usually whether two things
            // match, which needs the matches to be visible.
            var state = EnvironmentState.asOf(UAT, stream(), MAY);

            StateComparison comparison = StateComparison.between(state, state);

            assertThat(comparison.isIdentical()).isTrue();
            assertThat(comparison.unchanged()).hasSize(2);
        }

        @Test
        void cites_the_observation_on_both_sides_of_a_difference() {
            var comparison = StateComparison.between(
                    EnvironmentState.asOf(UAT, stream(), MARCH),
                    EnvironmentState.asOf(UAT, stream(), MAY));

            assertThat(comparison.differences())
                    .filteredOn(StateComparison.Difference::changed)
                    .allSatisfy(difference -> {
                        assertThat(difference.leftObservationId()).isNotNull();
                        assertThat(difference.rightObservationId()).isNotNull();
                    });
        }

        @Test
        void compares_two_environments_at_one_instant_just_as_readily() {
            // The same type answers both questions, which is why the sides are
            // called left and right rather than before and after.
            EnvironmentId production = EnvironmentId.newId();
            List<Observation> both = List.of(
                    seen(UAT, CUSTOMER, CUSTOMER_250, MAY),
                    seen(production, CUSTOMER, CUSTOMER_240, MAY));

            StateComparison comparison = StateComparison.between(
                    EnvironmentState.asOf(UAT, both, MAY),
                    EnvironmentState.asOf(production, both, MAY));

            assertThat(comparison.differences()).singleElement()
                    .satisfies(difference -> {
                        assertThat(difference.leftVersion()).isEqualTo(CUSTOMER_250);
                        assertThat(difference.rightVersion()).isEqualTo(CUSTOMER_240);
                    });
        }

        @Test
        void produces_the_same_result_every_time() {
            var left = EnvironmentState.asOf(UAT, stream(), MARCH);
            var right = EnvironmentState.asOf(UAT, stream(), MAY);

            assertThat(StateComparison.between(left, right))
                    .isEqualTo(StateComparison.between(left, right));
        }
    }
}
