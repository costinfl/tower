package dev.tower.application.port.in;

import java.util.List;

import dev.tower.application.sync.ConnectionTest;
import dev.tower.application.sync.PipelineSyncReport;

/**
 * Inbound port for reading a CI system's runs (ADR-020, FR-076).
 *
 * <p>On demand only, for the reason ADR-011 gives for the other Collector and
 * with more force here: a binding that names the wrong parameter writes
 * Observations that are immutable once written, and a person pressing a button
 * is present to read the report that says what was and was not recorded.
 */
public interface PipelineSynchronizationUseCases {

    /**
     * Reads every bound job now and records what each pass did.
     *
     * <p>Returns the reports rather than a status, because the report is the
     * point: what was read, what became a fact, and what was deliberately not
     * recorded (FR-078, FR-080).
     */
    List<PipelineSyncReport> synchronizePipelinesNow();

    /** The most recent reports, newest first. */
    List<PipelineSyncReport> history(int limit);

    /**
     * Confirms a CI system answers and the credential is accepted (FR-061).
     *
     * <p>Takes a job as well as a system, because a credential that can reach the
     * server may still not see the job, and a test that only asked about the
     * server would pass while every read failed.
     */
    ConnectionTest testConnection(String connectorId, String system, String job);
}
