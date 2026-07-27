package dev.tower.api.promotionpath;

import java.util.List;
import java.util.function.Function;

import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.promotionpath.PromotionPath;

/**
 * API-layer representation of a Promotion Path (issue #12). Never serialise {@link PromotionPath}
 * directly: {@code environments} on {@link #currentVersion()} and each entry of {@link #versions()}
 * carry the fully resolved Environment (id, name, stage), not bare ids.
 */
public record PathView(String id, String name, boolean archived, VersionView currentVersion,
                        List<VersionView> versions) {

    public static PathView from(PromotionPath path, Function<EnvironmentId, Environment> environments) {
        List<VersionView> versionViews = path.versions().stream()
                .map(version -> VersionView.from(version, environments))
                .toList();
        VersionView current = VersionView.from(path.currentVersion(), environments);
        return new PathView(path.id().toString(), path.name(), path.isArchived(), current, versionViews);
    }
}
