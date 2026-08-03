package dev.tower.persistence.sync;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.tower.application.port.out.PipelineSyncReportRepository;
import dev.tower.application.sync.NotRecordedRun;
import dev.tower.application.sync.PipelineSyncReport;
import dev.tower.application.sync.PipelineSyncReportId;

/**
 * {@link PipelineSyncReportRepository} adapter backed by
 * {@code pipeline_sync_report} and its two child tables (V13).
 *
 * <p>Mirrors {@link SyncRunJdbcRepository} closely, including one query per
 * report for the children rather than a join: a join would repeat every header
 * column once per child row and reassembling two independent lists from that
 * cross product is the kind of code that quietly returns duplicates.
 *
 * <p>Offers only append and read, matching the port. A report records something
 * that already happened.
 */
@Repository
public class PipelineSyncReportJdbcRepository implements PipelineSyncReportRepository {

    private static final String SELECT_REPORT = """
            SELECT id, connector_id, started_at, finished_at,
                   jobs_read, runs_read, observations_appended
            FROM pipeline_sync_report
            """;

    private final JdbcClient jdbcClient;

    public PipelineSyncReportJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public PipelineSyncReport append(PipelineSyncReport report) {
        jdbcClient.sql("""
                        INSERT INTO pipeline_sync_report (id, connector_id, started_at, finished_at,
                                                          jobs_read, runs_read, observations_appended)
                        VALUES (:id, :connectorId, :startedAt, :finishedAt,
                                :jobsRead, :runsRead, :observationsAppended)
                        """)
                .param("id", report.id().value())
                .param("connectorId", report.connectorId())
                .param("startedAt", Timestamp.from(report.startedAt()))
                .param("finishedAt", Timestamp.from(report.finishedAt()))
                .param("jobsRead", report.jobsRead())
                .param("runsRead", report.runsRead())
                .param("observationsAppended", report.observationsAppended())
                .update();

        for (int position = 0; position < report.notRecorded().size(); position++) {
            NotRecordedRun run = report.notRecorded().get(position);
            jdbcClient.sql("""
                            INSERT INTO pipeline_sync_not_recorded
                                (report_id, position, job, run_id, outcome, reason)
                            VALUES (:reportId, :position, :job, :runId, :outcome, :reason)
                            """)
                    .param("reportId", report.id().value())
                    .param("position", position)
                    .param("job", run.job())
                    .param("runId", run.runId())
                    .param("outcome", run.outcome())
                    .param("reason", run.reason())
                    .update();
        }

        for (int position = 0; position < report.failures().size(); position++) {
            jdbcClient.sql("""
                            INSERT INTO pipeline_sync_failure (report_id, position, message)
                            VALUES (:reportId, :position, :message)
                            """)
                    .param("reportId", report.id().value())
                    .param("position", position)
                    .param("message", report.failures().get(position))
                    .update();
        }

        return report;
    }

    @Override
    public List<PipelineSyncReport> findRecent(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<Header> headers = jdbcClient.sql(
                        SELECT_REPORT + " ORDER BY started_at DESC, id DESC FETCH FIRST :limit ROWS ONLY")
                .param("limit", limit)
                .query(PipelineSyncReportJdbcRepository::mapHeader)
                .list();

        List<PipelineSyncReport> reports = new ArrayList<>(headers.size());
        for (Header header : headers) {
            reports.add(new PipelineSyncReport(header.id(), header.connectorId(),
                    header.startedAt(), header.finishedAt(), header.jobsRead(), header.runsRead(),
                    header.observationsAppended(),
                    notRecordedOf(header.id()), failuresOf(header.id())));
        }
        return reports;
    }

    private List<NotRecordedRun> notRecordedOf(PipelineSyncReportId id) {
        return jdbcClient.sql("""
                        SELECT job, run_id, outcome, reason FROM pipeline_sync_not_recorded
                        WHERE report_id = :reportId ORDER BY position
                        """)
                .param("reportId", id.value())
                .query((ResultSet rs, int rowNum) -> new NotRecordedRun(
                        rs.getString("job"), rs.getString("run_id"),
                        rs.getString("outcome"), rs.getString("reason")))
                .list();
    }

    private List<String> failuresOf(PipelineSyncReportId id) {
        return jdbcClient.sql("""
                        SELECT message FROM pipeline_sync_failure
                        WHERE report_id = :reportId ORDER BY position
                        """)
                .param("reportId", id.value())
                .query((ResultSet rs, int rowNum) -> rs.getString("message"))
                .list();
    }

    private static Header mapHeader(ResultSet rs, int rowNum) throws SQLException {
        return new Header(
                new PipelineSyncReportId(UUID.fromString(rs.getString("id"))),
                rs.getString("connector_id"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("finished_at").toInstant(),
                rs.getInt("jobs_read"),
                rs.getInt("runs_read"),
                rs.getInt("observations_appended"));
    }

    /** The header row before its children are attached. */
    private record Header(PipelineSyncReportId id, String connectorId,
                          java.time.Instant startedAt, java.time.Instant finishedAt,
                          int jobsRead, int runsRead, int observationsAppended) {
    }
}
