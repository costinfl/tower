package dev.tower.api.releasepack;

/** Request body for PUT /api/release-packs/{id}/iterations/{iterationId}/notes. */
public record IterationNotesRequest(String notes) {
}
