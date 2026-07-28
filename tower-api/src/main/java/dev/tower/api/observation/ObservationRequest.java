package dev.tower.api.observation;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/observations. {@code observedAt} is optional: {@link
 * dev.tower.application.service.ObservationService#recordManual} falls back to the current time
 * when it is not supplied, and rejects a future timestamp with a 409 (an Observation records what
 * has been seen, not what is planned).
 *
 * <p>There is no {@code source} field. Provenance for a manually entered Observation comes from
 * the Manual Collector (ADR-006), never from the caller, so a client cannot claim a fact arrived
 * from somewhere it did not.
 */
public record ObservationRequest(
        @NotBlank(message = "environmentId is required") String environmentId,
        @NotBlank(message = "applicationVersionId is required") String applicationVersionId,
        Instant observedAt) {
}
