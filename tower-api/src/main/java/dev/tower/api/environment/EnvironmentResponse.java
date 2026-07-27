package dev.tower.api.environment;

import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.Stage;

/** API-layer representation of an Environment. Never serialise {@link Environment} directly. */
public record EnvironmentResponse(String id, String name, Stage stage) {

    public static EnvironmentResponse from(Environment environment) {
        return new EnvironmentResponse(environment.id().toString(), environment.name(), environment.stage());
    }
}
