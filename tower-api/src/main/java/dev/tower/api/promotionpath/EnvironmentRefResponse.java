package dev.tower.api.promotionpath;

import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.Stage;

/**
 * A fully-resolved Environment reference inside a PathView: id, name and stage, not a bare id.
 * The Lane visualisation needs names and stages without a second round trip.
 */
public record EnvironmentRefResponse(String id, String name, Stage stage) {

    public static EnvironmentRefResponse from(Environment environment) {
        return new EnvironmentRefResponse(environment.id().toString(), environment.name(), environment.stage());
    }
}
