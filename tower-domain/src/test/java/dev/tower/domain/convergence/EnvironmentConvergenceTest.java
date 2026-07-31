package dev.tower.domain.convergence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.observation.Observation;
import dev.tower.domain.observation.ObservationSource;
import dev.tower.domain.observation.ReleasePackProgression;
import dev.tower.domain.releasepack.ReleasePackId;

/**
 * Milestone 5, issue #7: which releases are competing for one Environment.
 *
 * <p>The constraint that shapes this type is not a calculation but a refusal.
 * Tower shows the situation and does not rank the packs, so the tests that
 * matter most here are the ones about ordering and about what an absence means.
 */
@DisplayName("Convergence on one Environment")
class EnvironmentConvergenceTest {

    private static final EnvironmentId UAT = EnvironmentId.newId();
    private static final EnvironmentId PRODUCTION = EnvironmentId.newId();

    private static final ApplicationId CUSTOMER = ApplicationId.newId();
    private static final ApplicationId ORDERS = ApplicationId.newId();
    private static final ApplicationVersionId CUSTOMER_250 = ApplicationVersionId.newId();
    private static final ApplicationVersionId ORDERS_190 = ApplicationVersionId.newId();

    private static final Instant MON = Instant.parse("2026-08-03T09:00:00Z");
    private static final Instant TUE = Instant.parse("2026-08-04T09:00:00Z");

    private static Observation seen(
            EnvironmentId environment, ApplicationId application,
            ApplicationVersionId version, Instant at) {
        return Observation.record(environment, application, version, at,
                ObservationSource.manual("costin"));
    }

    private static EnvironmentConvergence.PackProgression pack(
            String name, List<ApplicationVersionId> contents, List<Observation> observations) {
        ReleasePackId id = ReleasePackId.newId();
        return new EnvironmentConvergence.PackProgression(id, name, contents.size(),
                ReleasePackProgression.from(id, contents, observations));
    }

    @Nested
    @DisplayName("shows the situation without ranking it")
    class WithoutRanking {

        @Test
        void orders_packs_by_name_and_not_by_how_far_along_they_are() {
            // The one thing this type must never do. A dashboard that put the
            // nearest-to-arriving release at the top would be recommending it,
            // and the decision belongs to the business team (ADR-001).
            var convergence = EnvironmentConvergence.from(UAT, List.of(
                    pack("Zebra release", List.of(CUSTOMER_250), List.of(
                            seen(UAT, CUSTOMER, CUSTOMER_250, MON))),
                    pack("Alpha release", List.of(CUSTOMER_250), List.of())));

            assertThat(convergence.packs())
                    .extracting(EnvironmentConvergence.ConvergingPack::name)
                    .containsExactly("Alpha release", "Zebra release");
        }

        @Test
        void names_contention_rather_than_leaving_it_to_be_counted() {
            var contested = EnvironmentConvergence.from(UAT, List.of(
                    pack("Release A", List.of(CUSTOMER_250), List.of()),
                    pack("Release B", List.of(ORDERS_190), List.of())));

            assertThat(contested.isContested()).isTrue();
        }

        @Test
        void one_release_heading_for_an_environment_is_not_contention() {
            var alone = EnvironmentConvergence.from(UAT, List.of(
                    pack("Release A", List.of(CUSTOMER_250), List.of())));

            assertThat(alone.isContested()).isFalse();
        }

        @Test
        void an_environment_no_release_is_heading_for_is_empty_rather_than_absent() {
            // The Environment still exists and still deserves a row; it simply
            // has nothing converging on it.
            var quiet = EnvironmentConvergence.from(UAT, List.of());

            assertThat(quiet.packs()).isEmpty();
            assertThat(quiet.isContested()).isFalse();
        }
    }

    @Nested
    @DisplayName("says what it was told, not what is running")
    class Standing {

        @Test
        void a_pack_seen_in_full_here_is_fully_observed() {
            var convergence = EnvironmentConvergence.from(UAT, List.of(
                    pack("Release A", List.of(CUSTOMER_250, ORDERS_190), List.of(
                            seen(UAT, CUSTOMER, CUSTOMER_250, MON),
                            seen(UAT, ORDERS, ORDERS_190, TUE)))));

            assertThat(convergence.packs()).singleElement().satisfies(entry -> {
                assertThat(entry.standing())
                        .isEqualTo(EnvironmentConvergence.Standing.FULLY_OBSERVED);
                assertThat(entry.firstObservedAt()).isEqualTo(MON);
                assertThat(entry.completeAt()).isEqualTo(TUE);
                assertThat(entry.observedCount()).isEqualTo(2);
            });
        }

        @Test
        void a_pack_half_here_is_partly_observed_and_keeps_its_counts() {
            var convergence = EnvironmentConvergence.from(UAT, List.of(
                    pack("Release A", List.of(CUSTOMER_250, ORDERS_190), List.of(
                            seen(UAT, CUSTOMER, CUSTOMER_250, MON)))));

            assertThat(convergence.packs()).singleElement().satisfies(entry -> {
                assertThat(entry.standing())
                        .isEqualTo(EnvironmentConvergence.Standing.PARTLY_OBSERVED);
                assertThat(entry.completeAt()).isNull();
                assertThat(entry.observedCount()).isEqualTo(1);
                assertThat(entry.packedCount()).isEqualTo(2);
            });
        }

        @Test
        void a_pack_never_seen_here_is_not_observed_rather_than_not_deployed() {
            // Scenario 4. The pack's Promotion Path names UAT, so it is expected
            // here — but nobody has said it arrived, and that is all Tower knows.
            var convergence = EnvironmentConvergence.from(UAT, List.of(
                    pack("Release A", List.of(CUSTOMER_250), List.of(
                            seen(PRODUCTION, CUSTOMER, CUSTOMER_250, MON)))));

            assertThat(convergence.packs()).singleElement().satisfies(entry -> {
                assertThat(entry.standing())
                        .isEqualTo(EnvironmentConvergence.Standing.NOT_OBSERVED_HERE);
                assertThat(entry.firstObservedAt()).isNull();
                assertThat(entry.observedCount()).isZero();
                // Still known, even with no arrival to read it from — otherwise
                // the row could not say "0 of 2".
                assertThat(entry.packedCount()).isEqualTo(1);
            });
        }

        @Test
        void lists_the_packs_still_awaited_here() {
            var convergence = EnvironmentConvergence.from(UAT, List.of(
                    pack("Arrived", List.of(CUSTOMER_250), List.of(
                            seen(UAT, CUSTOMER, CUSTOMER_250, MON))),
                    pack("Awaited", List.of(ORDERS_190), List.of())));

            assertThat(convergence.notObservedHere())
                    .extracting(EnvironmentConvergence.ConvergingPack::name)
                    .containsExactly("Awaited");
        }

        @Test
        void reads_only_the_arrival_for_this_environment() {
            // A pack fully in Production has not thereby arrived in UAT.
            var convergence = EnvironmentConvergence.from(UAT, List.of(
                    pack("Release A", List.of(CUSTOMER_250), List.of(
                            seen(PRODUCTION, CUSTOMER, CUSTOMER_250, MON)))));

            assertThat(convergence.packs()).singleElement()
                    .extracting(EnvironmentConvergence.ConvergingPack::standing)
                    .isEqualTo(EnvironmentConvergence.Standing.NOT_OBSERVED_HERE);
        }
    }
}
