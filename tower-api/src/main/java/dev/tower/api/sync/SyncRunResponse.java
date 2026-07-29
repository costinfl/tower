package dev.tower.api.sync;

import java.time.Instant;
import java.util.List;

import dev.tower.application.sync.SyncRun;

/**
 * What one synchronization run did, as the API reports it (FR-059, FR-060).
 *
 * <p>{@code confirmsLiveness} is included rather than left for a client to infer
 * from the outcome. It is the answer to "may I say this deployment was still
 * present at this time", and only a wholly successful run licenses that — a
 * partial run says nothing about the scopes it could not read.
 */
public record SyncRunResponse(
        String id,
        String connectorId,
        Instant startedAt,
        Instant finishedAt,
        String outcome,
        int workloadsRead,
        int observationsAppended,
        boolean foundNoChange,
        boolean confirmsLiveness,
        List<UnrecognizedWorkloadResponse> unrecognized,
        List<String> failures) {

    public static SyncRunResponse from(SyncRun run) {
        return new SyncRunResponse(
                run.id().toString(),
                run.connectorId(),
                run.startedAt(),
                run.finishedAt(),
                run.outcome().name(),
                run.workloadsRead(),
                run.observationsAppended(),
                run.foundNoChange(),
                run.confirmsLiveness(),
                run.unrecognized().stream().map(UnrecognizedWorkloadResponse::from).toList(),
                run.failures());
    }

    public record UnrecognizedWorkloadResponse(
            String scope, String name, String imageReference, String reason) {

        public static UnrecognizedWorkloadResponse from(dev.tower.application.sync.UnrecognizedWorkload workload) {
            return new UnrecognizedWorkloadResponse(
                    workload.scope(), workload.name(), workload.imageReference(), workload.reason());
        }
    }
}
