package dev.tower.api.releasepack;

import java.util.List;
import java.util.function.Function;

import dev.tower.domain.application.Application;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.promotionpath.PromotionPath;
import dev.tower.domain.promotionpath.PromotionPathId;
import dev.tower.domain.releasepack.ReleasePack;

/**
 * API-layer representation of a Release Pack (issue #20). Self-sufficient for the UI: contents
 * carry the resolved Application name and version detail, and {@code promotionPath} carries the
 * resolved path name and Environments, so the Viewer never needs a follow-up round trip to render
 * a pack. Never serialise {@link ReleasePack} directly.
 */
public record ReleasePackView(
        String id, String name, String description, boolean archived,
        PromotionPathAssignmentView promotionPath, List<PackContentView> contents,
        List<WorkItemView> workItems, HandoverView handover,
        List<IterationView> iterations) {

    /**
     * A work item the release claims to deliver (ADR-018).
     *
     * <p>The title is what a person accepted, not what the tracker says now.
     * Whether the two still agree is a separate question, answered by the
     * resolution endpoint and never by this view — a Release Pack read must not
     * depend on an external system being reachable.
     */
    public record WorkItemView(String identifier, String title, boolean titleAccepted) {
    }

    public static ReleasePackView from(
            ReleasePack pack,
            Function<ApplicationId, Application> applications,
            Function<ApplicationVersionId, ApplicationVersion> versions,
            Function<PromotionPathId, PromotionPath> promotionPaths,
            Function<EnvironmentId, Environment> environments) {

        PromotionPathAssignmentView promotionPathView = pack.promotionPath()
                .map(assignment -> PromotionPathAssignmentView.from(
                        assignment, promotionPaths.apply(assignment.pathId()), environments))
                .orElse(null);

        List<PackContentView> contentViews = pack.contents().stream()
                .map(entry -> PackContentView.from(
                        applications.apply(entry.applicationId()), versions.apply(entry.versionId())))
                .toList();

        List<IterationView> iterationViews = pack.iterations().stream().map(IterationView::from).toList();

        List<WorkItemView> workItemViews = pack.workItems().stream()
                .map(reference -> new WorkItemView(
                        reference.identifier(), reference.title(), reference.hasTitle()))
                .toList();

        return new ReleasePackView(pack.id().toString(), pack.name(), pack.description(), pack.isArchived(),
                promotionPathView, contentViews, workItemViews,
                HandoverView.from(pack.handover()), iterationViews);
    }
}
