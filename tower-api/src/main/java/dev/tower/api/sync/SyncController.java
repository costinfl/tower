package dev.tower.api.sync;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.tower.application.port.in.SynchronizationUseCases;

/**
 * Issue #51: REST API for synchronization (FR-057, FR-059, FR-060).
 *
 * <p>{@code POST /api/sync} runs synchronously and answers 200 with what
 * happened, rather than 202 with nothing. ADR-011 makes synchronization
 * on-demand precisely so a person is present to see the result; answering
 * "accepted" and making them poll would undo that.
 */
@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SynchronizationUseCases synchronization;

    public SyncController(SynchronizationUseCases synchronization) {
        this.synchronization = synchronization;
    }

    @PostMapping
    public List<SyncRunResponse> synchronizeNow() {
        return synchronization.synchronizeNow().stream().map(SyncRunResponse::from).toList();
    }

    @GetMapping("/runs")
    public List<SyncRunResponse> history(
            @RequestParam(name = "limit", required = false, defaultValue = "0") int limit) {
        return synchronization.history(limit).stream().map(SyncRunResponse::from).toList();
    }

    /**
     * The last run that read everything successfully, or 404 when there has
     * never been one.
     *
     * <p>404 rather than an empty body with 200: "Tower has never completed a
     * run against this Connector" is a different statement from "here is the
     * confirmation", and a client rendering "confirmed present as of …" must not
     * be able to reach it by accident.
     */
    @GetMapping("/last-confirmation")
    public ResponseEntity<SyncRunResponse> lastConfirmation(
            @RequestParam("connectorId") String connectorId) {
        return synchronization.lastConfirmation(connectorId)
                .map(SyncRunResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
