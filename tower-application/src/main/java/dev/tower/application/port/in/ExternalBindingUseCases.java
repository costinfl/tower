package dev.tower.application.port.in;

import java.util.List;
import java.util.Optional;

import dev.tower.application.binding.ApplicationBinding;
import dev.tower.application.binding.ArtifactCoordinateBinding;
import dev.tower.application.binding.EnvironmentBinding;
import dev.tower.application.binding.IssueTrackerBinding;
import dev.tower.application.binding.PipelineJobBinding;
import dev.tower.application.binding.RepositoryBinding;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.environment.EnvironmentId;

/**
 * Inbound port for managing External Bindings (issue #47, ADR-012, FR-055, FR-056).
 *
 * <p>Binding is configuration, so these use cases carry only the rules that keep
 * a binding usable: the Environment or Application must exist, and the version
 * pattern must be able to produce a version.
 */
public interface ExternalBindingUseCases {

    EnvironmentBinding bindEnvironment(BindEnvironment command);

    ApplicationBinding bindApplication(BindApplication command);

    List<EnvironmentBinding> listEnvironmentBindings();

    List<ApplicationBinding> listApplicationBindings();

    Optional<EnvironmentBinding> findEnvironmentBinding(EnvironmentId environmentId, String connectorId);

    Optional<ApplicationBinding> findApplicationBinding(ApplicationId applicationId, String connectorId);

    void unbindEnvironment(EnvironmentId environmentId, String connectorId);

    void unbindApplication(ApplicationId applicationId, String connectorId);

    /**
     * What a pattern would make of a tag, without saving anything.
     *
     * <p>Exists because ADR-012 records the one mistake this design cannot undo:
     * a wrong pattern produces wrong Application Versions, and Observations are
     * immutable, so those survive the correction. Letting a user try the pattern
     * against a tag they recognise is cheap insurance against that.
     */
    VersionPreview previewVersion(String versionPattern, String imageTag);

    record BindEnvironment(EnvironmentId environmentId, String connectorId, String target, String scope) {}

    record BindApplication(ApplicationId applicationId, String connectorId, String image, String versionPattern) {}

    /**
     * @param matched whether the pattern recognised the tag at all
     * @param version the Application Version it yields, or null when it did not match
     */
    record VersionPreview(String imageTag, String versionPattern, boolean matched, String version) {}

    // Repository bindings (issue #3, ADR-014): where an Application's code lives.

    RepositoryBinding bindRepository(BindRepository command);

    List<RepositoryBinding> listRepositoryBindings();

    Optional<RepositoryBinding> findRepositoryBinding(ApplicationId applicationId, String connectorId);

    void unbindRepository(ApplicationId applicationId, String connectorId);

    record BindRepository(
            ApplicationId applicationId, String connectorId, String repositoryUrl,
            RepositoryBinding.RefSelection refSelection, String versionPattern) {}

    // Issue tracker bindings (ADR-018): where the team's work items live.
    //
    // No Tower concept on the left, unlike every binding above it. A work item
    // belongs to a release rather than to an Application, and a team has one
    // tracker, so the tracker is named once per Connector. ADR-018 records why
    // that difference is deliberate.

    IssueTrackerBinding bindIssueTracker(BindIssueTracker command);

    List<IssueTrackerBinding> listIssueTrackerBindings();

    Optional<IssueTrackerBinding> findIssueTrackerBinding(String connectorId);

    void unbindIssueTracker(String connectorId);

    /**
     * @param locator where the tracker lives, in the form that Connector expects
     *                — {@code owner/repository} for GitHub Issues, a site for
     *                Jira. Opaque above the Connector, deliberately: nothing in
     *                the application layer may parse it.
     */
    record BindIssueTracker(String connectorId, String locator) {}

    // Pipeline job bindings (ADR-020). Two Tower concepts on the left, because
    // that is what a run of a deployment job asserts.

    PipelineJobBinding bindPipelineJob(BindPipelineJob command);

    List<PipelineJobBinding> listPipelineJobBindings();

    void unbindPipelineJob(EnvironmentId environmentId, ApplicationId applicationId,
                           String connectorId);

    /**
     * @param versionSource where in a run the version is written — PARAMETER,
     *                      RUN_NAME or JOB_PATH. A CI system used as most of them
     *                      actually are keeps it somewhere a Connector cannot
     *                      infer, so it is configuration (ADR-020).
     */
    record BindPipelineJob(EnvironmentId environmentId, ApplicationId applicationId,
                           String connectorId, String system, String job,
                           String versionSource, String versionKey, String versionPattern) {}

    // Artifact coordinate bindings (ADR-021). The only binding a single
    // Application may have several of for one Connector, because a build
    // publishes an image and a chart and the kind is what tells them apart.

    ArtifactCoordinateBinding bindArtifactCoordinate(BindArtifactCoordinate command);

    List<ArtifactCoordinateBinding> listArtifactCoordinateBindings();

    void unbindArtifactCoordinate(ApplicationId applicationId, String connectorId, String kind);

    /**
     * @param kind               the team's own word for what this addresses —
     *                           "image", "chart", "installer". Never interpreted
     *                           (FR-083)
     * @param coordinateTemplate the vendor locator with {@code {version}},
     *                           {@code {commit}} and {@code {shortCommit}}
     *                           standing in for what Tower holds. Composes where
     *                           a version pattern extracts, which is ADR-012's
     *                           shape running the other way
     * @param shortCommitLength  0 to take the default of seven
     */
    record BindArtifactCoordinate(ApplicationId applicationId, String connectorId, String kind,
                                  String system, String coordinateTemplate, int shortCommitLength) {}

    /**
     * What a template would make of a version, without saving anything.
     *
     * <p>The counterpart of {@link #previewVersion}, and useful for a milder
     * reason. A wrong version pattern writes wrong Observations that outlive the
     * correction; a wrong template only produces a false "not found" (ADR-021).
     * This exists so that false absence can be told apart from real absence
     * before somebody goes looking in the repository for something that was never
     * addressed correctly.
     */
    CoordinatePreview previewCoordinate(String coordinateTemplate, int shortCommitLength,
                                        String version, String commit);

    /**
     * @param composed the coordinate the template yields, or null when the
     *                 version does not carry what the template asks for
     * @param missing  which of {@code version}, {@code commit} or
     *                 {@code shortCommit} was wanted and absent
     */
    record CoordinatePreview(String coordinateTemplate, String composed, List<String> missing) {

        public CoordinatePreview {
            missing = List.copyOf(missing);
        }

        public boolean wasComposed() {
            return composed != null;
        }
    }
}
