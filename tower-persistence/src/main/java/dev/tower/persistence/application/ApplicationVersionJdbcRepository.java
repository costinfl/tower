package dev.tower.persistence.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.tower.application.port.out.ApplicationVersionRepository;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.application.ApplicationVersionId;

/**
 * {@link ApplicationVersionRepository} adapter backed by the {@code application_version} table
 * (V3__release_packs.sql, issue #20).
 *
 * <p>BR-01 makes an Application Version immutable, so {@link #save(ApplicationVersion)} only ever
 * inserts - there is no update path, matching the port it implements.
 */
@Repository
public class ApplicationVersionJdbcRepository implements ApplicationVersionRepository {

    private static final String SELECT = """
            SELECT id, application_id, version, branch, tag, commit_ref, build_identifier
            FROM application_version
            """;

    private final JdbcClient jdbcClient;

    public ApplicationVersionJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public ApplicationVersion save(ApplicationVersion version) {
        jdbcClient.sql("""
                INSERT INTO application_version
                    (id, application_id, version, branch, tag, commit_ref, build_identifier)
                VALUES (:id, :applicationId, :version, :branch, :tag, :commit, :buildIdentifier)
                """)
                .param("id", version.id().value())
                .param("applicationId", version.applicationId().value())
                .param("version", version.version())
                .param("branch", version.branch())
                .param("tag", version.tag())
                .param("commit", version.commit())
                .param("buildIdentifier", version.buildIdentifier())
                .update();
        return version;
    }

    @Override
    public Optional<ApplicationVersion> findById(ApplicationVersionId id) {
        return jdbcClient.sql(SELECT + " WHERE id = :id")
                .param("id", id.value())
                .query(ApplicationVersionJdbcRepository::mapRow)
                .optional();
    }

    @Override
    public List<ApplicationVersion> findAll() {
        return jdbcClient.sql(SELECT + " ORDER BY version")
                .query(ApplicationVersionJdbcRepository::mapRow)
                .list();
    }

    @Override
    public List<ApplicationVersion> findAllByApplication(ApplicationId applicationId) {
        return jdbcClient.sql(SELECT + " WHERE application_id = :applicationId ORDER BY version")
                .param("applicationId", applicationId.value())
                .query(ApplicationVersionJdbcRepository::mapRow)
                .list();
    }

    @Override
    public List<ApplicationVersion> findAllById(List<ApplicationVersionId> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<UUID> values = ids.stream().map(ApplicationVersionId::value).toList();
        return jdbcClient.sql(SELECT + " WHERE id IN (:ids) ORDER BY version")
                .param("ids", values)
                .query(ApplicationVersionJdbcRepository::mapRow)
                .list();
    }

    @Override
    public Optional<ApplicationVersion> findByApplicationAndVersion(ApplicationId applicationId, String version) {
        return jdbcClient.sql(SELECT + " WHERE application_id = :applicationId AND version = :version")
                .param("applicationId", applicationId.value())
                .param("version", version)
                .query(ApplicationVersionJdbcRepository::mapRow)
                .optional();
    }

    @Override
    public boolean existsByApplicationAndVersion(ApplicationId applicationId, String version) {
        Integer count = jdbcClient.sql("""
                SELECT COUNT(*) FROM application_version
                WHERE application_id = :applicationId AND version = :version
                """)
                .param("applicationId", applicationId.value())
                .param("version", version)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    public void deleteById(ApplicationVersionId id) {
        jdbcClient.sql("DELETE FROM application_version WHERE id = :id")
                .param("id", id.value())
                .update();
    }

    private static ApplicationVersion mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ApplicationVersion(
                new ApplicationVersionId(rs.getObject("id", UUID.class)),
                new ApplicationId(rs.getObject("application_id", UUID.class)),
                rs.getString("version"),
                rs.getString("branch"),
                rs.getString("tag"),
                rs.getString("commit_ref"),
                rs.getString("build_identifier"));
    }
}
