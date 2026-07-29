package dev.tower.persistence.sync;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.tower.application.port.out.SyncRunRepository;
import dev.tower.application.sync.SyncRun;
import dev.tower.application.sync.SyncRunId;
import dev.tower.application.sync.UnrecognizedWorkload;

/**
 * {@link SyncRunRepository} adapter backed by {@code sync_run} and its two child tables
 * (V6__sync_runs.sql).
 *
 * <p>Uses {@link JdbcClient} for the same reason as the other adapters: the types it stores are
 * immutable records with no framework dependency.
 *
 * <p>Offers only append and read, matching the port. A run is a record of something that already
 * happened; there is nothing to update.
 */
@Repository
public class SyncRunJdbcRepository implements SyncRunRepository {

    private static final String SELECT_RUN = """
            SELECT id, connector_id, started_at, finished_at, outcome,
                   workloads_read, observations_appended
            FROM sync_run
            """;

    private final JdbcClient jdbcClient;

    public SyncRunJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public SyncRun append(SyncRun run) {
        jdbcClient.sql("""
                        INSERT INTO sync_run (id, connector_id, started_at, finished_at, outcome,
                                              workloads_read, observations_appended)
                        VALUES (:id, :connectorId, :startedAt, :finishedAt, :outcome,
                                :workloadsRead, :observationsAppended)
                        """)
                .param("id", run.id().value())
                .param("connectorId", run.connectorId())
                .param("startedAt", Timestamp.from(run.startedAt()))
                .param("finishedAt", Timestamp.from(run.finishedAt()))
                .param("outcome", run.outcome().name())
                .param("workloadsRead", run.workloadsRead())
                .param("observationsAppended", run.observationsAppended())
                .update();

        for (int position = 0; position < run.unrecognized().size(); position++) {
            UnrecognizedWorkload workload = run.unrecognized().get(position);
            jdbcClient.sql("""
                            INSERT INTO sync_run_unrecognized
                                (sync_run_id, position, scope, name, image_reference, reason)
                            VALUES (:runId, :position, :scope, :name, :imageReference, :reason)
                            """)
                    .param("runId", run.id().value())
                    .param("position", position)
                    .param("scope", workload.scope())
                    .param("name", workload.name())
                    .param("imageReference", workload.imageReference())
                    .param("reason", workload.reason())
                    .update();
        }

        for (int position = 0; position < run.failures().size(); position++) {
            jdbcClient.sql("""
                            INSERT INTO sync_run_failure (sync_run_id, position, message)
                            VALUES (:runId, :position, :message)
                            """)
                    .param("runId", run.id().value())
                    .param("position", position)
                    .param("message", run.failures().get(position))
                    .update();
        }

        return run;
    }

    @Override
    public List<SyncRun> findRecent(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<Header> headers = jdbcClient.sql(
                        SELECT_RUN + " ORDER BY started_at DESC, id DESC FETCH FIRST :limit ROWS ONLY")
                .param("limit", limit)
                .query(SyncRunJdbcRepository::mapHeader)
                .list();
        return withChildren(headers);
    }

    @Override
    public Optional<SyncRun> findLatest(String connectorId) {
        return latestWhere("connector_id = :connectorId", connectorId, null);
    }

    @Override
    public Optional<SyncRun> findLatestSuccessful(String connectorId) {
        return latestWhere("connector_id = :connectorId AND outcome = :outcome",
                connectorId, SyncRun.Outcome.SUCCEEDED.name());
    }

    private Optional<SyncRun> latestWhere(String where, String connectorId, String outcome) {
        var spec = jdbcClient.sql(
                        SELECT_RUN + " WHERE " + where + " ORDER BY started_at DESC, id DESC FETCH FIRST 1 ROWS ONLY")
                .param("connectorId", connectorId);
        if (outcome != null) {
            spec = spec.param("outcome", outcome);
        }
        List<Header> headers = spec.query(SyncRunJdbcRepository::mapHeader).list();
        return withChildren(headers).stream().findFirst();
    }

    /**
     * Loads the child rows for each run.
     *
     * <p>One query per run rather than a join. A join would repeat every header column once per
     * unrecognized workload and once per failure, and reassembling two independent child lists from
     * that cross product is the kind of code that quietly returns duplicates. The history screen
     * reads a bounded number of runs, so the extra round trips are affordable and the mapping stays
     * obviously correct.
     */
    private List<SyncRun> withChildren(List<Header> headers) {
        List<SyncRun> runs = new ArrayList<>(headers.size());
        for (Header header : headers) {
            runs.add(new SyncRun(header.id(), header.connectorId(), header.startedAt(), header.finishedAt(),
                    header.outcome(), header.workloadsRead(), header.observationsAppended(),
                    unrecognizedOf(header.id()), failuresOf(header.id())));
        }
        return runs;
    }

    private List<UnrecognizedWorkload> unrecognizedOf(SyncRunId id) {
        return jdbcClient.sql("""
                        SELECT scope, name, image_reference, reason FROM sync_run_unrecognized
                        WHERE sync_run_id = :runId ORDER BY position
                        """)
                .param("runId", id.value())
                .query((ResultSet rs, int rowNum) -> new UnrecognizedWorkload(
                        rs.getString("scope"), rs.getString("name"),
                        rs.getString("image_reference"), rs.getString("reason")))
                .list();
    }

    private List<String> failuresOf(SyncRunId id) {
        return jdbcClient.sql("""
                        SELECT message FROM sync_run_failure
                        WHERE sync_run_id = :runId ORDER BY position
                        """)
                .param("runId", id.value())
                .query(String.class)
                .list();
    }

    private static Header mapHeader(ResultSet rs, int rowNum) throws SQLException {
        return new Header(
                new SyncRunId(rs.getObject("id", UUID.class)),
                rs.getString("connector_id"),
                instant(rs, "started_at"),
                instant(rs, "finished_at"),
                SyncRun.Outcome.valueOf(rs.getString("outcome")),
                rs.getInt("workloads_read"),
                rs.getInt("observations_appended"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    /** The run row on its own, before its child lists are attached. */
    private record Header(
            SyncRunId id, String connectorId, Instant startedAt, Instant finishedAt,
            SyncRun.Outcome outcome, int workloadsRead, int observationsAppended) {
    }
}
