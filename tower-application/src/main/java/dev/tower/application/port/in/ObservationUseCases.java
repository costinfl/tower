package dev.tower.application.port.in;

import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.observation.EnvironmentState;
import dev.tower.domain.observation.StateComparison;
import dev.tower.domain.observation.Observation;
import dev.tower.domain.observation.ObservationId;
import dev.tower.domain.releasepack.ReleasePackId;
import dev.tower.domain.releasepack.ReleasePackState;

import java.time.Instant;
import java.util.List;

/**
 * Inbound port for recording and reading Observations (issues #21 to #24).
 *
 * <p>There is no update and no delete. Observations are immutable (BR-02,
 * FR-023); a mistaken entry is corrected by recording a later one, which is
 * also what preserves the record of what was believed and when.
 */
public interface ObservationUseCases {

    /**
     * Records that a version was seen in an Environment, by a person (ADR-006).
     *
     * <p>The provenance is supplied by the Manual Collector rather than by the
     * caller, so a client cannot claim a fact came from somewhere it did not.
     */
    Observation recordManual(RecordManualObservation command);

    /** Current derived state of one Environment (FR-010 to FR-013). */
    EnvironmentState environmentState(EnvironmentId environmentId);

    /**
     * The state of an Environment as it stood at an instant — a Snapshot
     * (Milestone 4, ADR-017).
     *
     * <p>Derived rather than looked up, so any instant is answerable and not
     * only the ones somebody captured. That is the point of the decision: the
     * question is usually asked about the moment an incident began, which is
     * never a moment anyone snapshotted in advance.
     */
    EnvironmentState environmentStateAt(EnvironmentId environmentId, Instant at);

    /**
     * The difference between two derived states.
     *
     * <p>The same operation answers "how did UAT change between Monday and
     * Friday" and "how does UAT differ from Production now", which is why both
     * sides are given as an Environment and an instant.
     */
    StateComparison compareStates(
            EnvironmentId leftEnvironment, Instant leftAt,
            EnvironmentId rightEnvironment, Instant rightAt);

    /** Full Observation history for an Environment, newest first. */
    List<Observation> historyOf(EnvironmentId environmentId);

    /** Where a Release Pack has been observed (ADR-008, FR-016, FR-034). */
    ReleasePackState stateOf(ReleasePackId releasePackId);

    /**
     * Every Environment a Release Pack's contents have been observed in.
     *
     * <p>Answers "what changed between two environments" and "which Release
     * Packs are represented here" without the caller re-deriving it.
     */
    List<PackSighting> sightingsOf(ReleasePackId releasePackId);

    record RecordManualObservation(EnvironmentId environmentId,
                                   ApplicationVersionId applicationVersionId,
                                   Instant observedAt) {}

    /**
     * One Environment in which a Release Pack's content was seen.
     *
     * <p>Carries the originating Observation and its source, so that anything
     * built on top of this — generated documentation in particular — can cite
     * the fact behind each claim rather than asserting it bare (FR-031,
     * FR-032, NFR-011).
     */
    record PackSighting(EnvironmentId environmentId,
                        String environmentName,
                        ApplicationVersionId applicationVersionId,
                        Instant observedAt,
                        String sourceCollector,
                        String sourceActor,
                        ObservationId observationId) {}
}
