package dev.tower.api.dashboard;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.tower.application.port.in.DashboardUseCases;
import dev.tower.application.port.in.DashboardUseCases.EnvironmentSummary;
import dev.tower.application.port.in.DashboardUseCases.Overview;
import dev.tower.application.port.in.DashboardUseCases.ReleasePackSummary;
import dev.tower.domain.convergence.EnvironmentConvergence;

/**
 * REST API for the operational dashboard (Milestone 5, issue #7).
 *
 * <p>One GET and nothing else. There is no control on this resource that
 * promotes, deploys or triggers anything, and there deliberately never will be:
 * the dashboard is where such a control would feel most natural and would do the
 * most damage to what Tower is (ADR-001, Guardrails.md).
 */
@RestController
public class DashboardController {

    private final DashboardUseCases dashboard;

    public DashboardController(DashboardUseCases dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/api/dashboard")
    public DashboardView overview() {
        Overview overview = dashboard.overview();
        return new DashboardView(
                overview.environments().stream().map(EnvironmentView::from).toList(),
                overview.releasePacks().stream().map(ReleasePackView::from).toList(),
                SummaryView.from(overview.summary()),
                SetupView.from(overview.setup()));
    }

    /**
     * Where this Tower stands against the order things have to be defined in.
     *
     * <p>Carried on the dashboard rather than on an endpoint of its own: it is
     * the same question the rest of this page answers — where do things stand —
     * asked of the configuration instead of the releases, and a screen that had
     * to make two calls to draw one page would go out of step with itself.
     */
    public record SetupView(List<SetupStepView> steps, boolean complete) {

        static SetupView from(DashboardUseCases.SetupState setup) {
            return new SetupView(
                    setup.steps().stream().map(SetupStepView::from).toList(),
                    setup.complete());
        }
    }

    /**
     * @param optional true where Tower works without it. Sent rather than
     *                 inferred from the step's wording, so a screen can render
     *                 the difference instead of parsing prose
     */
    public record SetupStepView(String id, String title, String detail, boolean done,
                                boolean optional) {

        static SetupStepView from(DashboardUseCases.SetupStep step) {
            return new SetupStepView(step.id(), step.title(), step.detail(), step.done(),
                    step.optional());
        }
    }

    public record DashboardView(List<EnvironmentView> environments,
                                List<ReleasePackView> releasePacks,
                                SummaryView summary,
                                SetupView setup) {}

    /**
     * @param contested   more than one active release is heading here — stated by
     *                    the server so every client marks the same thing
     * @param converging  ordered by name, an order that is not a ranking
     */
    public record EnvironmentView(String environmentId, String name, String stage,
                                  boolean hasBeenObserved, Instant lastObservedAt,
                                  int deployedCount, boolean contested,
                                  List<ConvergingPackView> converging) {

        static EnvironmentView from(EnvironmentSummary summary) {
            return new EnvironmentView(
                    summary.environmentId().toString(), summary.name(), summary.stage().name(),
                    summary.hasBeenObserved(), summary.lastObservedAt(), summary.deployedCount(),
                    summary.convergence().isContested(),
                    summary.convergence().packs().stream().map(ConvergingPackView::from).toList());
        }
    }

    /**
     * @param standing NOT_OBSERVED_HERE, PARTLY_OBSERVED or FULLY_OBSERVED —
     *                 named for what Tower was told, never for what is deployed
     */
    public record ConvergingPackView(String releasePackId, String name, String standing,
                                     Instant firstObservedAt, Instant completeAt,
                                     int observedCount, int packedCount) {

        static ConvergingPackView from(EnvironmentConvergence.ConvergingPack pack) {
            return new ConvergingPackView(
                    pack.releasePackId().toString(), pack.name(), pack.standing().name(),
                    pack.firstObservedAt(), pack.completeAt(),
                    pack.observedCount(), pack.packedCount());
        }
    }

    /**
     * @param furthestEnvironments every Environment at the highest Stage this release
     *                             was observed at — a list, because several Environments
     *                             commonly share a Stage and naming one of them would
     *                             answer a question the data does not answer
     */
    public record ReleasePackView(String releasePackId, String name, String state,
                                  String promotionPath, int contentCount,
                                  int environmentsReached, List<String> furthestEnvironments,
                                  Instant lastObservedAt) {

        static ReleasePackView from(ReleasePackSummary summary) {
            return new ReleasePackView(
                    summary.releasePackId().toString(), summary.name(), summary.state().name(),
                    summary.promotionPath(), summary.contentCount(),
                    summary.environmentsReached(), summary.furthestEnvironments(),
                    summary.lastObservedAt());
        }
    }

    public record SummaryView(int activeReleasePacks, int archivedReleasePacks,
                              int contestedEnvironments, int packsNotObservedAnywhere,
                              int environmentsNeverObserved) {

        static SummaryView from(DashboardUseCases.Summary summary) {
            return new SummaryView(
                    summary.activeReleasePacks(), summary.archivedReleasePacks(),
                    summary.contestedEnvironments(), summary.packsNotObservedAnywhere(),
                    summary.environmentsNeverObserved());
        }
    }
}
