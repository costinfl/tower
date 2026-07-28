package dev.tower.api.observation;

import java.time.Instant;
import java.util.List;

import dev.tower.domain.environment.Environment;
import dev.tower.domain.observation.EnvironmentState;

/**
 * API-layer representation of an Environment's derived state (FR-010 to FR-013). An Environment
 * nobody has observed is reported as exactly that ({@code hasBeenObserved = false}, empty
 * {@code deployed}) rather than left to look identical to one that simply has nothing running.
 */
public record EnvironmentStateView(
        EnvironmentRefView environment, boolean hasBeenObserved, Instant lastObservedAt,
        List<DeployedApplicationView> deployed) {

    public static EnvironmentStateView from(Environment environment, EnvironmentState state,
                                            List<DeployedApplicationView> deployed) {
        return new EnvironmentStateView(
                EnvironmentRefView.from(environment),
                state.hasBeenObserved(),
                state.lastObservedAt().orElse(null),
                deployed);
    }
}
