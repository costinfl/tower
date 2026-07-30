package dev.tower.application.service;

import dev.tower.application.port.in.ObservationUseCases;
import dev.tower.application.port.out.ApplicationVersionRepository;
import dev.tower.application.port.out.EnvironmentRepository;
import dev.tower.application.port.out.ManualObservationCollector;
import dev.tower.application.port.out.ObservationRepository;
import dev.tower.application.port.out.ReleasePackRepository;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.observation.EnvironmentState;
import dev.tower.domain.observation.Observation;
import dev.tower.domain.observation.ReleasePackProgression;
import dev.tower.domain.observation.StateComparison;
import dev.tower.domain.releasepack.PackedVersion;
import dev.tower.domain.releasepack.ReleasePack;
import dev.tower.domain.releasepack.ReleasePackId;
import dev.tower.domain.releasepack.ReleasePackState;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Observation use cases (issues #21 to #24).
 *
 * <p>Carries no framework annotation; Spring wiring lives in tower-api.
 */
public class ObservationService implements ObservationUseCases {

    private final ObservationRepository observations;
    private final EnvironmentRepository environments;
    private final ApplicationVersionRepository versions;
    private final ReleasePackRepository releasePacks;
    private final ManualObservationCollector manualCollector;
    private final Clock clock;

    public ObservationService(ObservationRepository observations,
                              EnvironmentRepository environments,
                              ApplicationVersionRepository versions,
                              ReleasePackRepository releasePacks,
                              ManualObservationCollector manualCollector,
                              Clock clock) {
        this.observations = Objects.requireNonNull(observations);
        this.environments = Objects.requireNonNull(environments);
        this.versions = Objects.requireNonNull(versions);
        this.releasePacks = Objects.requireNonNull(releasePacks);
        this.manualCollector = Objects.requireNonNull(manualCollector);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Observation recordManual(RecordManualObservation command) {
        Environment environment = environments.findById(command.environmentId())
                .orElseThrow(() -> new NotFoundException(
                        "Environment " + command.environmentId() + " does not exist."));
        ApplicationVersion version = versions.findById(command.applicationVersionId())
                .orElseThrow(() -> new NotFoundException(
                        "Application Version " + command.applicationVersionId() + " does not exist."));

        // A future observation would be a claim about something that has not
        // happened, and being append-only it could never be corrected, only
        // buried. Refuse it rather than record it.
        var observedAt = command.observedAt() == null ? clock.instant() : command.observedAt();
        if (observedAt.isAfter(clock.instant())) {
            throw new ApplicationException("An Observation cannot be dated in the future."
                    + " Tower records what has been seen, not what is planned.");
        }

        // Provenance comes from the Collector, never from the caller, so a
        // client cannot claim a fact arrived from somewhere it did not (ADR-006).
        Observation observation = Observation.record(
                environment.id(), version.applicationId(), version.id(), observedAt, manualCollector.source());

        return observations.append(observation);
    }

    @Override
    public EnvironmentState environmentState(EnvironmentId environmentId) {
        requireEnvironment(environmentId);
        return EnvironmentState.from(environmentId, observations.findAllInEnvironment(environmentId));
    }

    @Override
    public EnvironmentState environmentStateAt(EnvironmentId environmentId, Instant at) {
        requireEnvironment(environmentId);
        // Filtered in the fold rather than in the query. The Observation stream
        // for one Environment is small for the workload ADR-009 describes, and
        // pushing the bound into the port would add a second way to ask the same
        // question - two places for the "at or before" rule to live.
        return EnvironmentState.asOf(
                environmentId, observations.findAllInEnvironment(environmentId), at);
    }

    @Override
    public StateComparison compareStates(
            EnvironmentId leftEnvironment, Instant leftAt,
            EnvironmentId rightEnvironment, Instant rightAt) {
        return StateComparison.between(
                environmentStateAt(leftEnvironment, leftAt),
                environmentStateAt(rightEnvironment, rightAt));
    }

    @Override
    public List<Observation> historyOf(EnvironmentId environmentId) {
        requireEnvironment(environmentId);
        return observations.findAllInEnvironment(environmentId).stream()
                .sorted(Comparator.comparing(Observation::observedAt).reversed())
                .toList();
    }

    /**
     * ADR-008: the highest Stage at which any of the pack's contents has been
     * observed. The Stage comes from the Environment, so the derivation works
     * for any user-defined Promotion Path.
     */
    @Override
    public ReleasePackState stateOf(ReleasePackId releasePackId) {
        ReleasePack pack = requirePack(releasePackId);
        Set<ApplicationVersionId> contents = pack.contents().stream()
                .map(PackedVersion::versionId)
                .collect(Collectors.toSet());

        if (contents.isEmpty()) {
            return ReleasePackState.PLANNED;
        }

        Map<EnvironmentId, Environment> byId = environmentsById();
        List<ReleasePackState.Sighting> sightings = observations.findAllOfVersions(contents).stream()
                .map(observation -> {
                    Environment environment = byId.get(observation.environmentId());
                    // An Observation whose Environment has since been deleted cannot
                    // be classified, so it is skipped rather than guessed at.
                    return environment == null ? null
                            : new ReleasePackState.Sighting(
                                    observation.applicationVersionId(), environment.stage());
                })
                .filter(Objects::nonNull)
                .toList();

        return ReleasePackState.derive(contents, sightings);
    }

    @Override
    public List<PackSighting> sightingsOf(ReleasePackId releasePackId) {
        ReleasePack pack = requirePack(releasePackId);
        Set<ApplicationVersionId> contents = pack.contents().stream()
                .map(PackedVersion::versionId)
                .collect(Collectors.toSet());

        if (contents.isEmpty()) {
            return List.of();
        }

        Map<EnvironmentId, Environment> byId = environmentsById();
        return observations.findAllOfVersions(contents).stream()
                .filter(observation -> byId.containsKey(observation.environmentId()))
                .sorted(Comparator.comparing(Observation::observedAt).reversed())
                .map(observation -> new PackSighting(
                        observation.environmentId(),
                        byId.get(observation.environmentId()).name(),
                        observation.applicationVersionId(),
                        observation.observedAt(),
                        observation.source().collector(),
                        observation.source().actor(),
                        observation.id()))
                .toList();
    }

    @Override
    public ReleasePackProgression progressionOf(ReleasePackId releasePackId) {
        ReleasePack pack = requirePack(releasePackId);
        Set<ApplicationVersionId> contents = pack.contents().stream()
                .map(PackedVersion::versionId)
                .collect(Collectors.toSet());

        if (contents.isEmpty()) {
            return ReleasePackProgression.from(releasePackId, contents, List.of());
        }

        // Observations whose Environment has since been deleted are dropped, as
        // in sightingsOf: an arrival that cannot be named is worse than one that
        // is not shown.
        Map<EnvironmentId, Environment> byId = environmentsById();
        return ReleasePackProgression.from(releasePackId, contents,
                observations.findAllOfVersions(contents).stream()
                        .filter(observation -> byId.containsKey(observation.environmentId()))
                        .toList());
    }

    private Map<EnvironmentId, Environment> environmentsById() {
        return environments.findAll().stream()
                .collect(Collectors.toMap(Environment::id, Function.identity()));
    }

    private void requireEnvironment(EnvironmentId environmentId) {
        if (environments.findById(environmentId).isEmpty()) {
            throw new NotFoundException("Environment " + environmentId + " does not exist.");
        }
    }

    private ReleasePack requirePack(ReleasePackId releasePackId) {
        return releasePacks.findById(releasePackId)
                .orElseThrow(() -> new NotFoundException(
                        "Release Pack " + releasePackId + " does not exist."));
    }
}
