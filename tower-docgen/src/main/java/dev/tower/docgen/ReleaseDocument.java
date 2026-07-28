package dev.tower.docgen;

import dev.tower.domain.releasepack.ReleasePackState;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A release document assembled from the Canonical Model (FR-024).
 *
 * <p>This is a rendering-independent structure. Markdown is one output format;
 * OQ-009 leaves the others open, and keeping the assembled document separate
 * from the renderer is what lets a second format be added without re-deriving
 * anything.
 *
 * <p>Generated Information is disposable (IA-03): nothing here is stored, and
 * the document can always be reproduced from the model. It is therefore never
 * the source of truth (BR-07) — it is a view of one.
 *
 * <p>Nothing in this structure is invented. Every field either came from the
 * model or is explicitly marked absent, because a release document that
 * silently fills a gap is worse than one that admits it.
 */
public record ReleaseDocument(
        String packName,
        String description,
        ReleasePackState state,
        boolean archived,
        Optional<PromotionPathSection> promotionPath,
        List<ContentEntry> contents,
        HandoverSection handover,
        List<IterationEntry> iterations,
        List<SightingEntry> sightings) {

    public ReleaseDocument {
        contents = List.copyOf(contents);
        iterations = List.copyOf(iterations);
        sightings = List.copyOf(sightings);
    }

    /**
     * The Promotion Path the pack follows, at the version it was pinned to.
     *
     * <p>The version number is part of the document because a pack may follow
     * version 1 while the path has since moved on (ADR-007). Printing the path
     * name alone would describe a topology the release never followed.
     */
    public record PromotionPathSection(String name, int versionNumber, List<String> environments) {
        public PromotionPathSection {
            environments = List.copyOf(environments);
        }
    }

    /** One Application Version included in the release. */
    public record ContentEntry(
            String applicationName,
            String version,
            String branch,
            String tag,
            String commit,
            String buildIdentifier) {
    }

    /** Developer-owned deployment information (FR-028, FR-029). */
    public record HandoverSection(
            String deploymentInstructions,
            String shellCommands,
            String databaseMigrations,
            String rollbackProcedure,
            String validationNotes,
            String operationalNotes,
            boolean prepared) {
    }

    /** One validation cycle recorded against the release (FR-030). */
    public record IterationEntry(String name, Instant startedAt, Instant completedAt, String notes) {
        public boolean isComplete() {
            return completedAt != null;
        }
    }

    /**
     * One place a version in this release was observed.
     *
     * <p>Carries the originating Observation so every claim in the document
     * remains traceable to the fact behind it (FR-031, FR-032, NFR-011).
     */
    public record SightingEntry(
            String environmentName,
            String applicationName,
            String version,
            Instant observedAt,
            String sourceCollector,
            String sourceActor,
            String observationId) {
    }
}
