package dev.tower.portability;

import dev.tower.application.port.out.ApplicationRepository;
import dev.tower.application.port.out.ApplicationVersionRepository;
import dev.tower.application.port.out.EnvironmentRepository;
import dev.tower.application.port.out.ObservationRepository;
import dev.tower.application.port.out.PromotionPathRepository;
import dev.tower.application.port.out.ReleasePackRepository;
import dev.tower.domain.application.Application;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.environment.Stage;
import dev.tower.domain.handover.Handover;
import dev.tower.domain.iteration.Iteration;
import dev.tower.domain.iteration.IterationId;
import dev.tower.domain.observation.Observation;
import dev.tower.domain.observation.ObservationId;
import dev.tower.domain.observation.ObservationSource;
import dev.tower.domain.promotionpath.PromotionPath;
import dev.tower.domain.promotionpath.PromotionPathId;
import dev.tower.domain.promotionpath.PromotionPathVersion;
import dev.tower.domain.releasepack.PackedVersion;
import dev.tower.domain.releasepack.PromotionPathAssignment;
import dev.tower.domain.releasepack.ReleasePack;
import dev.tower.domain.releasepack.WorkItemReference;
import dev.tower.domain.releasepack.ReleasePackId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Imports Tower-owned information from an export file (issues #38, #39).
 *
 * <p>Import is additive with explicit conflict resolution, never an automatic
 * merge (ADR-010). It writes through the outbound repository ports rather than
 * the use cases, because identity must be preserved: a use case would mint new
 * ids and the transferred records would no longer be the same records.
 *
 * <p>Observations are the exception to conflict handling entirely. They are
 * immutable and append-only, so an incoming Observation either does not exist
 * here yet, in which case it is transferred verbatim, or it does, in which case
 * it is already the same fact and nothing needs doing. That is what ADR-010
 * means by saying Observations cannot conflict.
 */
public class ImportService {

    private final EnvironmentRepository environments;
    private final ApplicationRepository applications;
    private final ApplicationVersionRepository versions;
    private final PromotionPathRepository promotionPaths;
    private final ReleasePackRepository releasePacks;
    private final ObservationRepository observations;

    public ImportService(EnvironmentRepository environments,
                         ApplicationRepository applications,
                         ApplicationVersionRepository versions,
                         PromotionPathRepository promotionPaths,
                         ReleasePackRepository releasePacks,
                         ObservationRepository observations) {
        this.environments = Objects.requireNonNull(environments);
        this.applications = Objects.requireNonNull(applications);
        this.versions = Objects.requireNonNull(versions);
        this.promotionPaths = Objects.requireNonNull(promotionPaths);
        this.releasePacks = Objects.requireNonNull(releasePacks);
        this.observations = Objects.requireNonNull(observations);
    }

    /** Analyses the file and reports what would happen, changing nothing. */
    public ImportReport preview(TowerExport export, ConflictStrategy strategy) {
        return run(export, strategy, false);
    }

    /** Applies the file. */
    public ImportReport apply(TowerExport export, ConflictStrategy strategy) {
        return run(export, strategy, true);
    }

    private ImportReport run(TowerExport export, ConflictStrategy strategy, boolean apply) {
        Objects.requireNonNull(export, "An import file is required.");
        Objects.requireNonNull(strategy, "A conflict strategy is required.");

        if (export.schemaVersion() > TowerExport.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("This file uses export schema version "
                    + export.schemaVersion() + ", which this version of Tower cannot read."
                    + " It understands up to version " + TowerExport.CURRENT_SCHEMA_VERSION + ".");
        }

        ImportReport.Builder report = new ImportReport.Builder();

        // Order matters: referenced records must exist before the records that
        // point at them, or a Release Pack would arrive describing versions
        // nothing has heard of.
        importEnvironments(export, strategy, apply, report);
        importApplications(export, strategy, apply, report);
        importVersions(export, strategy, apply, report);
        importPromotionPaths(export, strategy, apply, report);
        importReleasePacks(export, strategy, apply, report);
        importObservations(export, apply, report);

        return report.build(apply, export.exportedFrom(), export.schemaVersion());
    }

    private void importEnvironments(TowerExport export, ConflictStrategy strategy, boolean apply,
                                    ImportReport.Builder report) {
        for (TowerExport.EnvironmentRecord record : export.environments()) {
            EnvironmentId id = EnvironmentId.of(record.id());
            boolean exists = environments.findById(id).isPresent();
            Environment incoming = new Environment(id, record.name(), Stage.valueOf(record.stage()));

            if (!exists) {
                if (apply) {
                    environments.save(incoming);
                }
                report.add("Environment", record.id(), record.name(), ImportReport.Outcome.CREATED, null);
                continue;
            }
            switch (strategy) {
                case SKIP -> report.add("Environment", record.id(), record.name(),
                        ImportReport.Outcome.SKIPPED, "Already present; kept the local record.");
                case REPLACE -> {
                    if (apply) {
                        environments.save(incoming);
                    }
                    report.add("Environment", record.id(), record.name(),
                            ImportReport.Outcome.REPLACED, "Local record overwritten.");
                }
                case DUPLICATE -> {
                    Environment copy = Environment.create(
                            disambiguate(record.name(), export.exportedFrom()), incoming.stage());
                    if (apply) {
                        environments.save(copy);
                    }
                    report.add("Environment", record.id(), copy.name(),
                            ImportReport.Outcome.DUPLICATED, "Imported alongside the local record.");
                }
            }
        }
    }

    private void importApplications(TowerExport export, ConflictStrategy strategy, boolean apply,
                                    ImportReport.Builder report) {
        for (TowerExport.ApplicationRecord record : export.applications()) {
            ApplicationId id = ApplicationId.of(record.id());
            boolean exists = applications.findById(id).isPresent();
            Application incoming = new Application(id, record.name(), record.description());

            if (!exists) {
                if (apply) {
                    applications.save(incoming);
                }
                report.add("Application", record.id(), record.name(), ImportReport.Outcome.CREATED, null);
            } else if (strategy == ConflictStrategy.REPLACE) {
                if (apply) {
                    applications.save(incoming);
                }
                report.add("Application", record.id(), record.name(),
                        ImportReport.Outcome.REPLACED, "Local record overwritten.");
            } else {
                report.add("Application", record.id(), record.name(),
                        ImportReport.Outcome.SKIPPED, "Already present; kept the local record.");
            }
        }
    }

    private void importVersions(TowerExport export, ConflictStrategy strategy, boolean apply,
                                ImportReport.Builder report) {
        for (TowerExport.ApplicationVersionRecord record : export.applicationVersions()) {
            ApplicationVersionId id = ApplicationVersionId.of(record.id());
            boolean exists = versions.findById(id).isPresent();

            // BR-01 makes an Application Version immutable, so the same id is by
            // definition the same version. Replacing it could only ever write
            // back what is already there, and would misreport as a change.
            if (exists) {
                report.add("Application Version", record.id(), record.version(),
                        ImportReport.Outcome.UNCHANGED, "Already present; versions are immutable (BR-01).");
                continue;
            }
            if (apply) {
                versions.save(new ApplicationVersion(
                        id, ApplicationId.of(record.applicationId()), record.version(),
                        record.branch(), record.tag(), record.commit(), record.buildIdentifier()));
            }
            report.add("Application Version", record.id(), record.version(),
                    ImportReport.Outcome.CREATED, null);
        }
    }

    private void importPromotionPaths(TowerExport export, ConflictStrategy strategy, boolean apply,
                                      ImportReport.Builder report) {
        for (TowerExport.PromotionPathRecord record : export.promotionPaths()) {
            PromotionPathId id = PromotionPathId.of(record.id());
            boolean exists = promotionPaths.findById(id).isPresent();

            List<PromotionPathVersion> versionList = record.versions().stream()
                    .map(v -> new PromotionPathVersion(
                            v.number(),
                            v.environmentIds().stream().map(EnvironmentId::of).toList(),
                            v.createdAt()))
                    .toList();

            if (!exists) {
                if (apply) {
                    promotionPaths.save(PromotionPath.reconstitute(
                            id, record.name(), versionList, record.archived()));
                }
                report.add("Promotion Path", record.id(), record.name(), ImportReport.Outcome.CREATED, null);
                continue;
            }
            switch (strategy) {
                case SKIP -> report.add("Promotion Path", record.id(), record.name(),
                        ImportReport.Outcome.SKIPPED, "Already present; kept the local record.");
                case REPLACE -> {
                    if (apply) {
                        promotionPaths.save(PromotionPath.reconstitute(
                                id, record.name(), versionList, record.archived()));
                    }
                    report.add("Promotion Path", record.id(), record.name(),
                            ImportReport.Outcome.REPLACED, "Local record overwritten.");
                }
                case DUPLICATE -> {
                    PromotionPath copy = PromotionPath.reconstitute(
                            PromotionPathId.newId(),
                            disambiguate(record.name(), export.exportedFrom()),
                            versionList, record.archived());
                    if (apply) {
                        promotionPaths.save(copy);
                    }
                    report.add("Promotion Path", record.id(), copy.name(),
                            ImportReport.Outcome.DUPLICATED, "Imported alongside the local record.");
                }
            }
        }
    }

    private void importReleasePacks(TowerExport export, ConflictStrategy strategy, boolean apply,
                                    ImportReport.Builder report) {
        for (TowerExport.ReleasePackRecord record : export.releasePacks()) {
            ReleasePackId id = ReleasePackId.of(record.id());
            boolean exists = releasePacks.findById(id).isPresent();

            if (!exists) {
                if (apply) {
                    releasePacks.save(rebuild(id, record.name(), record));
                }
                report.add("Release Pack", record.id(), record.name(), ImportReport.Outcome.CREATED, null);
                continue;
            }
            switch (strategy) {
                case SKIP -> report.add("Release Pack", record.id(), record.name(),
                        ImportReport.Outcome.SKIPPED, "Already present; kept the local record.");
                case REPLACE -> {
                    if (apply) {
                        releasePacks.save(rebuild(id, record.name(), record));
                    }
                    report.add("Release Pack", record.id(), record.name(),
                            ImportReport.Outcome.REPLACED, "Local record overwritten.");
                }
                case DUPLICATE -> {
                    String name = disambiguate(record.name(), export.exportedFrom());
                    if (apply) {
                        releasePacks.save(rebuild(ReleasePackId.newId(), name, record));
                    }
                    report.add("Release Pack", record.id(), name,
                            ImportReport.Outcome.DUPLICATED, "Imported alongside the local record.");
                }
            }
        }
    }

    private ReleasePack rebuild(ReleasePackId id, String name, TowerExport.ReleasePackRecord record) {
        PromotionPathAssignment assignment = record.promotionPathId() == null
                || record.promotionPathVersion() == null
                ? null
                : new PromotionPathAssignment(
                        PromotionPathId.of(record.promotionPathId()), record.promotionPathVersion());

        List<PackedVersion> contents = record.contents().stream()
                .map(c -> new PackedVersion(
                        ApplicationId.of(c.applicationId()),
                        ApplicationVersionId.of(c.applicationVersionId())))
                .toList();

        TowerExport.HandoverRecord h = record.handover();
        Handover handover = h == null ? Handover.empty() : new Handover(
                h.deploymentInstructions(), h.shellCommands(), h.databaseMigrations(),
                h.rollbackProcedure(), h.validationNotes(), h.operationalNotes());

        List<Iteration> iterations = record.iterations().stream()
                .map(i -> new Iteration(
                        IterationId.of(i.id()), i.name(), i.startedAt(), i.completedAt(), i.notes()))
                .toList();

        // Absent in schema version 1 documents, which read as no work items
        // rather than as an error (ADR-018).
        List<WorkItemReference> workItems = record.workItems() == null ? List.of()
                : record.workItems().stream()
                        .map(w -> new WorkItemReference(w.identifier(), w.title()))
                        .toList();

        return ReleasePack.reconstitute(
                id, name, record.description(), assignment, contents, workItems,
                handover, iterations, record.archived());
    }

    /**
     * Transfers Observations, preserving provenance exactly (issue #39).
     *
     * <p>This never mints a new Observation. Every field, including the origin
     * instance and the original timestamp, is carried across untouched, so the
     * receiving instance reports the fact as something another instance saw
     * rather than something it saw itself. Recreating Observations locally would
     * violate ADR-002, ADR-006, FR-022 and NFR-012 all at once.
     *
     * <p>An Observation already present is left alone: same id means the same
     * immutable fact, so importing it twice has no effect.
     */
    private void importObservations(TowerExport export, boolean apply, ImportReport.Builder report) {
        for (TowerExport.ObservationRecord record : export.observations()) {
            ObservationId id = ObservationId.of(record.id());

            if (observations.findById(id).isPresent()) {
                report.add("Observation", record.id(), record.observedAt().toString(),
                        ImportReport.Outcome.UNCHANGED,
                        "Already present; Observations are immutable and deduplicated by identity.");
                continue;
            }

            if (apply) {
                observations.append(new Observation(
                        id,
                        EnvironmentId.of(record.environmentId()),
                        ApplicationId.of(record.applicationId()),
                        ApplicationVersionId.of(record.applicationVersionId()),
                        record.observedAt(),
                        new ObservationSource(
                                record.sourceCollector(), record.sourceActor(), record.originInstance())));
            }
            report.add("Observation", record.id(), record.observedAt().toString(),
                    ImportReport.Outcome.CREATED,
                    "Transferred from " + describeOrigin(record, export.exportedFrom()) + ".");
        }
    }

    private String describeOrigin(TowerExport.ObservationRecord record, String exportedFrom) {
        return record.originInstance() == null ? exportedFrom : record.originInstance();
    }

    /** Keeps a duplicated record distinguishable without inventing meaning. */
    private String disambiguate(String name, String sourceInstance) {
        String suffix = sourceInstance == null || sourceInstance.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : sourceInstance;
        return name + " (imported from " + suffix + ")";
    }
}
