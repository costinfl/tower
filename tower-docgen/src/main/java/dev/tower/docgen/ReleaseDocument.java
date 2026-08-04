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
        List<ArtifactEntry> artifacts,
        List<WorkItemEntry> workItems,
        HandoverSection handover,
        List<IterationEntry> iterations,
        List<SightingEntry> sightings) {

    /**
     * What the Artifacts section says when nobody has accepted a digest.
     *
     * <p>Held once rather than written into each renderer, because three
     * renderers and the demo's fourth must agree on it word for word — and this
     * particular sentence is one a reader is meant to take at face value: it
     * says nothing was accepted, not that nothing was built.
     */
    public static final String NO_ARTIFACTS_ACCEPTED =
            "No artifact digests have been accepted for this release.";

    public ReleaseDocument {
        contents = List.copyOf(contents);
        artifacts = List.copyOf(artifacts);
        workItems = List.copyOf(workItems);
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

    /**
     * One artifact digest somebody accepted for a version in this release
     * (ADR-021, FR-086).
     *
     * <p>The digest is the one a person accepted, not what the repository holds
     * now. Nothing here is read at generation time, which is what keeps NFR-025
     * true: a tag pushed over does not change a document already handed over.
     * The difference is shown in the Viewer, where somebody can decide whether to
     * accept the newer bytes.
     *
     * <p>Only accepted digests appear. An artifact the repository holds that
     * nobody has accepted is not in the document, for exactly the reason a
     * tracker's current wording is not: Tower would be printing something it read
     * rather than something a person stood behind.
     *
     * @param kind       the team's own word for what this is, never interpreted
     * @param coordinate where it was found, so a digest is followable
     */
    public record ArtifactEntry(String applicationName, String version, String kind,
                                String coordinate, String digest) {
    }

    /**
     * One work item the release claims to deliver (ADR-018).
     *
     * <p>The title is the one a person accepted from the tracker, not what the
     * tracker says now. Nothing here is read at generation time, which is what
     * keeps NFR-025 true: rewriting a ticket summary does not change a document
     * already handed over.
     *
     * <p>No status. That is the tracker's, read on request in the Viewer and
     * stored nowhere — a status printed into a document would be stale before
     * the document was read.
     */
    public record WorkItemEntry(String identifier, String title) {
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
