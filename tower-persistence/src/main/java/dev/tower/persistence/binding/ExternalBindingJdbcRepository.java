package dev.tower.persistence.binding;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.tower.application.binding.ApplicationBinding;
import dev.tower.application.binding.EnvironmentBinding;
import dev.tower.application.port.out.ExternalBindingRepository;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.environment.EnvironmentId;

/**
 * {@link ExternalBindingRepository} adapter backed by the {@code environment_binding} and
 * {@code application_binding} tables (V5__external_bindings.sql).
 *
 * <p>Uses {@link JdbcClient} for the same reason the other adapters do: the types it stores are
 * immutable records with no framework dependency, so there is nothing for an ORM to map.
 *
 * <p>Save is an update-then-insert, matching {@code EnvironmentJdbcRepository}. Both tables use a
 * composite primary key of the Tower concept and the Connector, so this replaces a binding for the
 * same pair rather than accumulating rows.
 */
@Repository
public class ExternalBindingJdbcRepository implements ExternalBindingRepository {

    private final JdbcClient jdbcClient;

    public ExternalBindingJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public EnvironmentBinding save(EnvironmentBinding binding) {
        int updated = jdbcClient.sql("""
                        UPDATE environment_binding SET target = :target, scope = :scope
                        WHERE environment_id = :environmentId AND connector_id = :connectorId
                        """)
                .param("environmentId", binding.environmentId().value())
                .param("connectorId", binding.connectorId())
                .param("target", binding.target())
                .param("scope", binding.scope())
                .update();
        if (updated == 0) {
            jdbcClient.sql("""
                            INSERT INTO environment_binding (environment_id, connector_id, target, scope)
                            VALUES (:environmentId, :connectorId, :target, :scope)
                            """)
                    .param("environmentId", binding.environmentId().value())
                    .param("connectorId", binding.connectorId())
                    .param("target", binding.target())
                    .param("scope", binding.scope())
                    .update();
        }
        return binding;
    }

    @Override
    public Optional<EnvironmentBinding> findEnvironmentBinding(EnvironmentId environmentId, String connectorId) {
        return jdbcClient.sql("""
                        SELECT environment_id, connector_id, target, scope FROM environment_binding
                        WHERE environment_id = :environmentId AND connector_id = :connectorId
                        """)
                .param("environmentId", environmentId.value())
                .param("connectorId", connectorId)
                .query(ExternalBindingJdbcRepository::mapEnvironmentBinding)
                .optional();
    }

    @Override
    public List<EnvironmentBinding> findAllEnvironmentBindings(String connectorId) {
        return jdbcClient.sql("""
                        SELECT environment_id, connector_id, target, scope FROM environment_binding
                        WHERE connector_id = :connectorId ORDER BY target, scope
                        """)
                .param("connectorId", connectorId)
                .query(ExternalBindingJdbcRepository::mapEnvironmentBinding)
                .list();
    }

    @Override
    public List<EnvironmentBinding> findAllEnvironmentBindings() {
        return jdbcClient.sql("""
                        SELECT environment_id, connector_id, target, scope FROM environment_binding
                        ORDER BY connector_id, target, scope
                        """)
                .query(ExternalBindingJdbcRepository::mapEnvironmentBinding)
                .list();
    }

    @Override
    public void deleteEnvironmentBinding(EnvironmentId environmentId, String connectorId) {
        jdbcClient.sql("""
                        DELETE FROM environment_binding
                        WHERE environment_id = :environmentId AND connector_id = :connectorId
                        """)
                .param("environmentId", environmentId.value())
                .param("connectorId", connectorId)
                .update();
    }

    @Override
    public ApplicationBinding save(ApplicationBinding binding) {
        int updated = jdbcClient.sql("""
                        UPDATE application_binding SET image = :image, version_pattern = :versionPattern
                        WHERE application_id = :applicationId AND connector_id = :connectorId
                        """)
                .param("applicationId", binding.applicationId().value())
                .param("connectorId", binding.connectorId())
                .param("image", binding.image())
                .param("versionPattern", binding.versionPattern())
                .update();
        if (updated == 0) {
            jdbcClient.sql("""
                            INSERT INTO application_binding (application_id, connector_id, image, version_pattern)
                            VALUES (:applicationId, :connectorId, :image, :versionPattern)
                            """)
                    .param("applicationId", binding.applicationId().value())
                    .param("connectorId", binding.connectorId())
                    .param("image", binding.image())
                    .param("versionPattern", binding.versionPattern())
                    .update();
        }
        return binding;
    }

    @Override
    public Optional<ApplicationBinding> findApplicationBinding(ApplicationId applicationId, String connectorId) {
        return jdbcClient.sql("""
                        SELECT application_id, connector_id, image, version_pattern FROM application_binding
                        WHERE application_id = :applicationId AND connector_id = :connectorId
                        """)
                .param("applicationId", applicationId.value())
                .param("connectorId", connectorId)
                .query(ExternalBindingJdbcRepository::mapApplicationBinding)
                .optional();
    }

    @Override
    public List<ApplicationBinding> findAllApplicationBindings(String connectorId) {
        return jdbcClient.sql("""
                        SELECT application_id, connector_id, image, version_pattern FROM application_binding
                        WHERE connector_id = :connectorId ORDER BY image
                        """)
                .param("connectorId", connectorId)
                .query(ExternalBindingJdbcRepository::mapApplicationBinding)
                .list();
    }

    @Override
    public List<ApplicationBinding> findAllApplicationBindings() {
        return jdbcClient.sql("""
                        SELECT application_id, connector_id, image, version_pattern FROM application_binding
                        ORDER BY connector_id, image
                        """)
                .query(ExternalBindingJdbcRepository::mapApplicationBinding)
                .list();
    }

    @Override
    public void deleteApplicationBinding(ApplicationId applicationId, String connectorId) {
        jdbcClient.sql("""
                        DELETE FROM application_binding
                        WHERE application_id = :applicationId AND connector_id = :connectorId
                        """)
                .param("applicationId", applicationId.value())
                .param("connectorId", connectorId)
                .update();
    }

    private static EnvironmentBinding mapEnvironmentBinding(ResultSet rs, int rowNum) throws SQLException {
        return new EnvironmentBinding(
                new EnvironmentId(rs.getObject("environment_id", UUID.class)),
                rs.getString("connector_id"),
                rs.getString("target"),
                rs.getString("scope"));
    }

    private static ApplicationBinding mapApplicationBinding(ResultSet rs, int rowNum) throws SQLException {
        return new ApplicationBinding(
                new ApplicationId(rs.getObject("application_id", UUID.class)),
                rs.getString("connector_id"),
                rs.getString("image"),
                rs.getString("version_pattern"));
    }
}
