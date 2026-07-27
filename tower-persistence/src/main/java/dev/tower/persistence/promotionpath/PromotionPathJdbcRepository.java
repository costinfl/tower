package dev.tower.persistence.promotionpath;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.tower.application.port.out.PromotionPathRepository;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.promotionpath.PromotionPath;
import dev.tower.domain.promotionpath.PromotionPathId;
import dev.tower.domain.promotionpath.PromotionPathVersion;

/**
 * {@link PromotionPathRepository} adapter backed by the {@code promotion_path},
 * {@code promotion_path_version} and {@code promotion_path_version_environment} tables
 * (V2__environments_and_promotion_paths.sql).
 *
 * <p>ADR-007: existing versions are immutable, so {@link #save(PromotionPath)} only ever inserts
 * versions the database does not already hold; it never updates or deletes a
 * {@code promotion_path_version} row.
 *
 * <p>ADR-005: the ordered Environment references for a version live in the join table
 * {@code promotion_path_version_environment}, keyed by an explicit {@code env_position} column.
 * Loading always re-applies {@code ORDER BY env_position} so the sequence a version was
 * published with is exactly the sequence returned - see
 * PromotionPathAdapterOrderingTest / PromotionPathAdapterSharedEnvironmentTest for the tests that
 * pin this down.
 */
@Repository
public class PromotionPathJdbcRepository implements PromotionPathRepository {

    private final JdbcClient jdbcClient;

    public PromotionPathJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public PromotionPath save(PromotionPath path) {
        upsertPathRow(path);

        Set<Integer> persistedVersions = new LinkedHashSet<>(jdbcClient
                .sql("SELECT version_number FROM promotion_path_version WHERE path_id = :id")
                .param("id", path.id().value())
                .query(Integer.class)
                .list());

        for (PromotionPathVersion version : path.versions()) {
            if (persistedVersions.contains(version.number())) {
                continue; // Already published; ADR-007 forbids mutating it.
            }
            insertVersion(path.id(), version);
        }
        return path;
    }

    private void upsertPathRow(PromotionPath path) {
        int updated = jdbcClient.sql("UPDATE promotion_path SET name = :name, archived = :archived WHERE id = :id")
                .param("id", path.id().value())
                .param("name", path.name())
                .param("archived", path.isArchived())
                .update();
        if (updated == 0) {
            jdbcClient.sql("INSERT INTO promotion_path (id, name, archived) VALUES (:id, :name, :archived)")
                    .param("id", path.id().value())
                    .param("name", path.name())
                    .param("archived", path.isArchived())
                    .update();
        }
    }

    private void insertVersion(PromotionPathId pathId, PromotionPathVersion version) {
        jdbcClient.sql("""
                INSERT INTO promotion_path_version (path_id, version_number, created_at)
                VALUES (:pathId, :versionNumber, :createdAt)
                """)
                .param("pathId", pathId.value())
                .param("versionNumber", version.number())
                .param("createdAt", Timestamp.from(version.createdAt()))
                .update();

        List<EnvironmentId> environments = version.environments();
        for (int position = 0; position < environments.size(); position++) {
            jdbcClient.sql("""
                    INSERT INTO promotion_path_version_environment
                        (path_id, version_number, env_position, environment_id)
                    VALUES (:pathId, :versionNumber, :position, :environmentId)
                    """)
                    .param("pathId", pathId.value())
                    .param("versionNumber", version.number())
                    .param("position", position)
                    .param("environmentId", environments.get(position).value())
                    .update();
        }
    }

    @Override
    public Optional<PromotionPath> findById(PromotionPathId id) {
        return findPathRow(id).map(row -> toDomain(row, loadVersions(id)));
    }

    @Override
    public List<PromotionPath> findAll() {
        List<PathRow> rows = jdbcClient.sql("SELECT id, name, archived FROM promotion_path ORDER BY name")
                .query(PromotionPathJdbcRepository::mapPathRow)
                .list();
        List<PromotionPath> paths = new ArrayList<>(rows.size());
        for (PathRow row : rows) {
            paths.add(toDomain(row, loadVersions(row.id())));
        }
        return paths;
    }

