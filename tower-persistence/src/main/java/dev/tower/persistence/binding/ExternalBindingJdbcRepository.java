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
import dev.tower.application.binding.IssueTrackerBinding;
import dev.tower.application.binding.RepositoryBinding;
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

    @Override
    public RepositoryBinding save(RepositoryBinding binding) {
        int updated = jdbcClient.sql("""
                        UPDATE repository_binding
                        SET repository_url = :repositoryUrl, ref_selection = :refSelection,
                            version_pattern = :versionPattern
                        WHERE application_id = :applicationId AND connector_id = :connectorId
                        """)
                .param("applicationId", binding.applicationId().value())
                .param("connectorId", binding.connectorId())
                .param("repositoryUrl", binding.repositoryUrl())
                .param("refSelection", binding.refSelection().name())
                .param("versionPattern", binding.versionPattern())
                .update();
        if (updated == 0) {
            jdbcClient.sql("""
                            INSERT INTO repository_binding
                                (application_id, connector_id, repository_url, ref_selection, version_pattern)
                            VALUES (:applicationId, :connectorId, :repositoryUrl, :refSelection, :versionPattern)
                            """)
                    .param("applicationId", binding.applicationId().value())
                    .param("connectorId", binding.connectorId())
                    .param("repositoryUrl", binding.repositoryUrl())
                    .param("refSelection", binding.refSelection().name())
                    .param("versionPattern", binding.versionPattern())
                    .update();
        }
        return binding;
    }

    @Override
    public Optional<RepositoryBinding> findRepositoryBinding(ApplicationId applicationId, String connectorId) {
        return jdbcClient.sql("""
                        SELECT application_id, connector_id, repository_url, ref_selection, version_pattern
                        FROM repository_binding
                        WHERE application_id = :applicationId AND connector_id = :connectorId
                        """)
                .param("applicationId", applicationId.value())
                .param("connectorId", connectorId)
                .query(ExternalBindingJdbcRepository::mapRepositoryBinding)
                .optional();
    }

    @Override
    public List<RepositoryBinding> findAllRepositoryBindings(String connectorId) {
        return jdbcClient.sql("""
                        SELECT application_id, connector_id, repository_url, ref_selection, version_pattern
                        FROM repository_binding WHERE connector_id = :connectorId ORDER BY repository_url
                        """)
                .param("connectorId", connectorId)
                .query(ExternalBindingJdbcRepository::mapRepositoryBinding)
                .list();
    }

    @Override
    public List<RepositoryBinding> findAllRepositoryBindings() {
        return jdbcClient.sql("""
                        SELECT application_id, connector_id, repository_url, ref_selection, version_pattern
                        FROM repository_binding ORDER BY connector_id, repository_url
                        """)
                .query(ExternalBindingJdbcRepository::mapRepositoryBinding)
                .list();
    }

    /**
     * Issue tracker bindings (ADR-018), keyed by Connector alone.
     *
     * <p>The primary key is the Connector, so "one tracker per Connector" is
     * enforced by the table rather than by a service that could be bypassed.
     */
    @Override
    public IssueTrackerBinding save(IssueTrackerBinding binding) {
        int updated = jdbcClient.sql("""
                        UPDATE issue_tracker_binding SET locator = :locator
                        WHERE connector_id = :connectorId
                        """)
                .param("connectorId", binding.connectorId())
                .param("locator", binding.locator())
                .update();
        if (updated == 0) {
            jdbcClient.sql("""
                            INSERT INTO issue_tracker_binding (connector_id, locator)
                            VALUES (:connectorId, :locator)
                            """)
                    .param("connectorId", binding.connectorId())
                    .param("locator", binding.locator())
                    .update();
        }
        return binding;
    }

    @Override
    public Optional<IssueTrackerBinding> findIssueTrackerBinding(String connectorId) {
        return jdbcClient.sql("""
                        SELECT connector_id, locator FROM issue_tracker_binding
                        WHERE connector_id = :connectorId
                        """)
                .param("connectorId", connectorId)
                .query(ExternalBindingJdbcRepository::mapIssueTrackerBinding)
                .optional();
    }

    @Override
    public List<IssueTrackerBinding> findAllIssueTrackerBindings() {
        return jdbcClient.sql("""
                        SELECT connector_id, locator FROM issue_tracker_binding ORDER BY connector_id
                        """)
                .query(ExternalBindingJdbcRepository::mapIssueTrackerBinding)
                .list();
    }

    @Override
    public void deleteIssueTrackerBinding(String connectorId) {
        jdbcClient.sql("DELETE FROM issue_tracker_binding WHERE connector_id = :connectorId")
                .param("connectorId", connectorId)
                .update();
    }

    private static IssueTrackerBinding mapIssueTrackerBinding(java.sql.ResultSet rs, int rowNum)
            throws java.sql.SQLException {
        return new IssueTrackerBinding(rs.getString("connector_id"), rs.getString("locator"));
    }

    @Override
    public void deleteRepositoryBinding(ApplicationId applicationId, String connectorId) {
        jdbcClient.sql("""
                        DELETE FROM repository_binding
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

    private static RepositoryBinding mapRepositoryBinding(ResultSet rs, int rowNum) throws SQLException {
        return new RepositoryBinding(
                new ApplicationId(rs.getObject("application_id", UUID.class)),
                rs.getString("connector_id"),
                rs.getString("repository_url"),
                // Parsed rather than valueOf'd, so a row written by a later version of Tower
                // fails with a message naming the value instead of an IllegalArgumentException.
                RepositoryBinding.RefSelection.parse(rs.getString("ref_selection")),
                rs.getString("version_pattern"));
    }
}
