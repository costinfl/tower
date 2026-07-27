package dev.tower.api.releasepack;

import java.time.Instant;

/**
 * Request body for POST /api/release-packs/{id}/iterations/{iterationId}/complete. {@code
 * completedAt} is optional: the service falls back to the current time when it is not supplied.
 */
public record CompleteIterationRequest(Instant completedAt) {
}
