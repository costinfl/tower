package dev.tower.persistence.handover;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.tower.application.port.out.HandoverRevisionRepository;
import dev.tower.domain.handover.Handover;
import dev.tower.domain.handover.HandoverRevision;
import dev.tower.domain.handover.HandoverRevision.HandoverRevisionId;
import dev.tower.domain.releasepack.ReleasePackId;

/**
 * {@link HandoverRevisionRepository} adapter backed by the {@code handover_revision} table
 * (V9__handover_revisions.sql).
 *
 * <p>Insert only. There is no update and no delete here because the port offers neither, and the
 * port offers neither because a revision records what a team was told (ADR-016). This adapter is
 * the last place that rule could be quietly broken, so it is worth saying plainly: nothing below
 * writes an UPDATE or a DELETE.
 */
@Repository
public class HandoverRevisionJdbcRepository implements HandoverRevisionRepository {

    private final JdbcClient jdbcClient;

    public HandoverRevisionJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public HandoverRevision append(HandoverRevision revision) {
        jdbcClient.sql("""
                        INSERT INTO handover_revision
                            (id, release_pack_id, revision_number, deployment_instructions, shell_commands,
                             database_migrations, rollback_procedure, validation_notes, operational_notes,
                             recorded_at)
                        VALUES (:id, :releasePackId, :revisionNumber, :deploymentInstructions, :shellCommands,
                                :databaseMigrations, :rollbackProcedure, :validationNotes, :operationalNotes,
                                :recordedAt)
                        """)
                .param("id", revision.id().value())
                .param("releasePackId", revision.releasePackId().value())
                .param("revisionNumber", revision.revisionNumber())
                .param("deploymentInstructions", revision.handover().deploymentInstructions())
                .param("shellCommands", revision.handover().shellCommands())
                .param("databaseMigrations", revision.handover().databaseMigrations())
                .param("rollbackProcedure", revision.handover().rollbackProcedure())
                .param("validationNotes", revision.handover().validationNotes())
                .param("operationalNotes", revision.handover().operationalNotes())
                .param("recordedAt", Timestamp.from(revision.recordedAt()))
                .update();
        return revision;
    }

    @Override
    public List<HandoverRevision> findAllByReleasePack(ReleasePackId releasePackId) {
        return jdbcClient.sql("""
                        SELECT * FROM handover_revision
                        WHERE release_pack_id = :releasePackId
                        ORDER BY revision_number DESC
                        """)
                .param("releasePackId", releasePackId.value())
                .query(HandoverRevisionJdbcRepository::map)
                .list();
    }

    @Override
    public Optional<HandoverRevision> findByReleasePackAndNumber(
            ReleasePackId releasePackId, int revisionNumber) {
        return jdbcClient.sql("""
                        SELECT * FROM handover_revision
                        WHERE release_pack_id = :releasePackId AND revision_number = :revisionNumber
                        """)
                .param("releasePackId", releasePackId.value())
                .param("revisionNumber", revisionNumber)
                .query(HandoverRevisionJdbcRepository::map)
                .optional();
    }

    @Override
    public int highestRevisionNumber(ReleasePackId releasePackId) {
        // COALESCE rather than a null-returning MAX, so a Release Pack whose Handover has never
        // been edited answers 0 and the first revision becomes 1 without a special case.
        return jdbcClient.sql("""
                        SELECT COALESCE(MAX(revision_number), 0) FROM handover_revision
                        WHERE release_pack_id = :releasePackId
                        """)
                .param("releasePackId", releasePackId.value())
                .query(Integer.class)
                .single();
    }

    private static HandoverRevision map(ResultSet rs, int rowNum) throws SQLException {
        return new HandoverRevision(
                new HandoverRevisionId(rs.getObject("id", UUID.class)),
                new ReleasePackId(rs.getObject("release_pack_id", UUID.class)),
                rs.getInt("revision_number"),
                new Handover(
                        rs.getString("deployment_instructions"),
                        rs.getString("shell_commands"),
                        rs.getString("database_migrations"),
                        rs.getString("rollback_procedure"),
                        rs.getString("validation_notes"),
                        rs.getString("operational_notes")),
                rs.getTimestamp("recorded_at").toInstant());
    }
}
