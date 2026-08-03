package dev.tower.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.tower.application.port.in.PipelineSynchronizationUseCases;
import dev.tower.application.port.out.PipelineRunObservationCollector;
import dev.tower.application.port.out.PipelineSyncReportRepository;
import dev.tower.application.sync.ConnectionTest;
import dev.tower.application.sync.PipelineSyncReport;

/**
 * Reading a CI system's runs, as a use case (ADR-020).
 *
 * <p>Thin, like {@link SynchronizationService}, and for the same reason: the
 * Collector does the work Connector-Model.md assigns it — reading, normalizing,
 * comparing against what Tower already holds — and this records the result.
 * Splitting them that way lets the Collector be tested against a fake Connector
 * with no database, and this be tested with no Connector at all.
 *
 * <p>Takes every Collector rather than one. ADR-020 expects a period with two CI
 * Connectors installed at once, since the Jenkins it was written for may be
 * replaced while its history still matters, and each is run independently: one
 * failing must not stop another from being read.
 *
 * <p>Carries no framework annotation, like every service here; Spring wiring
 * lives in tower-api.
 */
public class PipelineSynchronizationService implements PipelineSynchronizationUseCases {

    /** Bounds the history query, which would otherwise grow without limit. */
    public static final int DEFAULT_HISTORY_LIMIT = 20;

    private final List<PipelineRunObservationCollector> collectors;
    private final PipelineSyncReportRepository reports;

    public PipelineSynchronizationService(List<PipelineRunObservationCollector> collectors,
                                          PipelineSyncReportRepository reports) {
        this.collectors = List.copyOf(Objects.requireNonNull(collectors));
        this.reports = Objects.requireNonNull(reports);
    }

    @Override
    public List<PipelineSyncReport> synchronizePipelinesNow() {
        List<PipelineSyncReport> completed = new ArrayList<>(collectors.size());
        for (PipelineRunObservationCollector collector : collectors) {
            // The Collector already turns a Connector failure into a recorded
            // outcome rather than an exception, so a pass always produces a
            // report worth storing — including one that read nothing.
            completed.add(reports.append(collector.collect()));
        }
        return List.copyOf(completed);
    }

    @Override
    public List<PipelineSyncReport> history(int limit) {
        return reports.findRecent(limit <= 0 ? DEFAULT_HISTORY_LIMIT : limit);
    }

    @Override
    public ConnectionTest testConnection(String connectorId, String system, String job) {
        InvalidRequestException.require(connectorId != null && !connectorId.isBlank(),
                "Name the Connector to test.");
        InvalidRequestException.require(system != null && !system.isBlank(),
                "Name the CI system to connect to.");
        InvalidRequestException.require(job != null && !job.isBlank(),
                "Name the job to look for.");

        return collectors.stream()
                .filter(collector -> collector.connectorId().equals(connectorId))
                .findFirst()
                .map(collector -> collector.checkConnection(system, job))
                .orElseGet(() -> ConnectionTest.unreachable(connectorId, system, job,
                        "No Connector named '" + connectorId + "' is installed."));
    }
}
