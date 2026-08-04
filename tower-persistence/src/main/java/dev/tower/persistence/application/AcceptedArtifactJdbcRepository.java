package dev.tower.persistence.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.tower.application.port.out.AcceptedArtifactRepository;
import dev.tower.domain.application.AcceptedArtifact;
import dev.tower.domain.application.ApplicationVersionId;

/**
 * {@link AcceptedArtifactRepository} adapter backed by the
 * {@code accepted_artifact} table (V15__accepted_artifacts.sql).
 *
 * <p>Save is an update-then-insert, matching the binding adapters. The table's
 * primary key is the version and the kind together, so accepting a newer digest
 * for the same kind replaces the previous one rather than accumulating rows —
 * which is the design ADR-021 asks for, because what a document prints is the
 * digest somebody stands behind rather than the sequence of times they looked.
 */
@Repository
public class AcceptedArtifactJdbcRepository implements AcceptedArtifactRepository {

    private static final String SELECT = """
            SELECT application_version_id, kind, coordinate, digest, accepted_at
            FROM accepted_artifact
            """;

    private final JdbcClient jdbcClient;

    public AcceptedArtifactJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public AcceptedArtifact save(AcceptedArtifact accepted) {
        int updated = jdbcClient.sql("""
                        UPDATE accepted_artifact
                        SET coordinate = :coordinate, digest = :digest, accepted_at = :acceptedAt
                        WHERE application_version_id = :versionId AND kind = :kind
                        """)
                .params(parametersOf(accepted))
                .update();
        if (updated == 0) {
            jdbcClient.sql("""
                            INSERT INTO accepted_artifact
                                (application_version_id, kind, coordinate, digest, accepted_at)
                            VALUES (:versionId, :kind, :coordinate, :digest, :acceptedAt)
                            """)
                    .params(parametersOf(accepted))
                    .update();
        }
        return accepted;
    }

    private static java.util.Map<String, Object> parametersOf(AcceptedArtifact accepted) {
        return java.util.Map.of(
                "versionId", accepted.applicationVersionId().value(),
                "kind", accepted.kind(),
                "coordinate", accepted.coordinate(),
                "digest", accepted.digest(),
                "acceptedAt", Timestamp.from(accepted.acceptedAt()));
    }

    @Override
    public Optional<AcceptedArtifact> find(ApplicationVersionId applicationVersionId, String kind) {
        return jdbcClient.sql(SELECT + """
                        WHERE application_version_id = :versionId AND kind = :kind
                        """)
                .param("versionId", applicationVersionId.value())
                // Normalised on the way in, so a lookup for "Image" finds the
                // row accepted as "image".
                .param("kind", AcceptedArtifact.normaliseKind(kind))
                .query(AcceptedArtifactJdbcRepository::map)
                .optional();
    }

    @Override
    public List<AcceptedArtifact> findAllFor(ApplicationVersionId applicationVersionId) {
        return jdbcClient.sql(SELECT + " WHERE application_version_id = :versionId ORDER BY kind")
                .param("versionId", applicationVersionId.value())
                .query(AcceptedArtifactJdbcRepository::map)
                .list();
    }

    @Override
    public List<AcceptedArtifact> findAllFor(List<ApplicationVersionId> applicationVersionIds) {
        if (applicationVersionIds.isEmpty()) {
            // An IN clause with an empty list is a syntax error in some dialects
            // and matches everything in others. Neither is what "no versions"
            // means (ADR-009).
            return List.of();
        }
        return jdbcClient.sql(SELECT + """
                        WHERE application_version_id IN (:versionIds)
                        ORDER BY application_version_id, kind
                        """)
                .param("versionIds", applicationVersionIds.stream()
                        .map(ApplicationVersionId::value).toList())
                .query(AcceptedArtifactJdbcRepository::map)
                .list();
    }

    @Override
    public void delete(ApplicationVersionId applicationVersionId, String kind) {
        jdbcClient.sql("""
                        DELETE FROM accepted_artifact
                        WHERE application_version_id = :versionId AND kind = :kind
                        """)
                .param("versionId", applicationVersionId.value())
                .param("kind", AcceptedArtifact.normaliseKind(kind))
                .update();
    }

    private static AcceptedArtifact map(ResultSet rs, int rowNum) throws SQLException {
        return new AcceptedArtifact(
                new ApplicationVersionId(rs.getObject("application_version_id", UUID.class)),
                rs.getString("kind"),
                rs.getString("coordinate"),
                rs.getString("digest"),
                rs.getTimestamp("accepted_at").toInstant());
    }
}
