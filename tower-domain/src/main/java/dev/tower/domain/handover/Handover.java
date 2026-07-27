package dev.tower.domain.handover;

/**
 * Developer-owned information required before a Release Pack is transferred to
 * another team (Glossary.md, FR-006).
 *
 * <p>Tower assists in preparing Handover information and never executes it
 * (ADR-001). The shell commands and migrations recorded here are a description
 * of what someone else will run, not something Tower will run.
 *
 * <p>Every field is free text. The documentation deliberately does not
 * prescribe a structure, and imposing one would make Tower a deployment tool
 * rather than a documentation one.
 */
public record Handover(
        String deploymentInstructions,
        String shellCommands,
        String databaseMigrations,
        String rollbackProcedure,
        String validationNotes,
        String operationalNotes) {

    public Handover {
        deploymentInstructions = normalise(deploymentInstructions);
        shellCommands = normalise(shellCommands);
        databaseMigrations = normalise(databaseMigrations);
        rollbackProcedure = normalise(rollbackProcedure);
        validationNotes = normalise(validationNotes);
        operationalNotes = normalise(operationalNotes);
    }

    /** An empty Handover. Every Release Pack starts with one (FR-006). */
    public static Handover empty() {
        return new Handover("", "", "", "", "", "");
    }

    /** True when nothing has been prepared yet, which documentation generation reports rather than hides. */
    public boolean isEmpty() {
        return deploymentInstructions.isEmpty() && shellCommands.isEmpty() && databaseMigrations.isEmpty()
                && rollbackProcedure.isEmpty() && validationNotes.isEmpty() && operationalNotes.isEmpty();
    }

    private static String normalise(String value) {
        return value == null ? "" : value.strip();
    }
}
