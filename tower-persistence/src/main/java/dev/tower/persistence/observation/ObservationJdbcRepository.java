package dev.tower.persistence.observation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.tower.application.port.out.ObservationRepository;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.observation.Observation;
import dev.tower.domain.observation.ObservationId;
import dev.tower.domain.observation.ObservationSource;

/**
 * {@link ObservationRepository} adapter backed by the {@code observation} table
 * (V4__observations.sql, Epic 3, issues #21 to #24).
 *
 * <p>ADR-002, BR-02 and FR-023 make an Observation immutable and append-only, so this class only
 * ever issues INSERT and SELECT - there is no update or delete method here at all, matching the
 * port it implements exactly.
 */
@Repository
public class ObservationJdbcRepository implements ObservationRepository {

    private static final String SELECT = """
            SELECT id, environment_id, application_id, application_version_id, observed_at,
                   source_collector, source_actor, source_origin_instance
            FROM observation
            """;

    private final JdbcClient jdbcClient;

    public ObservationJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Observation append(Observation observation) {
        jdbcClient.sql("""
                INSERT INTO observation
                    (id, environment_id, application_id, application_version_id, observed_at,
                     source_collector, source_actor, source_origin_instance)
                VALUES (:id, :environmentId, :applicationId, :applicationVersionId, :observedAt,
                        :sourceCollector, :sourceActor, :sourceOriginInstance)
                """)
                .param("id", observation.id().value())
                .param("environmentId", observation.environmentId().value())
                .param("applicationId", observation.applicationId().value())
                .param("applicationVersionId", observation.applicationVersionId().value())
                .param("observedAt", Timestamp.from(observation.observedAt()))
                .param("sourceCollector", observation.source().collector())
                .param("sourceActor", observation.source().actor())
                .param("sourceOriginInstance", observation.source().originInstance())
                .update();
        return observation;
    }

    @Override
    public Optional<Observation> findById(ObservationId id) {
        return jdbcClient.sql(SELECT + " WHERE id = :id")
                .param("id", id.value())
                .query(ObservationJdbcRepository::mapRow)
                .optional();
    }

    @Override
    public List<Observation> findAllInEnvironment(EnvironmentId environmentId) {
        return jdbcClient.sql(SELECT + " WHERE environment_id = :environmentId")
                .param("environmentId", environmentId.value())
                .query(ObservationJdbcRepository::mapRow)
                .list();
    }

    @Override
    public List<Observation> findAllOfVersions(Collection<ApplicationVersionId> versionIds) {
        if (versionIds == null || versionIds.isEmpty()) {
            return List.of();
        }
        List<UUID> values = versionIds.stream().map(ApplicationVersionId::value).toList();
        return jdbcClient.sql(SELECT + " WHERE application_version_id IN (:versionIds)")
                .param("versionIds", values)
                .query(ObservationJdbcRepository::mapRow)
                .list();
    }

    @Override
    public List<Observation> findAll() {
        return jdbcClient.sql(SELECT + " ORDER BY observed_at DESC")
                .query(ObservationJdbcRepository::mapRow)
                .list();
    }

    private static Observation mapRow(ResultSet rs, int rowNum) throws SQLException {
        ObservationSource source = new ObservationSource(
                rs.getString("source_collector"),
                rs.getString("source_actor"),
                rs.getString("source_origin_instance"));
        return new Observation(
                new ObservationId(rs.getObject("id", UUID.class)),
                new EnvironmentId(rs.getObject("environment_id", UUID.class)),
                new ApplicationId(rs.getObject("application_id", UUID.class)),
                new ApplicationVersionId(rs.getObject("application_version_id", UUID.class)),
                rs.getTimestamp("observed_at").toInstant(),
                source);
    }
}