    @Override
    public boolean existsByNameIgnoringCase(String name) {
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM promotion_path WHERE UPPER(name) = UPPER(:name)")
                .param("name", name)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    public List<PromotionPath> findAllReferencing(EnvironmentId environment) {
        List<UUID> pathIds = jdbcClient.sql("""
                SELECT DISTINCT path_id
                FROM promotion_path_version_environment
                WHERE environment_id = :environmentId
                """)
                .param("environmentId", environment.value())
                .query(UUID.class)
                .list();

        List<PromotionPath> referencing = new ArrayList<>(pathIds.size());
        for (UUID pathId : pathIds) {
            findById(new PromotionPathId(pathId)).ifPresent(referencing::add);
        }
        return referencing.stream()
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteById(PromotionPathId id) {
        // ON DELETE CASCADE on promotion_path_version and promotion_path_version_environment
        // (V2 migration) removes the versions and Environment references together with the path.
        jdbcClient.sql("DELETE FROM promotion_path WHERE id = :id")
                .param("id", id.value())
                .update();
    }

    private Optional<PathRow> findPathRow(PromotionPathId id) {
        return jdbcClient.sql("SELECT id, name, archived FROM promotion_path WHERE id = :id")
                .param("id", id.value())
                .query(PromotionPathJdbcRepository::mapPathRow)
                .optional();
    }

    /**
     * Loads every version of a path together with its ordered Environment references.
     *
     * <p>Two queries rather than a single join: {@code promotion_path_version} rows carry
     * {@code created_at} even for a version whose environments query returns nothing (which
     * cannot happen given the NOT NULL/FK constraints, but keeps the mapping simple regardless),
     * and grouping the join rows by version_number in Java is simpler to get right than
     * reconstructing groups from a flattened join result set.
     */
    private List<PromotionPathVersion> loadVersions(PromotionPathId pathId) {
        List<VersionRow> versionRows = jdbcClient.sql("""
                SELECT version_number, created_at
                FROM promotion_path_version
                WHERE path_id = :pathId
                ORDER BY version_number
                """)
                .param("pathId", pathId.value())
                .query(PromotionPathJdbcRepository::mapVersionRow)
                .list();

        List<EnvRow> envRows = jdbcClient.sql("""
                SELECT version_number, env_position, environment_id
                FROM promotion_path_version_environment
                WHERE path_id = :pathId
                ORDER BY version_number, env_position
                """)
                .param("pathId", pathId.value())
                .query(PromotionPathJdbcRepository::mapEnvRow)
                .list();

        Map<Integer, List<EnvironmentId>> environmentsByVersion = new java.util.HashMap<>();
        for (EnvRow row : envRows) {
            // envRows is ordered by (version_number, env_position), so appending in iteration
            // order preserves the exact published sequence for each version (ADR-005 ordering).
            environmentsByVersion
                    .computeIfAbsent(row.versionNumber(), key -> new ArrayList<>())
                    .add(new EnvironmentId(row.environmentId()));
        }

        List<PromotionPathVersion> versions = new ArrayList<>(versionRows.size());
        for (VersionRow row : versionRows) {
            versions.add(new PromotionPathVersion(
                    row.number(),
                    environmentsByVersion.getOrDefault(row.number(), List.of()),
                    row.createdAt()));
        }
        return versions;
    }

    private static PromotionPath toDomain(PathRow row, List<PromotionPathVersion> versions) {
        return PromotionPath.reconstitute(row.id(), row.name(), versions, row.archived());
    }

    private static PathRow mapPathRow(ResultSet rs, int rowNum) throws SQLException {
        return new PathRow(
                new PromotionPathId(rs.getObject("id", UUID.class)),
                rs.getString("name"),
                rs.getBoolean("archived"));
    }

    private static VersionRow mapVersionRow(ResultSet rs, int rowNum) throws SQLException {
        return new VersionRow(rs.getInt("version_number"), rs.getTimestamp("created_at").toInstant());
    }

    private static EnvRow mapEnvRow(ResultSet rs, int rowNum) throws SQLException {
        return new EnvRow(
                rs.getInt("version_number"),
                rs.getInt("env_position"),
                rs.getObject("environment_id", UUID.class));
    }

    private record PathRow(PromotionPathId id, String name, boolean archived) {}

    private record VersionRow(int number, Instant createdAt) {}

    private record EnvRow(int versionNumber, int position, UUID environmentId) {}
}
