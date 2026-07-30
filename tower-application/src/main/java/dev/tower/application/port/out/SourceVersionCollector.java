package dev.tower.application.port.out;

import dev.tower.application.discovery.VersionDiscovery;
import dev.tower.application.sync.ConnectionTest;
import dev.tower.domain.application.ApplicationId;

/**
 * Outbound port for discovering candidate Application Versions from source
 * control (issue #3, ADR-014).
 *
 * <p>Sibling of {@link DeploymentObservationCollector}, and shaped the same way
 * for the same reason: it names no Connector type, because the application layer
 * may not depend on {@code dev.tower.connector..} (CM-03, ADR-003). The
 * implementation lives in tower-collector, which applies the binding and does the
 * normalization Connector-Model.md assigns to a Collector.
 *
 * <p>One difference from its sibling is worth stating, because it is a rule
 * rather than an omission: this Collector writes nothing. The deployment
 * Collector appends Observations, which are facts about the world Tower saw for
 * itself. A ref is not a fact about a deployment — it is a list of names someone
 * may want to register — so discovery hands back candidates and stores none of
 * them. BR-01 makes an Application Version immutable, and a version created
 * without anyone asking would be immutable too.
 */
public interface SourceVersionCollector {

    /** The Connector this Collector normalizes for. */
    String connectorId();

    /**
     * Looks at the repository bound to this Application and reports what it
     * holds.
     *
     * <p>Returns rather than throws when the repository cannot be read.
     * Connector-Model.md requires a Connector failure not to invalidate the
     * Canonical Model, and here there is nothing to invalidate — the failure is
     * simply the answer, carried in the result so a caller can show it.
     */
    VersionDiscovery discover(ApplicationId applicationId);

    /**
     * Checks that the repository is reachable and any credential accepted,
     * without modifying it (FR-061, FR-036, CM-01).
     *
     * <p>Separate from {@link #discover} so a binding can be verified before
     * anyone relies on it, and so "cannot read the repository" is distinguishable
     * from "read it and recognised nothing" — which look identical in an empty
     * candidate list.
     */
    ConnectionTest checkConnection(String repositoryUrl);
}
