package dev.tower.domain.observation;

import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.shared.DomainException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What is currently deployed in an Environment, derived from its Observations
 * (FR-010 to FR-013).
 *
 * <p>Environment state is reconstructed rather than stored (State-Model.md).
 * Deriving it means there is exactly one source of truth — the Observations —
 * and no stored projection that can silently disagree with them.
 *
 * <p>For each Application the most recent Observation wins. Tower reports only
 * what it was told: an Application nobody has observed simply does not appear,
 * and Tower never fills the gap with a guess (Scenarios.md, Scenario 4).
 */
public record EnvironmentState(EnvironmentId environmentId, List<DeployedApplication> deployed) {

    public EnvironmentState {
        DomainException.require(environmentId != null, "Environment id is required.");
        deployed = List.copyOf(deployed);
    }

    /**
     * Derives current state from a set of Observations.
     *
     * <p>Observations for other Environments are ignored rather than rejected,
     * so a caller may pass a broad result set without pre-filtering it.
     */
    public static EnvironmentState from(EnvironmentId environmentId, Collection<Observation> observations) {
        DomainException.require(environmentId != null, "Environment id is required.");

        Map<ApplicationId, Observation> latestPerApplication = new LinkedHashMap<>();
        for (Observation observation : observations) {
            if (observation == null || !environmentId.equals(observation.environmentId())) {
                continue;
            }
            latestPerApplication.merge(observation.applicationId(), observation,
                    (incumbent, candidate) -> candidate.isNewerThan(incumbent) ? candidate : incumbent);
        }

        List<DeployedApplication> deployed = new ArrayList<>();
        latestPerApplication.values().forEach(observation -> deployed.add(new DeployedApplication(
                observation.applicationId(),
                observation.applicationVersionId(),
                observation.observedAt(),
                observation.source(),
                observation.id())));
        deployed.sort(Comparator.comparing((DeployedApplication d) -> d.observedAt()).reversed());

        return new EnvironmentState(environmentId, deployed);
    }

    /**
     * When this Environment was last observed, or empty if it never has been.
     *
     * <p>FR-012 requires the timestamp to be shown. An Environment nobody has
     * observed is reported as exactly that, rather than as empty.
     */
    public Optional<Instant> lastObservedAt() {
        return deployed.stream().map(DeployedApplication::observedAt).max(Comparator.naturalOrder());
    }

    public boolean hasBeenObserved() {
        return !deployed.isEmpty();
    }

    public Optional<DeployedApplication> deploymentOf(ApplicationId applicationId) {
        return deployed.stream().filter(d -> d.applicationId().equals(applicationId)).findFirst();
    }

    /**
     * One Application currently deployed in an Environment.
     *
     * <p>The originating Observation is carried through so that every displayed
     * value remains traceable to the fact that produced it (FR-013, FR-031,
     * NFR-011, NFR-013).
     */
    public record DeployedApplication(
            ApplicationId applicationId,
            dev.tower.domain.application.ApplicationVersionId applicationVersionId,
            Instant observedAt,
            ObservationSource source,
            ObservationId observationId) {
    }
}
