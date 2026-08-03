package dev.tower.application.port.out;

import dev.tower.application.sync.ConnectionTest;
import dev.tower.application.sync.PipelineSyncReport;

/**
 * Outbound port for collecting Observations from a CI system's runs (ADR-020).
 *
 * <p>The sibling of {@link DeploymentObservationCollector}, and the difference
 * between them is the whole of ADR-020: that one reads what a platform is
 * running now, this reads what a pipeline reported doing. Both produce
 * Observations, on the footing ADR-006 established — a fact from an identified
 * source — and the source is stamped on each so a reader can tell which kind of
 * evidence they are looking at.
 *
 * <p>Names no Connector type, and cannot: the application layer may not depend
 * on {@code dev.tower.connector..} (CM-03, ADR-003).
 */
public interface PipelineRunObservationCollector {

    /** The Connector this Collector normalizes for, as stamped on provenance. */
    String connectorId();

    /**
     * Reads every bound job and appends an Observation for each successful run
     * that reports a version Tower does not already hold for that moment.
     *
     * <p>Returns rather than throws when a job fails, for the reason
     * Connector-Model.md gives under Failure Handling: a Connector failure must
     * not invalidate the Canonical Model, so the jobs that were read keep their
     * Observations and the one that failed is recorded in the report.
     */
    PipelineSyncReport collect();

    /**
     * Checks that the CI system is reachable and the credential accepted,
     * without modifying it (FR-061, FR-036, CM-01).
     *
     * <p>Returns rather than throws: unreachable is the answer, not a failure of
     * the question.
     */
    ConnectionTest checkConnection(String system, String job);
}
