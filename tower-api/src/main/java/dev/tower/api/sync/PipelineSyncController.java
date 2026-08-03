package dev.tower.api.sync;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.tower.application.port.in.PipelineSynchronizationUseCases;
import dev.tower.application.sync.ConnectionTest;

/**
 * REST API for reading a CI system's runs (ADR-020, FR-076).
 *
 * <p>Its own path rather than more verbs under {@code /api/sync}, matching the
 * separation ADR-020 requires of the reports themselves: what these two read,
 * and what a clean read of each establishes, are different things, and one
 * endpoint returning both shapes would leave a client to guess which it had.
 * OQ-017 records the question of how a reader should eventually see them
 * together.
 *
 * <p>{@code POST} runs synchronously and answers 200 with what happened, for the
 * reason {@link SyncController} does: reading is on demand precisely so a person
 * is present to see the result, and here that matters more — the report is where
 * a binding that names the wrong parameter shows up.
 */
@RestController
@RequestMapping("/api/pipeline-sync")
public class PipelineSyncController {

    private final PipelineSynchronizationUseCases synchronization;

    public PipelineSyncController(PipelineSynchronizationUseCases synchronization) {
        this.synchronization = synchronization;
    }

    @PostMapping
    public List<PipelineSyncReportResponse> synchronizeNow() {
        return synchronization.synchronizePipelinesNow().stream()
                .map(PipelineSyncReportResponse::from).toList();
    }

    @GetMapping("/reports")
    public List<PipelineSyncReportResponse> history(
            @RequestParam(name = "limit", required = false, defaultValue = "0") int limit) {
        return synchronization.history(limit).stream()
                .map(PipelineSyncReportResponse::from).toList();
    }

    /**
     * Checks a job without modifying the CI system (FR-061, FR-036, CM-01).
     *
     * <p>GET, because the operation changes nothing and the method should say so.
     * Answers 200 whether or not the system was reachable: unreachable is the
     * result the caller asked for, and the body carries the reason.
     */
    @GetMapping("/connection-test")
    public ConnectionTest testConnection(
            @RequestParam("connectorId") String connectorId,
            @RequestParam("system") String system,
            @RequestParam("job") String job) {
        return synchronization.testConnection(connectorId, system, job);
    }
}
