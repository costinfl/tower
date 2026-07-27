package dev.tower.api.releasepack;

import java.util.List;
import java.util.function.Function;

import dev.tower.api.promotionpath.EnvironmentRefResponse;
import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.promotionpath.PromotionPath;
import dev.tower.domain.releasepack.PromotionPathAssignment;
import dev.tower.domain.shared.DomainException;

/**
 * The Promotion Path version a Release Pack follows (FR-005), fully resolved: the path's name and
 * the exact Environments the pinned version carries (ADR-007), not a bare id/version pair. The UI
 * must not need extra round trips to render the pack's Lane.
 */
public record PromotionPathAssignmentView(
        String pathId, String pathName, int versionNumber, List<EnvironmentRefResponse> environments) {

    public static PromotionPathAssignmentView from(
            PromotionPathAssignment assignment, PromotionPath path, Function<EnvironmentId, Environment> environments) {
        var version = path.version(assignment.versionNumber())
                .orElseThrow(() -> new DomainException(
                        "Promotion Path '" + path.name() + "' has no version " + assignment.versionNumber() + "."));
        List<EnvironmentRefResponse> resolved = version.environments().stream()
                .map(environments)
                .map(EnvironmentRefResponse::from)
                .toList();
        return new PromotionPathAssignmentView(
                path.id().toString(), path.name(), assignment.versionNumber(), resolved);
    }
}
