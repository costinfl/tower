package dev.tower.application.port.out;

import dev.tower.application.discovery.VersionDiscovery;
import dev.tower.application.sync.ConnectionTest;
import dev.tower.domain.application.ApplicationId;

/**
 * Outbound port for discovering candidate Application Versions from build runs
 * (ADR-020).
 *
 * <p>The sibling of {@link SourceVersionCollector}, and deliberately the same
 * shape. ADR-020 put it as "the version discovery screen gains a source rather
 * than a mode": a team that tags releases and a team that versions by build
 * number are both proposing candidates, and a second port returning a second
 * kind of thing would have made two screens out of one question.
 *
 * <p>Writes nothing, for the reason its sibling writes nothing. A build run is
 * not a fact about a deployment — it is a version somebody may want to register —
 * so discovery hands back candidates and stores none of them. BR-01 makes an
 * Application Version immutable, and one created without anyone asking would be
 * immutable too.
 *
 * <p>Which is also the whole difference from {@link PipelineRunObservationCollector},
 * the other port over the same Connector. That one appends Observations from
 * <em>deployment</em> runs; this proposes versions from <em>build</em> runs.
 * Reading the same CI system through two ports looks redundant until the
 * question is asked out loud: one is "what reached an Environment", the other is
 * "what exists to be released", and ADR-020 exists because conflating them
 * records a build as though it had deployed something.
 *
 * <p>Names no Connector type, and cannot: the application layer may not depend on
 * {@code dev.tower.connector..} (CM-03, ADR-003).
 */
public interface BuildVersionCollector {

    /** The Connector this Collector normalizes for. */
    String connectorId();

    /**
     * Looks at the build jobs bound to this Application and reports what their
     * runs produced.
     *
     * <p>Returns rather than throws when a job cannot be read, matching its
     * sibling: an unreachable CI system is the answer, not a failure of the
     * question, and there is nothing to invalidate because nothing is stored.
     */
    VersionDiscovery discover(ApplicationId applicationId);

    /**
     * Checks that a job is reachable and any credential accepted, without
     * modifying the CI system (FR-061, FR-036, CM-01).
     *
     * <p>Takes a job as well as a system for the reason the deployment side does:
     * a credential that reaches the server may still not see the job, and a test
     * that only asked about the server would pass while every read failed.
     */
    ConnectionTest checkConnection(String system, String job);
}
