package dev.tower.application.port.out;

import dev.tower.application.sync.ConnectionTest;
import dev.tower.application.sync.SyncRun;

/**
 * Outbound port for collecting Observations from a Deployment Platform
 * (ADR-006, ADR-011, ADR-012).
 *
 * <p>Sibling of {@link ManualObservationCollector}. ADR-006 models manual entry
 * as a Collector precisely so that adding an automated one changes nothing about
 * the Observation model — this is that promise being kept.
 *
 * <p>Names no Connector type, and cannot: the application layer may not depend
 * on {@code dev.tower.connector..} (CM-03, ADR-003). The implementation lives in
 * tower-collector, which does the normalization Connector-Model.md assigns to a
 * Collector and the change comparison ADR-011 assigns to it.
 */
public interface DeploymentObservationCollector {

    /** The Connector this Collector normalizes for, as stamped on provenance. */
    String connectorId();

    /**
     * Reads every Environment bound to this Collector's Connector and appends an
     * Observation wherever the deployed version has changed.
     *
     * <p>Returns rather than throws when a scope fails. Connector-Model.md
     * requires that a Connector failure not invalidate the Canonical Model, so a
     * namespace Tower could not read is recorded in the returned run and the
     * scopes that succeeded keep their Observations.
     */
    SyncRun collect();

    /**
     * Checks that the platform is reachable and the credential accepted, without
     * modifying it (FR-061, FR-036, CM-01).
     *
     * <p>Separate from {@link #collect()} so a binding can be verified before any
     * Observation depends on it, and so "cannot connect" is distinguishable from
     * "connected and found nothing" — which under ADR-011 look identical in a run.
     *
     * <p>Returns rather than throws: unreachable is the answer, not a failure of
     * the question.
     */
    ConnectionTest checkConnection(String target, String scope);
}
