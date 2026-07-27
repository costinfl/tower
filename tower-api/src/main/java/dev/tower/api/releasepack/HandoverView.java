package dev.tower.api.releasepack;

import dev.tower.domain.handover.Handover;

/** API-layer representation of a Release Pack's Handover information (FR-006). */
public record HandoverView(
        String deploymentInstructions,
        String shellCommands,
        String databaseMigrations,
        String rollbackProcedure,
        String validationNotes,
        String operationalNotes) {

    public static HandoverView from(Handover handover) {
        return new HandoverView(
                handover.deploymentInstructions(),
                handover.shellCommands(),
                handover.databaseMigrations(),
                handover.rollbackProcedure(),
                handover.validationNotes(),
                handover.operationalNotes());
    }
}
