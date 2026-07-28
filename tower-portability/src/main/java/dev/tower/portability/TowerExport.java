package dev.tower.portability;

import java.time.Instant;
import java.util.List;

/**
 * The portable form of Tower-owned information (ADR-010).
 *
 * <p>JSON with an explicit schema version, so a file can be reviewed, diffed
 * and stored before anyone imports it. A developer should be able to read this
 * and know exactly what will land in their instance.
 *
 * <p>The export is self-contained. ADR-010 names Release Packs, Promotion Paths
 * and their versions, Handover, Iterations and Intent, but a Release Pack
 * references Application Versions and a Promotion Path references Environments.
 * Shipping the packs without those would produce a file that cannot be
 * imported, so the referenced records travel with them.
 *
 * <p>It contains no credentials and no configuration. An export is meant to be
 * shared, and distributing secrets in a file intended for sharing is exactly
 * what NFR-026 forbids.
 */
public record TowerExport(
        int schemaVersion,
        String exportedFrom,
        Instant exportedAt,
        List<EnvironmentRecord> environments,
        List<ApplicationRecord> applications,
        List<ApplicationVersionRecord> applicationVersions,
        List<PromotionPathRecord> promotionPaths,
        List<ReleasePackRecord> releasePacks,
        List<ObservationRecord> observations) {

    /** Incremented only when the format changes in a way older readers cannot handle. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public TowerExport {
        environments = List.copyOf(environments == null ? List.of() : environments);
        applications = List.copyOf(applications == null ? List.of() : applications);
        applicationVersions = List.copyOf(applicationVersions == null ? List.of() : applicationVersions);
        promotionPaths = List.copyOf(promotionPaths == null ? List.of() : promotionPaths);
        releasePacks = List.copyOf(releasePacks == null ? List.of() : releasePacks);
        observations = List.copyOf(observations == null ? List.of() : observations);
    }

    public record EnvironmentRecord(String id, String name, String stage) {}

    public record ApplicationRecord(String id, String name, String description) {}

    public record ApplicationVersionRecord(
            String id, String applicationId, String version,
            String branch, String tag, String commit, String buildIdentifier) {}

    public record PromotionPathRecord(
            String id, String name, boolean archived, List<PromotionPathVersionRecord> versions) {}

    public record PromotionPathVersionRecord(int number, Instant createdAt, List<String> environmentIds) {}

    public record ReleasePackRecord(
            String id, String name, String description, boolean archived,
            String promotionPathId, Integer promotionPathVersion,
            List<PackedVersionRecord> contents,
            HandoverRecord handover,
            List<IterationRecord> iterations) {}

    public record PackedVersionRecord(String applicationId, String applicationVersionId) {}

    public record HandoverRecord(
            String deploymentInstructions, String shellCommands, String databaseMigrations,
            String rollbackProcedure, String validationNotes, String operationalNotes) {}

    public record IterationRecord(
            String id, String name, Instant startedAt, Instant completedAt, String notes) {}

    /**
     * An Observation, carrying the provenance it was recorded with.
     *
     * <p>{@code originInstance} is the heart of ADR-010. On export it is filled
     * in with the exporting instance's name for any Observation that instance
     * recorded itself, and left as-is for one that had already travelled. On
     * import nothing is re-stamped. The result is that a fact always names
     * whoever actually saw it, however many instances it has passed through,
     * and no instance can end up appearing to have observed something it never
     * did.
     */
    public record ObservationRecord(
            String id, String environmentId, String applicationId, String applicationVersionId,
            Instant observedAt, String sourceCollector, String sourceActor, String originInstance) {}
}
