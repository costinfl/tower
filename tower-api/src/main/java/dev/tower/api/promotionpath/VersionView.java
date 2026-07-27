package dev.tower.api.promotionpath;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.promotionpath.PromotionPathVersion;

/** One version of a Promotion Path, with its Environment references fully resolved. */
public record VersionView(int number, Instant createdAt, List<EnvironmentRefResponse> environments) {

    public static VersionView from(PromotionPathVersion version, Function<EnvironmentId, Environment> environments) {
        List<EnvironmentRefResponse> resolved = version.environments().stream()
                .map(environments)
                .map(EnvironmentRefResponse::from)
                .toList();
        return new VersionView(version.number(), version.createdAt(), resolved);
    }
}
