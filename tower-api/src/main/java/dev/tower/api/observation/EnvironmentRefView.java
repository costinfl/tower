package dev.tower.api.observation;

import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.Stage;

/**
 * Resolved reference to an Environment, embedded in Observation-derived views so the Viewer never
 * needs a follow-up round trip to show its name and Stage (FR-012, FR-013, NFR-013).
 */
public record EnvironmentRefView(String id, String name, Stage stage) {

    public static EnvironmentRefView from(Environment environment) {
        return new EnvironmentRefView(environment.id().toString(), environment.name(), environment.stage());
    }
}
