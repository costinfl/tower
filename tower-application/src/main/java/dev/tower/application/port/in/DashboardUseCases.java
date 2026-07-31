package dev.tower.application.port.in;

import java.time.Instant;
import java.util.List;

import dev.tower.domain.convergence.EnvironmentConvergence;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.environment.Stage;
import dev.tower.domain.releasepack.ReleasePackId;
import dev.tower.domain.releasepack.ReleasePackState;

/**
 * Inbound port for the operational dashboard (Milestone 5, issue #7).
 *
 * <p>One view across every active release, so a developer does not have to open
 * each Release Pack to see where things stand — and, above all, so the question
 * "which packs are competing for UAT right now" can be asked once rather than
 * assembled by hand from five pages.
 *
 * <p>Read-only, like everything else. There is no operation here that promotes,
 * deploys or triggers anything, and there is deliberately nothing that ranks or
 * recommends a release (ADR-001, Guardrails.md).
 */
public interface DashboardUseCases {

    /** The whole dashboard in one read, derived on request and stored nowhere. */
    Overview overview();

    /**
     * @param environments  every Environment, including those nothing is heading for
     * @param releasePacks  every active Release Pack; archived ones are excluded,
     *                      because the dashboard is about what is in flight
     * @param summary       counts, each phrased as a fact about what Tower was told
     */
    record Overview(List<EnvironmentSummary> environments,
                    List<ReleasePackSummary> releasePacks,
                    Summary summary) {}

    /**
     * One Environment: what is deployed there now, and what is heading for it.
     *
     * @param hasBeenObserved false when no Observation has ever been recorded
     *                        here — distinct from "observed and found empty",
     *                        and the two must never render the same (Scenario 4)
     * @param deployedCount   how many Applications are currently observed here
     * @param convergence     the Release Packs whose pinned Promotion Path names
     *                        this Environment, in a deliberately unranked order
     */
    record EnvironmentSummary(EnvironmentId environmentId, String name, Stage stage,
                              boolean hasBeenObserved, Instant lastObservedAt,
                              int deployedCount, EnvironmentConvergence convergence) {}

    /**
     * One active Release Pack, as far as the dashboard needs it.
     *
     * @param state           where it has been observed (ADR-008)
     * @param promotionPath   the pinned path's name, or null when none is assigned
     * @param environmentsReached how many Environments any of its contents has been seen in
     * @param furthestEnvironments every Environment it reached at the highest Stage
     *                             it was observed at, empty when it has been observed
     *                             nowhere. A list rather than one name because several
     *                             Environments commonly share a Stage (ADR-008), and
     *                             picking one of them would answer a question the data
     *                             does not answer
     */
    record ReleasePackSummary(ReleasePackId releasePackId, String name, ReleasePackState state,
                              String promotionPath, int contentCount,
                              int environmentsReached, List<String> furthestEnvironments,
                              Instant lastObservedAt) {}

    /**
     * @param contestedEnvironments Environments more than one active release is heading for —
     *                              the number the dashboard exists to surface
     * @param packsNotObservedAnywhere releases nothing has been reported about; a count of
     *                                 silence, not of failure
     * @param environmentsNeverObserved Environments Tower has been told nothing about
     */
    record Summary(int activeReleasePacks, int archivedReleasePacks,
                   int contestedEnvironments, int packsNotObservedAnywhere,
                   int environmentsNeverObserved) {}
}
