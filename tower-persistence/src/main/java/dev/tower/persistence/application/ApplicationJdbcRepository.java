package dev.tower.persistence.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.tower.application.port.out.ApplicationRepository;
import dev.tower.domain.application.Application;
import dev.tower.domain.application.ApplicationId;

/**
 * {@link ApplicationRepository} adapter backed by the {@code application} table
 * (V3__release_packs.sql, issue #20).
 *
 * <p>Uses Spring's {@link JdbcClient} rather than JPA, matching
 * {@code EnvironmentJdbcRepository} and {@code PromotionPathJdbcRepository}: {@link Application}
 * is an immutable record with zero framework dependency, so there is nothing for an ORM to map.
 */
@Repository
public class ApplicationJdbcRepository implements ApplicationRepository {

    private final JdbcClient jdbcClient;

    public ApplicationJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Application save(Application application) {
        int updated = jdbcClient.sql("""
                UPDATE application SET name = :name, description = :description WHERE id = :id
                """)
                .param("id", application.id().value())
                .param("name", application.name())
                .param("description", application.description())
                .update();
        if (updated == 0) {
            jdbcClient.sql("""
                    INSERT INTO application (id, name, description) VALUES (:id, :name, :description)
                    """)
                    .param("id", application.id().value())
                    .param("name", application.name())
                    .param("description", application.description())
                    .update();
        }
        return application;
    }

    @Override
    public Optional<Application> findById(ApplicationId id) {
        return jdbcClient.sql("SELECT id, name, description FROM application WHERE id = :id")
                .param("id", id.value())
                .query(ApplicationJdbcRepository::mapRow)
                .optional();
    }

    @Override
    public List<Application> findAll() {
        return jdbcClient.sql("SELECT id, name, description FROM application ORDER BY name")
                .query(ApplicationJdbcRepository::mapRow)
                .list();
    }

    @Override
    public boolean existsByNameIgnoringCase(String name) {
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM application WHERE UPPER(name) = UPPER(:name)")
                .param("name", name)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    public void deleteById(ApplicationId id) {
        jdbcClient.sql("DELETE FROM application WHERE id = :id")
                .param("id", id.value())
                .update();
    }

    private static Application mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Application(
                new ApplicationId(rs.getObject("id", UUID.class)),
                rs.getString("name"),
                rs.getString("description"));
    }
}
