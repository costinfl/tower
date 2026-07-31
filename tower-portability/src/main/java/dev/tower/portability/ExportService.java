package dev.tower.portability;

import dev.tower.application.port.out.ApplicationRepository;
import dev.tower.application.port.out.ApplicationVersionRepository;
import dev.tower.application.port.out.EnvironmentRepository;
import dev.tower.application.port.out.ObservationRepository;
import dev.tower.application.port.out.PromotionPathRepository;
import dev.tower.application.port.out.ReleasePackRepository;
import dev.tower.domain.observation.Observation;
import dev.tower.domain.observation.ObservationSource;
import dev.tower.domain.promotionpath.PromotionPath;
import dev.tower.domain.releasepack.ReleasePack;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Exports Tower-owned information as a portable file (issue #37, ADR-010).
 *
 * <p>Ordering is by identity throughout, so exporting an unchanged instance
 * twice produces the same file. That makes an export diffable, which is most of
 * why ADR-010 chose JSON in the first place.
 */
public class ExportService {

    private final EnvironmentRepository environments;
    private final ApplicationRepository applications;
    private final ApplicationVersionRepository versions;
    private final PromotionPathRepository promotionPaths;
    private final ReleasePackRepository releasePacks;
    private final ObservationRepository observations;
    private final Clock clock;

    public ExportService(EnvironmentRepository environments,
                         ApplicationRepository applications,
                         ApplicationVersionRepository versions,
                         PromotionPathRepository promotionPaths,
                         ReleasePackRepository releasePacks,
                         ObservationRepository observations,
                         Clock clock) {
        this.environments = Objects.requireNonNull(environments);
        this.applications = Objects.requireNonNull(applications);
        this.versions = Objects.requireNonNull(versions);
        this.promotionPaths = Objects.requireNonNull(promotionPaths);
        this.releasePacks = Objects.requireNonNull(releasePacks);
        this.observations = Objects.requireNonNull(observations);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * @param instanceName        this instance, stamped onto Observations it recorded itself
     * @param includeObservations ADR-010 makes Observations an explicit option, because a
     *                            colleague may want the plan without the sightings
     */
    public TowerExport export(String instanceName, boolean includeObservations) {
        return new TowerExport(
                TowerExport.CURRENT_SCHEMA_VERSION,
                instanceName,
                clock.instant(),
                environmentRecords(),
                applicationRecords(),
                versionRecords(),
                promotionPathRecords(),
                releasePackRecords(),
                includeObservations ? observationRecords(instanceName) : List.of());
    }

    private List<TowerExport.EnvironmentRecord> environmentRecords() {
        return environments.findAll().stream()
                .sorted(Comparator.comparing(e -> e.id().toString()))
                .map(e -> new TowerExport.EnvironmentRecord(
                        e.id().toString(), e.name(), e.stage().name()))
                .toList();
    }

    private List<TowerExport.ApplicationRecord> applicationRecords() {
        return applications.findAll().stream()
                .sorted(Comparator.comparing(a -> a.id().toString()))
                .map(a -> new TowerExport.ApplicationRecord(
                        a.id().toString(), a.name(), a.description()))
                .toList();
    }

    private List<TowerExport.ApplicationVersionRecord> versionRecords() {
        return versions.findAll().stream()
                .sorted(Comparator.comparing(v -> v.id().toString()))
                .map(v -> new TowerExport.ApplicationVersionRecord(
                        v.id().toString(), v.applicationId().toString(), v.version(),
                        v.branch(), v.tag(), v.commit(), v.buildIdentifier()))
                .toList();
    }

    private List<TowerExport.PromotionPathRecord> promotionPathRecords() {
        return promotionPaths.findAll().stream()
                .sorted(Comparator.comparing(p -> p.id().toString()))
                .map(this::toRecord)
                .toList();
    }

    private TowerExport.PromotionPathRecord toRecord(PromotionPath path) {
        List<TowerExport.PromotionPathVersionRecord> versionRecords = path.versions().stream()
                .map(version -> new TowerExport.PromotionPathVersionRecord(
                        version.number(),
                        version.createdAt(),
                        version.environments().stream().map(Object::toString).toList()))
                .toList();
        return new TowerExport.PromotionPathRecord(
                path.id().toString(), path.name(), path.isArchived(), versionRecords);
    }

    private List<TowerExport.ReleasePackRecord> releasePackRecords() {
        return releasePacks.findAll().stream()
                .sorted(Comparator.comparing(p -> p.id().toString()))
                .map(this::toRecord)
                .toList();
    }

    private TowerExport.ReleasePackRecord toRecord(ReleasePack pack) {
        return new TowerExport.ReleasePackRecord(
                pack.id().toString(),
                pack.name(),
                pack.description(),
                pack.isArchived(),
                pack.promotionPath().map(a -> a.pathId().toString()).orElse(null),
                pack.promotionPath().map(a -> a.versionNumber()).orElse(null),
                pack.contents().stream()
                        .map(c -> new TowerExport.PackedVersionRecord(
                                c.applicationId().toString(), c.versionId().toString()))
                        .toList(),
                pack.workItems().stream()
                        .map(w -> new TowerExport.WorkItemRecord(w.identifier(), w.title()))
                        .toList(),
                new TowerExport.HandoverRecord(
                        pack.handover().deploymentInstructions(),
                        pack.handover().shellCommands(),
                        pack.handover().databaseMigrations(),
                        pack.handover().rollbackProcedure(),
                        pack.handover().validationNotes(),
                        pack.handover().operationalNotes()),
                pack.iterations().stream()
                        .map(i -> new TowerExport.IterationRecord(
                                i.id().toString(), i.name(), i.startedAt(), i.completedAt(), i.notes()))
                        .toList());
    }

    private List<TowerExport.ObservationRecord> observationRecords(String instanceName) {
        return observations.findAll().stream()
                .sorted(Comparator.comparing(o -> o.id().toString()))
                .map(observation -> toRecord(observation, instanceName))
                .toList();
    }

    /**
     * Stamps the exporting instance onto Observations it recorded itself, and
     * leaves an already-travelled Observation's origin untouched.
     *
     * <p>This is the only place origin is ever written. A fact re-exported from
     * a second instance still names the first, so provenance survives any number
     * of hops and nobody inherits credit for a sighting they did not make.
     */
    private TowerExport.ObservationRecord toRecord(Observation observation, String instanceName) {
        ObservationSource source = observation.source();
        String origin = source.originInstance() == null ? instanceName : source.originInstance();

        return new TowerExport.ObservationRecord(
                observation.id().toString(),
                observation.environmentId().toString(),
                observation.applicationId().toString(),
                observation.applicationVersionId().toString(),
                observation.observedAt(),
                source.collector(),
                source.actor(),
                origin);
    }
}
