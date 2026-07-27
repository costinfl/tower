package dev.tower.api.releasepack;

import java.time.Instant;

import dev.tower.domain.iteration.Iteration;

/** API-layer representation of a validation Iteration (gap G4). */
public record IterationView(String id, String name, Instant startedAt, Instant completedAt, String notes) {

    public static IterationView from(Iteration iteration) {
        return new IterationView(
                iteration.id().toString(), iteration.name(), iteration.startedAt(), iteration.completedAt(),
                iteration.notes());
    }
}
