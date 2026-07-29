package dev.tower.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dev.tower.application.port.in.SynchronizationUseCases;
import dev.tower.application.port.out.DeploymentObservationCollector;
import dev.tower.application.port.out.SyncRunRepository;
import dev.tower.application.sync.SyncRun;

/**
 * Synchronization use cases (issue #51).
 *
 * <p>Carries no framework annotation, like every service here; Spring wiring
 * lives in tower-api.
 *
 * <p>Thin on purpose. The Collector does the work ADR-011 and Connector-Model.md
 * assign to it — reading, normalizing, comparing — and this records the result.
 * Splitting them that way is what lets the Collector be tested against a fake
 * Connector with no database, and this be tested with no Connector at all.
 *
 * <p>Takes every Collector rather than one, so adding a Source Control Collector
 * later changes nothing here. Each is run independently: one Connector failing
 * must not stop another from being read, for the same reason one namespace
 * failing does not stop another within a run.
 */
public class SynchronizationService implements SynchronizationUseCases {

    /** Bounds the history query, which would otherwise grow without limit. */
    public static final int DEFAULT_HISTORY_LIMIT = 20;

    private final List<DeploymentObservationCollector> collectors;
    private final SyncRunRepository runs;

    public SynchronizationService(List<DeploymentObservationCollector> collectors, SyncRunRepository runs) {
        this.collectors = List.copyOf(Objects.requireNonNull(collectors));
        this.runs = Objects.requireNonNull(runs);
    }

    @Override
    public List<SyncRun> synchronizeNow() {
        List<SyncRun> completed = new ArrayList<>(collectors.size());
        for (DeploymentObservationCollector collector : collectors) {
            // The Collector already converts a Connector failure into a recorded
            // outcome rather than an exception, so a run always produces a record
            // worth storing — including one that failed entirely.
            completed.add(runs.append(collector.collect()));
        }
        return List.copyOf(completed);
    }

    @Override
    public List<SyncRun> history(int limit) {
        return runs.findRecent(limit <= 0 ? DEFAULT_HISTORY_LIMIT : limit);
    }

    @Override
    public Optional<SyncRun> lastConfirmation(String connectorId) {
        InvalidRequestException.require(connectorId != null && !connectorId.isBlank(),
                "Name the Connector whose last confirmation you want.");
        return runs.findLatestSuccessful(connectorId);
    }
}
