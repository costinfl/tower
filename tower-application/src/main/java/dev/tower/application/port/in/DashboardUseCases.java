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
     * @param setup         where this Tower stands against the order things have
     *                      to be defined in — empty of interest once it is complete
     */
    record Overview(List<EnvironmentSummary> environments,
                    List<ReleasePackSummary> releasePacks,
                    Summary summary,
                    SetupState setup) {}

    /**
     * Where this Tower stands against the order things have to be defined in.
     *
     * <p>Exists because the dashboard had nothing to say to a Tower that holds
     * nothing. It answered five zeros and "No Environments have been defined
     * yet" — honest, and a dead end. Counting silence is right (Scenario 4);
     * leaving somebody with no idea what to do about it is not.
     *
     * <p>Derived on request from what exists, like everything else here, and
     * derived on the server rather than in the Viewer so that the sequence a
     * screen teaches and the model it teaches about cannot drift apart.
     *
     * <p>Says nothing about whether Tower is being used <em>well</em>. It is a
     * list of what has been defined, not advice — the same restraint that keeps
     * this page from ranking releases keeps it from grading a configuration.
     *
     * @param complete every <em>required</em> step is done. An optional step left
     *                 undone does not hold this back, which is the difference
     *                 between "you can use Tower now" and "you have used every
     *                 feature"
     */
    record SetupState(List<SetupStep> steps, boolean complete) {

        public SetupState {
            steps = List.copyOf(steps);
        }
    }

    /**
     * One step, and whether this Tower has taken it.
     *
     * @param id       stable name for the step, so a screen can decide where the
     *                 step leads without matching on its wording
     * @param optional true where Tower works without it. Only Connectors are:
     *                 ADR-006 admits a person stating what is deployed as a real
     *                 Observation, so a Tower with no Connector at all is a
     *                 supported way to run rather than an unfinished one, and a
     *                 checklist implying otherwise would misdescribe the product
     */
    record SetupStep(String id, String title, String detail, boolean done, boolean optional) {}

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
