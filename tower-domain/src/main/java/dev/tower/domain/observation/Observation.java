package dev.tower.domain.observation;

import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.shared.DomainException;

import java.time.Instant;

/**
 * An immutable fact collected from an identified source at a point in time —
 * the atomic unit of information in Tower (ADR-002).
 *
 * <p>Everything Tower knows originates here. Environment state and Release Pack
 * progression are derived from Observations rather than stored alongside them,
 * so there is no second copy of the truth to drift.
 *
 * <p>This type carries no mutator of any kind. BR-02, IA-01, SM-06 and FR-023
 * all say the same thing from different angles: an Observation is never edited.
 * A mistaken entry is superseded by a later Observation, and the record of what
 * was believed, and when, survives. That is what makes historical comparison
 * possible at all.
 *
 * <p>For Milestone 1 a Deployment Unit is an Application Version
 * (Domain-Model.md), so an Observation references the version directly.
 */
public record Observation(
        ObservationId id,
        EnvironmentId environmentId,
        ApplicationId applicationId,
        ApplicationVersionId applicationVersionId,
        Instant observedAt,
        ObservationSource source) {

    public Observation {
        DomainException.require(id != null, "Observation id is required.");
        DomainException.require(environmentId != null,
                "An Observation must name the Environment it was seen in.");
        DomainException.require(applicationId != null,
                "An Observation must name the Application observed.");
        DomainException.require(applicationVersionId != null,
                "An Observation must name the Application Version observed.");
        DomainException.require(observedAt != null,
                "An Observation must record when it was observed (FR-021).");
        DomainException.require(source != null,
                "An Observation must record where it came from (FR-022).");
    }

    public static Observation record(EnvironmentId environmentId, ApplicationId applicationId,
                                     ApplicationVersionId applicationVersionId,
                                     Instant observedAt, ObservationSource source) {
        return new Observation(ObservationId.newId(), environmentId, applicationId,
                applicationVersionId, observedAt, source);
    }

    /**
     * True when this Observation is more recent than another.
     *
     * <p>Ties fall to the incumbent, so replaying identical Observations cannot
     * reorder what Tower reports.
     */
    public boolean isNewerThan(Observation other) {
        return other == null || observedAt.isAfter(other.observedAt());
    }
}
