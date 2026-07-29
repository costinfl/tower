package dev.tower.application.port.in;

import java.util.List;
import java.util.Optional;

import dev.tower.application.sync.ConnectionTest;
import dev.tower.application.sync.SyncRun;

/**
 * Inbound port for synchronization (issue #51, FR-057, FR-059).
 *
 * <p>On demand only. ADR-011 records that scheduling is deferred until the
 * image-to-version mapping is trusted, on the grounds that a wrong pattern
 * running unattended writes Observations that are immutable once written. A
 * person pressing a button is present to see the result.
 */
public interface SynchronizationUseCases {

    /**
     * Runs every configured Collector now and records what each did.
     *
     * <p>Returns the runs rather than a status, because the outcome is the
     * point: how many workloads were read, how many were new facts, and what
     * could not be attributed (FR-060).
     */
    List<SyncRun> synchronizeNow();

    /** The most recent runs, newest first (FR-059). */
    List<SyncRun> history(int limit);

    /**
     * The most recent wholly successful run for a Connector.
     *
     * <p>The other half of ADR-011's bargain. An Observation says when a version
     * last changed; this says when Tower last looked and found it unchanged.
     * Empty means Tower has never completed a run, and the interface should say
     * so rather than imply the Observation is current.
     */
    Optional<SyncRun> lastConfirmation(String connectorId);

    /**
     * Checks a binding's target and scope without modifying anything (FR-061).
     *
     * <p>Lets a user confirm a cluster URL, namespace and token together before
     * any Observation depends on them — the configuration mistakes that would
     * otherwise surface as a failed run, or worse as an empty one.
     */
    ConnectionTest testConnection(String connectorId, String target, String scope);
}
