package dev.tower.docgen;

import dev.tower.application.port.in.ApplicationUseCases;
import dev.tower.application.port.in.ArtifactUseCases;
import dev.tower.application.port.in.EnvironmentUseCases;
import dev.tower.application.port.in.ObservationUseCases;
import dev.tower.application.port.in.PromotionPathUseCases;
import dev.tower.application.port.in.ReleasePackUseCases;
import dev.tower.domain.application.AcceptedArtifact;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.handover.Handover;
import dev.tower.domain.promotionpath.PromotionPath;
import dev.tower.domain.promotionpath.PromotionPathVersion;
import dev.tower.domain.releasepack.PackedVersion;
import dev.tower.domain.releasepack.PromotionPathAssignment;
import dev.tower.domain.releasepack.ReleasePack;
import dev.tower.domain.releasepack.ReleasePackId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Assembles a {@link ReleaseDocument} from the Canonical Model (FR-024, FR-032).
 *
 * <p>Everything is read through the application layer's inbound ports. Nothing
 * here reaches a Connector or storage directly, which is what FR-038 and the
 * Information Architecture require and what an architecture test enforces.
 *
 * <p>The assembled document is deterministic: the same Canonical Model always
 * produces the same document. Collections are ordered by stable business keys
 * rather than by whatever order storage happened to return, and no generation
 * timestamp is embedded. Scenario 8 requires regeneration to produce identical
 * output, and a clock in the document body would quietly break that. The only
 * timestamps present are the ones the facts themselves carry.
 */
public class ReleaseDocumentAssembler {

    private final ReleasePackUseCases releasePacks;
    private final ApplicationUseCases applications;
    private final PromotionPathUseCases promotionPaths;
    private final EnvironmentUseCases environments;
    private final ObservationUseCases observations;
    private final ArtifactUseCases artifacts;

    public ReleaseDocumentAssembler(ReleasePackUseCases releasePacks,
                                    ApplicationUseCases applications,
                                    PromotionPathUseCases promotionPaths,
                                    EnvironmentUseCases environments,
                                    ObservationUseCases observations,
                                    ArtifactUseCases artifacts) {
        this.releasePacks = Objects.requireNonNull(releasePacks);
        this.applications = Objects.requireNonNull(applications);
        this.promotionPaths = Objects.requireNonNull(promotionPaths);
        this.environments = Objects.requireNonNull(environments);
        this.observations = Objects.requireNonNull(observations);
        this.artifacts = Objects.requireNonNull(artifacts);
    }

    public ReleaseDocument assemble(ReleasePackId releasePackId) {
        ReleasePack pack = releasePacks.get(releasePackId);

        Map<ApplicationVersionId, ApplicationVersion> versionsById = new HashMap<>();
        Map<ApplicationVersionId, String> applicationNames = new HashMap<>();
        for (PackedVersion packed : pack.contents()) {
            versionsById.put(packed.versionId(), applications.getVersion(packed.versionId()));
            applicationNames.put(packed.versionId(), applications.get(packed.applicationId()).name());
        }

        return new ReleaseDocument(
                pack.name(),
                pack.description(),
                observations.stateOf(releasePackId),
                pack.isArchived(),
                promotionPathSection(pack),
                contents(pack, versionsById, applicationNames),
                artifacts(pack, versionsById, applicationNames),
                workItems(pack),
                handoverSection(pack.handover()),
                iterations(pack),
                sightings(releasePackId, versionsById, applicationNames));
    }

    /**
     * What the release claims to deliver, in the order the team linked them
     * (ADR-018).
     *
     * <p>Read from the Release Pack and from nowhere else. The tracker is not
     * consulted at generation time, which is what lets an unchanged release
     * regenerate byte-identically (NFR-025) however often its tickets are
     * edited afterwards.
     */
    private List<ReleaseDocument.WorkItemEntry> workItems(ReleasePack pack) {
        return pack.workItems().stream()
                .map(reference -> new ReleaseDocument.WorkItemEntry(
                        reference.identifier(), reference.title()))
                .toList();
    }

    /**
     * The artifact digests somebody accepted for this release's versions
     * (ADR-021, FR-086).
     *
     * <p>Read from Tower and from nowhere else. No repository is consulted here,
     * which is what lets an unchanged release regenerate byte-identically
     * (NFR-025) however often a tag is pushed over afterwards — and a tag being
     * pushed over is exactly why accepting a digest exists.
     *
     * <p>Ordered by Application name, then version, then kind, so the order comes
     * from the release rather than from whatever order storage returned.
     */
    private List<ReleaseDocument.ArtifactEntry> artifacts(
            ReleasePack pack,
            Map<ApplicationVersionId, ApplicationVersion> versionsById,
            Map<ApplicationVersionId, String> applicationNames) {

        List<ApplicationVersionId> versionIds = pack.contents().stream()
                .map(PackedVersion::versionId).toList();

        return artifacts.acceptedArtifactsFor(versionIds).stream()
                .map(accepted -> new ReleaseDocument.ArtifactEntry(
                        applicationNames.getOrDefault(accepted.applicationVersionId(), ""),
                        versionOf(versionsById, accepted),
                        accepted.kind(), accepted.coordinate(), accepted.digest()))
                .sorted(Comparator.comparing(ReleaseDocument.ArtifactEntry::applicationName)
                        .thenComparing(ReleaseDocument.ArtifactEntry::version)
                        .thenComparing(ReleaseDocument.ArtifactEntry::kind))
                .toList();
    }

