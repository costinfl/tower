package dev.tower.persistence.releasepack;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.tower.application.port.out.ReleasePackRepository;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.handover.Handover;
import dev.tower.domain.iteration.Iteration;
import dev.tower.domain.iteration.IterationId;
import dev.tower.domain.promotionpath.PromotionPathId;
import dev.tower.domain.releasepack.PackedVersion;
import dev.tower.domain.releasepack.PromotionPathAssignment;
import dev.tower.domain.releasepack.ReleasePack;
import dev.tower.domain.releasepack.ReleasePackId;

/**
 * {@link ReleasePackRepository} adapter backed by the {@code release_pack},
 * {@code release_pack_version}, {@code release_pack_handover} and {@code release_pack_iteration}
 * tables (V3__release_packs.sql, issue #20).
 *
 * <p>Unlike a Promotion Path version (ADR-007, append-only and immutable once published), a
 * Release Pack's contents, Handover and Iterations are all freely mutable in place. {@link
 * #save(ReleasePack)} therefore replaces the child rows wholesale on every save - delete what is
 * there, insert what the aggregate currently holds - rather than diffing, which keeps the mapping
 * simple and is cheap at the scale a single local Tower instance operates at (ADR-009).
 *
 * <p>ADR-007's pinning guarantee is expressed by storing only the assigned Promotion Path
 * <em>version number</em> on the pack row, never a live reference to "the path's current
 * version": publishing a new version of that path never touches this row.
 */
@Repository
public class ReleasePackJdbcRepository implements ReleasePackRepository {

    private final JdbcClient jdbcClient;

    public ReleasePackJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public ReleasePack save(ReleasePack pack) {
        upsertPackRow(pack);
        replaceContents(pack);
        upsertHandover(pack);
        replaceIterations(pack);
        return pack;
    }

    private void upsertPackRow(ReleasePack pack) {
        Optional<PromotionPathAssignment> assignment = pack.promotionPath();
        UUID pathId = assignment.map(a -> a.pathId().value()).orElse(null);
        Integer versionNumber = assignment.map(PromotionPathAssignment::versionNumber).orElse(null);

        int updated = jdbcClient.sql("""
                UPDATE release_pack
                SET name = :name, description = :description, promotion_path_id = :pathId,
                    promotion_path_version = :versionNumber, archived = :archived
                WHERE id = :id
                """)
                .param("id", pack.id().value())
                .param("name", pack.name())
                .param("description", pack.description())
                .param("pathId", pathId)
                .param("versionNumber", versionNumber)
                .param("archived", pack.isArchived())
                .update();
        if (updated == 0) {
            jdbcClient.sql("""
                    INSERT INTO release_pack
                        (id, name, description, promotion_path_id, promotion_path_version, archived)
                    VALUES (:id, :name, :description, :pathId, :versionNumber, :archived)
                    """)
                    .param("id", pack.id().value())
                    .param("name", pack.name())
                    .param("description", pack.description())
                    .param("pathId", pathId)
                    .param("versionNumber", versionNumber)
                    .param("archived", pack.isArchived())
                    .update();
        }
    }

    private void replaceContents(ReleasePack pack) {
        jdbcClient.sql("DELETE FROM release_pack_version WHERE release_pack_id = :packId")
                .param("packId", pack.id().value())
                .update();
        for (PackedVersion entry : pack.contents()) {
            jdbcClient.sql("""
                    INSERT INTO release_pack_version (release_pack_id, application_id, application_version_id)
                    VALUES (:packId, :applicationId, :versionId)
                    """)
                    .param("packId", pack.id().value())
                    .param("applicationId", entry.applicationId().value())
                    .param("versionId", entry.versionId().value())
                    .update();
        }
    }

    private void upsertHandover(ReleasePack pack) {
        Handover handover = pack.handover();
        int updated = jdbcClient.sql("""
                UPDATE release_pack_handover
                SET deployment_instructions = :deploymentInstructions, shell_commands = :shellCommands,
                    database_migrations = :databaseMigrations, rollback_procedure = :rollbackProcedure,
                    validation_notes = :validationNotes, operational_notes = :operationalNotes
                WHERE release_pack_id = :packId
                """)
                .param("packId", pack.id().value())
                .param("deploymentInstructions", handover.deploymentInstructions())
                .param("shellCommands", handover.shellCommands())
                .param("databaseMigrations", handover.databaseMigrations())
                .param("rollbackProcedure", handover.rollbackProcedure())
                .param("validationNotes", handover.validationNotes())
                .param("operationalNotes", handover.operationalNotes())
                .update();
        if (updated == 0) {
            jdbcClient.sql("""
                    INSERT INTO release_pack_handover
                        (release_pack_id, deployment_instructions, shell_commands, database_migrations,
                         rollback_procedure, validation_notes, operational_notes)
                    VALUES (:packId, :deploymentInstructions, :shellCommands, :databaseMigrations,
                            :rollbackProcedure, :validationNotes, :operationalNotes)
                    """)
                    .param("packId", pack.id().value())
                    .param("deploymentInstructions", handover.deploymentInstructions())
                    .param("shellCommands", handover.shellCommands())
                    .param("databaseMigrations", handover.databaseMigrations())
                    .param("rollbackProcedure", handover.rollbackProcedure())
                    .param("validationNotes", handover.validationNotes())
                    .param("operationalNotes", handover.operationalNotes())
                    .update();
        }
    }

