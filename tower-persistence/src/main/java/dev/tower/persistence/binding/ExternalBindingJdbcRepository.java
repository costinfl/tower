package dev.tower.persistence.binding;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.tower.application.binding.ApplicationBinding;
import dev.tower.application.binding.ArtifactCoordinateBinding;
import dev.tower.application.binding.BuildJobBinding;
import dev.tower.application.binding.EnvironmentBinding;
import dev.tower.application.binding.IssueTrackerBinding;
import dev.tower.application.binding.PipelineJobBinding;
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

    /**
     * Pipeline job bindings (ADR-020), keyed by Environment, Application and
     * Connector together.
     *
     * <p>Three columns in the key rather than two, because a run of a deployment
     * job asserts both ends: this Application arrived in that Environment. The
     * table enforces one job per such pair, which is not fussiness — two jobs
     * claiming the same pair would both claim to say when it arrived, and nothing
     * would say which to believe.
     */
    @Override
    public PipelineJobBinding save(PipelineJobBinding binding) {
        int updated = jdbcClient.sql("""
                        UPDATE pipeline_job_binding
                        SET ci_system = :system, job = :job, version_source = :versionSource,
                            version_key = :versionKey, version_pattern = :versionPattern
                        WHERE environment_id = :environmentId AND application_id = :applicationId
                          AND connector_id = :connectorId
                        """)
                .params(parametersOf(binding))
                .update();
        if (updated == 0) {
            jdbcClient.sql("""
                            INSERT INTO pipeline_job_binding (
                                environment_id, application_id, connector_id, ci_system, job,
                                version_source, version_key, version_pattern)
                            VALUES (:environmentId, :applicationId, :connectorId, :system, :job,
                                    :versionSource, :versionKey, :versionPattern)
                            """)
                    .params(parametersOf(binding))
                    .update();
        }
        return binding;
    }

    private static java.util.Map<String, Object> parametersOf(PipelineJobBinding binding) {
        return java.util.Map.of(
                "environmentId", binding.environmentId().value(),
                "applicationId", binding.applicationId().value(),
                "connectorId", binding.connectorId(),
                "system", binding.system(),
                "job", binding.job(),
                "versionSource", binding.versionSource().name(),
                "versionKey", binding.versionKey(),
                "versionPattern", binding.versionPattern());
    }

    @Override
    public Optional<PipelineJobBinding> findPipelineJobBinding(
            EnvironmentId environmentId, ApplicationId applicationId, String connectorId) {
        return jdbcClient.sql(PIPELINE_JOB_COLUMNS + """
                        WHERE environment_id = :environmentId AND application_id = :applicationId
                          AND connector_id = :connectorId
                        """)
                .param("environmentId", environmentId.value())
                .param("applicationId", applicationId.value())
                .param("connectorId", connectorId)
                .query(ExternalBindingJdbcRepository::mapPipelineJobBinding)
                .optional();
    }

    @Override
    public List<PipelineJobBinding> findAllPipelineJobBindings(String connectorId) {
        return jdbcClient.sql(PIPELINE_JOB_COLUMNS + """
                        WHERE connector_id = :connectorId ORDER BY job
                        """)
                .param("connectorId", connectorId)
                .query(ExternalBindingJdbcRepository::mapPipelineJobBinding)
                .list();
    }

    @Override
    public List<PipelineJobBinding> findAllPipelineJobBindings() {
        return jdbcClient.sql(PIPELINE_JOB_COLUMNS + " ORDER BY connector_id, job")
                .query(ExternalBindingJdbcRepository::mapPipelineJobBinding)
                .list();
    }

    @Override
    public void deletePipelineJobBinding(
            EnvironmentId environmentId, ApplicationId applicationId, String connectorId) {
        jdbcClient.sql("""
                        DELETE FROM pipeline_job_binding
                        WHERE environment_id = :environmentId AND application_id = :applicationId
                          AND connector_id = :connectorId
                        """)
                .param("environmentId", environmentId.value())
                .param("applicationId", applicationId.value())
                .param("connectorId", connectorId)
                .update();
    }

    /** Every column, once, so the three reads above cannot drift apart. */
    private static final String PIPELINE_JOB_COLUMNS = """
            SELECT environment_id, application_id, connector_id, ci_system, job,
                   version_source, version_key, version_pattern
            FROM pipeline_job_binding
            """;

    private static PipelineJobBinding mapPipelineJobBinding(java.sql.ResultSet rs, int rowNum)
            throws java.sql.SQLException {
        return new PipelineJobBinding(
                EnvironmentId.of(rs.getString("environment_id")),
                ApplicationId.of(rs.getString("application_id")),
                rs.getString("connector_id"),
                rs.getString("ci_system"),
                rs.getString("job"),
                PipelineJobBinding.VersionSource.parse(rs.getString("version_source")),
                rs.getString("version_key"),
                rs.getString("version_pattern"));
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

    /**
     * Artifact coordinate bindings (ADR-021), keyed by Application, Connector and
     * kind (V14__artifact_coordinate_bindings.sql).
     *
     * <p>The only binding here with more than one row per Application and
     * Connector. A build publishes an image and a chart, and nothing but the kind
     * tells the two templates apart, so the kind is in the key rather than being
     * a field a second row would overwrite.
     */
    @Override
    public ArtifactCoordinateBinding save(ArtifactCoordinateBinding binding) {
        int updated = jdbcClient.sql("""
                        UPDATE artifact_coordinate_binding
                        SET repository_system = :system, coordinate_template = :template,
                            short_commit_length = :shortCommitLength
                        WHERE application_id = :applicationId AND connector_id = :connectorId
                          AND kind = :kind
                        """)
                .params(parametersOf(binding))
                .update();
        if (updated == 0) {
            jdbcClient.sql("""
                            INSERT INTO artifact_coordinate_binding (
                                application_id, connector_id, kind, repository_system,
                                coordinate_template, short_commit_length)
                            VALUES (:applicationId, :connectorId, :kind, :system,
                                    :template, :shortCommitLength)
                            """)
                    .params(parametersOf(binding))
                    .update();
        }
        return binding;
    }

    private static java.util.Map<String, Object> parametersOf(ArtifactCoordinateBinding binding) {
        return java.util.Map.of(
                "applicationId", binding.applicationId().value(),
                "connectorId", binding.connectorId(),
                "kind", binding.kind(),
                "system", binding.system(),
                "template", binding.coordinateTemplate(),
                "shortCommitLength", binding.shortCommitLength());
    }

    @Override
    public Optional<ArtifactCoordinateBinding> findArtifactCoordinateBinding(
            ApplicationId applicationId, String connectorId, String kind) {
        return jdbcClient.sql(ARTIFACT_COORDINATE_COLUMNS + """
                        WHERE application_id = :applicationId AND connector_id = :connectorId
                          AND kind = :kind
                        """)
                .param("applicationId", applicationId.value())
                .param("connectorId", connectorId)
                // Normalised on the way in as well as on the way out, so a lookup
                // for "Image" finds the row bound as "image".
                .param("kind", ArtifactCoordinateBinding.normaliseKind(kind))
                .query(ExternalBindingJdbcRepository::mapArtifactCoordinateBinding)
                .optional();
    }

    @Override
    public List<ArtifactCoordinateBinding> findArtifactCoordinateBindings(ApplicationId applicationId) {
        return jdbcClient.sql(ARTIFACT_COORDINATE_COLUMNS + """
                        WHERE application_id = :applicationId ORDER BY connector_id, kind
                        """)
                .param("applicationId", applicationId.value())
                .query(ExternalBindingJdbcRepository::mapArtifactCoordinateBinding)
                .list();
    }

    @Override
    public List<ArtifactCoordinateBinding> findAllArtifactCoordinateBindings(String connectorId) {
        return jdbcClient.sql(ARTIFACT_COORDINATE_COLUMNS + """
                        WHERE connector_id = :connectorId ORDER BY kind
                        """)
                .param("connectorId", connectorId)
                .query(ExternalBindingJdbcRepository::mapArtifactCoordinateBinding)
                .list();
    }

    @Override
    public List<ArtifactCoordinateBinding> findAllArtifactCoordinateBindings() {
        return jdbcClient.sql(ARTIFACT_COORDINATE_COLUMNS + " ORDER BY connector_id, kind")
                .query(ExternalBindingJdbcRepository::mapArtifactCoordinateBinding)
                .list();
    }

    @Override
    public void deleteArtifactCoordinateBinding(
            ApplicationId applicationId, String connectorId, String kind) {
        jdbcClient.sql("""
                        DELETE FROM artifact_coordinate_binding
                        WHERE application_id = :applicationId AND connector_id = :connectorId
                          AND kind = :kind
                        """)
                .param("applicationId", applicationId.value())
                .param("connectorId", connectorId)
                .param("kind", ArtifactCoordinateBinding.normaliseKind(kind))
                .update();
    }

    /** Every column, once, so the four reads above cannot drift apart. */
    private static final String ARTIFACT_COORDINATE_COLUMNS = """
            SELECT application_id, connector_id, kind, repository_system,
                   coordinate_template, short_commit_length
            FROM artifact_coordinate_binding
            """;

    private static ArtifactCoordinateBinding mapArtifactCoordinateBinding(ResultSet rs, int rowNum)
            throws SQLException {
        return new ArtifactCoordinateBinding(
                new ApplicationId(rs.getObject("application_id", UUID.class)),
                rs.getString("connector_id"),
                rs.getString("kind"),
                rs.getString("repository_system"),
                rs.getString("coordinate_template"),
                rs.getInt("short_commit_length"));
    }

    /**
     * Build job bindings (ADR-020), keyed by Application, Connector and job
     * (V16__build_job_bindings.sql).
     *
     * <p>The job is in the key here and is not in V12's, which is the difference
     * between the two tables in one line: two build jobs for one Application are
     * ordinary, while two deployment jobs for one Environment and Application
     * would both claim to say when it arrived.
     */
    @Override
    public BuildJobBinding save(BuildJobBinding binding) {
        int updated = jdbcClient.sql("""
                        UPDATE build_job_binding
                        SET ci_system = :system, version_source = :versionSource,
                            version_key = :versionKey, version_pattern = :versionPattern
                        WHERE application_id = :applicationId AND connector_id = :connectorId
                          AND job = :job
                        """)
                .params(parametersOf(binding))
                .update();
        if (updated == 0) {
            jdbcClient.sql("""
                            INSERT INTO build_job_binding (
                                application_id, connector_id, ci_system, job,
                                version_source, version_key, version_pattern)
                            VALUES (:applicationId, :connectorId, :system, :job,
                                    :versionSource, :versionKey, :versionPattern)
                            """)
                    .params(parametersOf(binding))
                    .update();
        }
        return binding;
    }

    private static java.util.Map<String, Object> parametersOf(BuildJobBinding binding) {
        return java.util.Map.of(
                "applicationId", binding.applicationId().value(),
                "connectorId", binding.connectorId(),
                "system", binding.system(),
                "job", binding.job(),
                "versionSource", binding.versionSource().name(),
                "versionKey", binding.versionKey(),
                "versionPattern", binding.versionPattern());
    }

    @Override
    public Optional<BuildJobBinding> findBuildJobBinding(
            ApplicationId applicationId, String connectorId, String job) {
        return jdbcClient.sql(BUILD_JOB_COLUMNS + """
                        WHERE application_id = :applicationId AND connector_id = :connectorId
                          AND job = :job
                        """)
                .param("applicationId", applicationId.value())
                .param("connectorId", connectorId)
                .param("job", job)
                .query(ExternalBindingJdbcRepository::mapBuildJobBinding)
                .optional();
    }

    @Override
    public List<BuildJobBinding> findBuildJobBindings(ApplicationId applicationId) {
        return jdbcClient.sql(BUILD_JOB_COLUMNS + """
                        WHERE application_id = :applicationId ORDER BY connector_id, job
                        """)
                .param("applicationId", applicationId.value())
                .query(ExternalBindingJdbcRepository::mapBuildJobBinding)
                .list();
    }

    @Override
    public List<BuildJobBinding> findAllBuildJobBindings(String connectorId) {
        return jdbcClient.sql(BUILD_JOB_COLUMNS + " WHERE connector_id = :connectorId ORDER BY job")
                .param("connectorId", connectorId)
                .query(ExternalBindingJdbcRepository::mapBuildJobBinding)
                .list();
    }

    @Override
    public List<BuildJobBinding> findAllBuildJobBindings() {
        return jdbcClient.sql(BUILD_JOB_COLUMNS + " ORDER BY connector_id, job")
                .query(ExternalBindingJdbcRepository::mapBuildJobBinding)
                .list();
    }

    @Override
    public void deleteBuildJobBinding(ApplicationId applicationId, String connectorId, String job) {
        jdbcClient.sql("""
                        DELETE FROM build_job_binding
                        WHERE application_id = :applicationId AND connector_id = :connectorId
                          AND job = :job
                        """)
                .param("applicationId", applicationId.value())
                .param("connectorId", connectorId)
                .param("job", job)
                .update();
    }

    /** Every column, once, so the four reads above cannot drift apart. */
    private static final String BUILD_JOB_COLUMNS = """
            SELECT application_id, connector_id, ci_system, job,
                   version_source, version_key, version_pattern
            FROM build_job_binding
            """;

    private static BuildJobBinding mapBuildJobBinding(ResultSet rs, int rowNum) throws SQLException {
        return new BuildJobBinding(
                new ApplicationId(rs.getObject("application_id", UUID.class)),
                rs.getString("connector_id"),
                rs.getString("ci_system"),
                rs.getString("job"),
                // Parsed rather than valueOf'd, so a row written by a later
                // version of Tower fails with a message naming the value.
                PipelineJobBinding.VersionSource.parse(rs.getString("version_source")),
                rs.getString("version_key"),
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
