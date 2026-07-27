package dev.tower.persistence.environment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.tower.application.port.out.EnvironmentRepository;
import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.environment.Stage;

/**
 * {@link EnvironmentRepository} adapter backed by the {@code environment} table
 * (V2__environments_and_promotion_paths.sql).
 *
 * <p>Uses Spring's {@link JdbcClient} rather than JPA: {@link Environment} is an immutable record
 * with zero framework dependency (an architecture test enforces this), so there is nothing for an
 * ORM to map and no persistence annotation may appear on it.
 */
@Repository
public class EnvironmentJdbcRepository implements EnvironmentRepository {

    private final JdbcClient jdbcClient;

    public EnvironmentJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Environment save(Environment environment) {
        int updated = jdbcClient.sql("UPDATE environment SET name = :name, stage = :stage WHERE id = :id")
                .param("id", environment.id().value())
                .param("name", environment.name())
                .param("stage", environment.stage().name())
                .update();
        if (updated == 0) {
            jdbcClient.sql("INSERT INTO environment (id, name, stage) VALUES (:id, :name, :stage)")
                    .param("id", environment.id().value())
                    .param("name", environment.name())
                    .param("stage", environment.stage().name())
                    .update();
        }
        return environment;
    }

    @Override
    public Optional<Environment> findById(EnvironmentId id) {
        return jdbcClient.sql("SELECT id, name, stage FROM environment WHERE id = :id")
                .param("id", id.value())
                .query(EnvironmentJdbcRepository::mapRow)
                .optional();
    }

    @Override
    public List<Environment> findAll() {
        return jdbcClient.sql("SELECT id, name, stage FROM environment ORDER BY name")
                .query(EnvironmentJdbcRepository::mapRow)
                .list();
    }

    @Override
    public List<Environment> findAllById(List<EnvironmentId> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<UUID> values = ids.stream().map(EnvironmentId::value).toList();
        return jdbcClient.sql("SELECT id, name, stage FROM environment WHERE id IN (:ids) ORDER BY name")
                .param("ids", values)
                .query(EnvironmentJdbcRepository::mapRow)
                .list();
    }

    @Override
    public boolean existsByNameIgnoringCase(String name) {
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM environment WHERE UPPER(name) = UPPER(:name)")
                .param("name", name)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    public void deleteById(EnvironmentId id) {
        jdbcClient.sql("DELETE FROM environment WHERE id = :id")
                .param("id", id.value())
                .update();
    }

    private static Environment mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Environment(
                new EnvironmentId(rs.getObject("id", UUID.class)),
                rs.getString("name"),
                Stage.valueOf(rs.getString("stage")));
    }
}
