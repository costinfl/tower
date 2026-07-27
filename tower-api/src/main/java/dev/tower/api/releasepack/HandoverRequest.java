package dev.tower.api.releasepack;

/**
 * Request body for PUT /api/release-packs/{id}/handover (FR-006). Every field is free text and
 * optional; {@link dev.tower.domain.handover.Handover} normalises a null or blank value to an
 * empty string.
 */
public record HandoverRequest(
        String deploymentInstructions,
        String shellCommands,
        String databaseMigrations,
        String rollbackProcedure,
        String validationNotes,
        String operationalNotes) {
}
