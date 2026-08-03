package dev.tower.api.sync;

import java.time.Instant;
import java.util.List;

import dev.tower.application.sync.NotRecordedRun;
import dev.tower.application.sync.PipelineSyncReport;

/**
 * What a pipeline read produced, as the API renders it (ADR-020).
 *
 * <p>Carries {@code confirmsNothingWasDeployed} rather than a
 * {@code confirmsLiveness}, and the difference is the point. A Deployment
 * Platform reads what is running, so a clean run licenses "still present as of".
 * A CI system reads what happened, so a clean read licenses only "nothing was
 * deployed by these jobs since Tower last looked". A screen given the first
 * would say something Tower does not know.
 *
 * @param notRecorded what Tower read and declined to record, which is the field
 *                    a team with an untidy CI system will look at most
 */
public record PipelineSyncReportResponse(
        String id,
        String connectorId,
        Instant startedAt,
        Instant finishedAt,
        int jobsRead,
        int runsRead,
        int observationsAppended,
        boolean readEverything,
        boolean confirmsNothingWasDeployed,
        List<NotRecordedRun> notRecorded,
        List<String> failures) {

    public static PipelineSyncReportResponse from(PipelineSyncReport report) {
        return new PipelineSyncReportResponse(
                report.id().toString(),
                report.connectorId(),
                report.startedAt(),
                report.finishedAt(),
                report.jobsRead(),
                report.runsRead(),
                report.observationsAppended(),
                report.readEverythingItWasAskedTo(),
                report.confirmsNothingWasDeployed(),
                report.notRecorded(),
                report.failures());
    }
}
