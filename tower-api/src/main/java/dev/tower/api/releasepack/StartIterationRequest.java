package dev.tower.api.releasepack;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import dev.tower.domain.iteration.Iteration;

/**
 * Request body for POST /api/release-packs/{id}/iterations (gap G4). {@code startedAt} is
 * optional: {@link dev.tower.application.service.ReleasePackService#startIteration} falls back to
 * the current time when it is not supplied.
 */
public record StartIterationRequest(
        @NotBlank(message = "name is required") @Size(max = Iteration.NAME_MAX_LENGTH,
                message = "name must be at most " + Iteration.NAME_MAX_LENGTH + " characters") String name,
        Instant startedAt,
        String notes) {
}