    private String versionOf(Map<ApplicationVersionId, ApplicationVersion> versionsById,
                             AcceptedArtifact accepted) {
        ApplicationVersion version = versionsById.get(accepted.applicationVersionId());
        return version == null ? "" : version.version();
    }

    private Optional<ReleaseDocument.PromotionPathSection> promotionPathSection(ReleasePack pack) {
        Optional<PromotionPathAssignment> assignment = pack.promotionPath();
        if (assignment.isEmpty()) {
            return Optional.empty();
        }
        PromotionPathAssignment pinned = assignment.get();
        PromotionPath path = promotionPaths.get(pinned.pathId());

        // The pinned version, never the current one. A pack that followed
        // version 1 must keep describing version 1's topology after the path
        // moves on — the guarantee ADR-007 exists to provide.
        Optional<PromotionPathVersion> version = path.version(pinned.versionNumber());
        if (version.isEmpty()) {
            return Optional.empty();
        }

        Map<EnvironmentId, String> names = environments.list().stream()
                .collect(Collectors.toMap(Environment::id, Environment::name));

        List<String> sequence = version.get().environments().stream()
                .map(id -> names.getOrDefault(id, "(Environment no longer defined)"))
                .toList();

        return Optional.of(new ReleaseDocument.PromotionPathSection(
                path.name(), pinned.versionNumber(), sequence));
    }

    private List<ReleaseDocument.ContentEntry> contents(ReleasePack pack,
                                                        Map<ApplicationVersionId, ApplicationVersion> versionsById,
                                                        Map<ApplicationVersionId, String> applicationNames) {
        List<ReleaseDocument.ContentEntry> entries = new ArrayList<>();
        for (PackedVersion packed : pack.contents()) {
            ApplicationVersion version = versionsById.get(packed.versionId());
            entries.add(new ReleaseDocument.ContentEntry(
                    applicationNames.get(packed.versionId()),
                    version.version(),
                    version.branch(),
                    version.tag(),
                    version.commit(),
                    version.buildIdentifier()));
        }
        entries.sort(Comparator.comparing(ReleaseDocument.ContentEntry::applicationName)
                .thenComparing(ReleaseDocument.ContentEntry::version));
        return entries;
    }

    private ReleaseDocument.HandoverSection handoverSection(Handover handover) {
        return new ReleaseDocument.HandoverSection(
                handover.deploymentInstructions(),
                handover.shellCommands(),
                handover.databaseMigrations(),
                handover.rollbackProcedure(),
                handover.validationNotes(),
                handover.operationalNotes(),
                !handover.isEmpty());
    }

    private List<ReleaseDocument.IterationEntry> iterations(ReleasePack pack) {
        return pack.iterations().stream()
                .map(iteration -> new ReleaseDocument.IterationEntry(
                        iteration.name(), iteration.startedAt(), iteration.completedAt(), iteration.notes()))
                .sorted(Comparator.comparing(ReleaseDocument.IterationEntry::startedAt)
                        .thenComparing(ReleaseDocument.IterationEntry::name))
                .toList();
    }

    private List<ReleaseDocument.SightingEntry> sightings(ReleasePackId releasePackId,
                                                          Map<ApplicationVersionId, ApplicationVersion> versionsById,
                                                          Map<ApplicationVersionId, String> applicationNames) {
        return observations.sightingsOf(releasePackId).stream()
                .map(sighting -> new ReleaseDocument.SightingEntry(
                        sighting.environmentName(),
                        applicationNames.getOrDefault(sighting.applicationVersionId(), "(unknown Application)"),
                        Optional.ofNullable(versionsById.get(sighting.applicationVersionId()))
                                .map(ApplicationVersion::version)
                                .orElse("(unknown version)"),
                        sighting.observedAt(),
                        sighting.sourceCollector(),
                        sighting.sourceActor(),
                        sighting.observationId() == null ? null : sighting.observationId().toString()))
                .sorted(Comparator.comparing(ReleaseDocument.SightingEntry::observedAt).reversed()
                        .thenComparing(ReleaseDocument.SightingEntry::environmentName)
                        .thenComparing(ReleaseDocument.SightingEntry::applicationName))
                .toList();
    }

}
