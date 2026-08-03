package dev.tower.application.sync;

import java.time.Instant;
import java.util.List;

/**
 * What reading a CI system's pipeline runs produced (ADR-020).
 *
 * <p>Deliberately not a {@link SyncRun}, and the reason is one line of that
 * type: {@code confirmsLiveness()} is true of any run that succeeded, and the
 * Viewer turns it into "everything Tower already knew was confirmed still
 * present at this time". That statement is true of a Deployment Platform, which
 * reads what is running now. It is false of a CI system, which reads what
 * happened. A successful read of a job with no new runs confirms only that
 * nothing was deployed by that job since Tower last looked — a narrower and
 * different claim.
 *
 * <p>Reusing SyncRun would have made a screen assert the wrong one, so the two
 * are kept apart until somebody decides how a single history should describe
 * both (OQ-017).
 *
 * @param id                   this report's identity
 * @param connectorId          which Connector read
 * @param startedAt            when Tower began reading
 * @param finishedAt           when it stopped
 * @param jobsRead             how many bound jobs it asked about
 * @param runsRead             how many runs those jobs reported
 * @param observationsAppended how many were new facts; zero is the ordinary
 *                             steady state, exactly as under ADR-011
 * @param notRecorded          runs read and deliberately not recorded (FR-078, FR-080)
 * @param failures             jobs that could not be read at all, in words fit
 *                             to show a user
 */
public record PipelineSyncReport(
        PipelineSyncReportId id,
        String connectorId,
        Instant startedAt,
        Instant finishedAt,
        int jobsRead,
        int runsRead,
        int observationsAppended,
        List<NotRecordedRun> notRecorded,
        List<String> failures) {

    public PipelineSyncReport {
        notRecorded = notRecorded == null ? List.of() : List.copyOf(notRecorded);
        failures = failures == null ? List.of() : List.copyOf(failures);
    }

    /**
     * What a successful read with nothing new actually establishes.
     *
     * <p>Named for the claim rather than for liveness, because it is not
     * liveness: nothing here says a version is still running, only that no job
     * reported deploying one since Tower last looked.
     */
    public boolean confirmsNothingWasDeployed() {
        return failures.isEmpty() && observationsAppended == 0;
    }

    public boolean readEverythingItWasAskedTo() {
        return failures.isEmpty();
    }
}
