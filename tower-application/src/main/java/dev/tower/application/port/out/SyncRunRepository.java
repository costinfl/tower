package dev.tower.application.port.out;

import java.util.List;
import java.util.Optional;

import dev.tower.application.sync.SyncRun;

/**
 * Outbound port for Sync Run records (ADR-011, FR-059).
 *
 * <p>Append-only, like {@link ObservationRepository} but for a different reason.
 * An Observation is immutable because it is a historical fact; a Sync Run is
 * immutable because it is a record of something that already happened and cannot
 * un-happen. Neither port offers an update.
 *
 * <p>Unlike Observations, Sync Runs may eventually need pruning: they accumulate
 * with every synchronization whether or not anything changed. ADR-011 records
 * that the retention decision is deliberately not made yet, so this port offers
 * no delete either — adding one would imply a policy nobody has chosen.
 */
public interface SyncRunRepository {

    SyncRun append(SyncRun run);

    /**
     * The most recent runs, newest first.
     *
     * <p>Bounded because the history grows without limit until a retention
     * policy exists, and an unbounded query would degrade quietly as it does.
     */
    List<SyncRun> findRecent(int limit);

    /** The most recent run for a Connector, whatever its outcome. */
    Optional<SyncRun> findLatest(String connectorId);

    /**
     * The most recent run that read every scope successfully.
     *
     * <p>This is what pairs with an Observation to answer "is this still
     * deployed" (ADR-011). A partial or failed run cannot answer it, because it
     * says nothing about the scopes it did not read.
     */
    Optional<SyncRun> findLatestSuccessful(String connectorId);
}
