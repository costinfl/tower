package dev.tower.application.port.out;

import java.util.List;

import dev.tower.application.sync.PipelineSyncReport;

/**
 * Stores what reading a CI system produced (ADR-020).
 *
 * <p>Append and read, with no update and no delete, matching
 * {@link SyncRunRepository}. A report is a record of something that happened;
 * editing one would be editing the past, and ADR-011 leaves retention open
 * rather than inviting a delete nobody has designed.
 *
 * <p>Separate from SyncRunRepository because the reports are separate, and
 * ADR-020 records why: the two describe reading different kinds of system and
 * support different claims about what a successful read establishes.
 */
public interface PipelineSyncReportRepository {

    PipelineSyncReport append(PipelineSyncReport report);

    /** The most recent reports, newest first. */
    List<PipelineSyncReport> findRecent(int limit);
}