    private void replaceIterations(ReleasePack pack) {
        jdbcClient.sql("DELETE FROM release_pack_iteration WHERE release_pack_id = :packId")
                .param("packId", pack.id().value())
                .update();
        for (Iteration iteration : pack.iterations()) {
            jdbcClient.sql("""
                    INSERT INTO release_pack_iteration
                        (id, release_pack_id, name, started_at, completed_at, notes)
                    VALUES (:id, :packId, :name, :startedAt, :completedAt, :notes)
                    """)
                    .param("id", iteration.id().value())
                    .param("packId", pack.id().value())
                    .param("name", iteration.name())
                    .param("startedAt", Timestamp.from(iteration.startedAt()))
                    .param("completedAt", iteration.completedAt() == null ? null : Timestamp.from(iteration.completedAt()))
                    .param("notes", iteration.notes())
                    .update();
        }
    }

    @Override
    public Optional<ReleasePack> findById(ReleasePackId id) {
        return findPackRow(id).map(this::toDomain);
    }

    @Override
    public List<ReleasePack> findAll() {
        List<PackRow> rows = jdbcClient.sql("""
                SELECT id, name, description, promotion_path_id, promotion_path_version, archived
                FROM release_pack ORDER BY name
                """)
                .query(ReleasePackJdbcRepository::mapPackRow)
                .list();
        List<ReleasePack> packs = new ArrayList<>(rows.size());
        for (PackRow row : rows) {
            packs.add(toDomain(row));
        }
        return packs;
    }

    @Override
    public boolean existsByNameIgnoringCase(String name) {
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM release_pack WHERE UPPER(name) = UPPER(:name)")
                .param("name", name)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    public List<ReleasePack> findAllContaining(ApplicationVersionId versionId) {
        List<UUID> packIds = jdbcClient.sql("""
                SELECT DISTINCT release_pack_id FROM release_pack_version WHERE application_version_id = :versionId
                """)
                .param("versionId", versionId.value())
                .query(UUID.class)
                .list();
        return loadAndSortByName(packIds);
    }

    @Override
    public List<ReleasePack> findAllReferencingPromotionPath(PromotionPathId pathId) {
        List<UUID> packIds = jdbcClient.sql("SELECT id FROM release_pack WHERE promotion_path_id = :pathId")
                .param("pathId", pathId.value())
                .query(UUID.class)
                .list();
        return loadAndSortByName(packIds);
    }

    private List<ReleasePack> loadAndSortByName(List<UUID> packIds) {
        List<ReleasePack> found = new ArrayList<>(packIds.size());
        for (UUID packId : packIds) {
            findById(new ReleasePackId(packId)).ifPresent(found::add);
        }
        return found.stream()
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteById(ReleasePackId id) {
        // ON DELETE CASCADE on release_pack_version, release_pack_handover and
        // release_pack_iteration (V3 migration) removes the pack's contents, Handover and
        // Iterations together with the pack itself.
        jdbcClient.sql("DELETE FROM release_pack WHERE id = :id")
                .param("id", id.value())
                .update();
    }

    private Optional<PackRow> findPackRow(ReleasePackId id) {
        return jdbcClient.sql("""
                SELECT id, name, description, promotion_path_id, promotion_path_version, archived
                FROM release_pack WHERE id = :id
                """)
                .param("id", id.value())
                .query(ReleasePackJdbcRepository::mapPackRow)
                .optional();
    }

    private ReleasePack toDomain(PackRow row) {
        return ReleasePack.reconstitute(row.id(), row.name(), row.description(), row.promotionPath(),
                loadContents(row.id()), loadHandover(row.id()), loadIterations(row.id()), row.archived());
    }

    private List<PackedVersion> loadContents(ReleasePackId packId) {
        return jdbcClient.sql("""
                SELECT application_id, application_version_id FROM release_pack_version
                WHERE release_pack_id = :packId
                """)
                .param("packId", packId.value())
                .query((rs, rowNum) -> new PackedVersion(
                        new ApplicationId(rs.getObject("application_id", UUID.class)),
                        new ApplicationVersionId(rs.getObject("application_version_id", UUID.class))))
                .list();
    }

    private Handover loadHandover(ReleasePackId packId) {
        return jdbcClient.sql("""
                SELECT deployment_instructions, shell_commands, database_migrations, rollback_procedure,
                       validation_notes, operational_notes
                FROM release_pack_handover WHERE release_pack_id = :packId
                """)
                .param("packId", packId.value())
                .query((rs, rowNum) -> new Handover(
                        rs.getString("deployment_instructions"),
                        rs.getString("shell_commands"),
                        rs.getString("database_migrations"),
                        rs.getString("rollback_procedure"),
                        rs.getString("validation_notes"),
                        rs.getString("operational_notes")))
                .optional()
                .orElseGet(Handover::empty);
    }

    private List<Iteration> loadIterations(ReleasePackId packId) {
        return jdbcClient.sql("""
                SELECT id, name, started_at, completed_at, notes FROM release_pack_iteration
                WHERE release_pack_id = :packId ORDER BY started_at
                """)
                .param("packId", packId.value())
                .query((rs, rowNum) -> new Iteration(
                        new IterationId(rs.getObject("id", UUID.class)),
                        rs.getString("name"),
                        rs.getTimestamp("started_at").toInstant(),
                        rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant(),
                        rs.getString("notes")))
                .list();
    }

    private static PackRow mapPackRow(ResultSet rs, int rowNum) throws SQLException {
        UUID pathId = rs.getObject("promotion_path_id", UUID.class);
        Integer versionNumber = (Integer) rs.getObject("promotion_path_version");
        PromotionPathAssignment assignment = pathId == null ? null
                : new PromotionPathAssignment(new PromotionPathId(pathId), versionNumber);
        return new PackRow(
                new ReleasePackId(rs.getObject("id", UUID.class)),
                rs.getString("name"),
                rs.getString("description"),
                assignment,
                rs.getBoolean("archived"));
    }

    private record PackRow(ReleasePackId id, String name, String description,
                           PromotionPathAssignment promotionPath, boolean archived) {}
}
