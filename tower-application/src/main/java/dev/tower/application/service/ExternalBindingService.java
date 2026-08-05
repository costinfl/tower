package dev.tower.application.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dev.tower.application.binding.ApplicationBinding;
import dev.tower.application.binding.ArtifactCoordinateBinding;
import dev.tower.application.binding.BuildJobBinding;
import dev.tower.application.binding.EnvironmentBinding;
import dev.tower.application.binding.IssueTrackerBinding;
import dev.tower.application.binding.PipelineJobBinding;
import dev.tower.application.binding.RepositoryBinding;
import dev.tower.application.port.in.ExternalBindingUseCases;
import dev.tower.application.port.in.ExternalBindingUseCases.BindRepository;
import dev.tower.application.port.out.ApplicationRepository;
import dev.tower.application.port.out.EnvironmentRepository;
import dev.tower.application.port.out.ExternalBindingRepository;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.environment.EnvironmentId;

/**
 * External Binding use cases (issue #47).
 *
 * <p>Carries no framework annotation, like every service here; Spring wiring
 * lives in tower-api.
 *
 * <p>Saving a binding replaces any binding already held for the same pair rather
 * than refusing. A binding is configuration, and re-pointing an Environment at a
 * different namespace is an ordinary correction, not a conflict. Nothing already
 * observed changes: Observations recorded under the previous binding remain
 * facts about what was seen, which is what ADR-002 requires of them.
 */
public class ExternalBindingService implements ExternalBindingUseCases {

    private final ExternalBindingRepository bindings;
    private final EnvironmentRepository environments;
    private final ApplicationRepository applications;

    public ExternalBindingService(ExternalBindingRepository bindings,
                                  EnvironmentRepository environments,
                                  ApplicationRepository applications) {
        this.bindings = Objects.requireNonNull(bindings);
        this.environments = Objects.requireNonNull(environments);
        this.applications = Objects.requireNonNull(applications);
    }

    @Override
    public EnvironmentBinding bindEnvironment(BindEnvironment command) {
        requireEnvironmentExists(command.environmentId());
        return bindings.save(new EnvironmentBinding(
                command.environmentId(), command.connectorId(), command.target(), command.scope()));
    }

    @Override
    public ApplicationBinding bindApplication(BindApplication command) {
        requireApplicationExists(command.applicationId());
        return bindings.save(new ApplicationBinding(
                command.applicationId(), command.connectorId(), command.image(), command.versionPattern()));
    }

    @Override
    public List<EnvironmentBinding> listEnvironmentBindings() {
        return bindings.findAllEnvironmentBindings();
    }

    @Override
    public List<ApplicationBinding> listApplicationBindings() {
        return bindings.findAllApplicationBindings();
    }

    @Override
    public Optional<EnvironmentBinding> findEnvironmentBinding(EnvironmentId environmentId, String connectorId) {
        return bindings.findEnvironmentBinding(environmentId, connectorId);
    }

    @Override
    public Optional<ApplicationBinding> findApplicationBinding(ApplicationId applicationId, String connectorId) {
        return bindings.findApplicationBinding(applicationId, connectorId);
    }

    @Override
    public void unbindEnvironment(EnvironmentId environmentId, String connectorId) {
        bindings.deleteEnvironmentBinding(environmentId, connectorId);
    }

    @Override
    public void unbindApplication(ApplicationId applicationId, String connectorId) {
        bindings.deleteApplicationBinding(applicationId, connectorId);
    }

    @Override
    public RepositoryBinding bindRepository(BindRepository command) {
        requireApplicationExists(command.applicationId());
        return bindings.save(new RepositoryBinding(
                command.applicationId(), command.connectorId(), command.repositoryUrl(),
                command.refSelection(), command.versionPattern()));
    }

    @Override
    public List<RepositoryBinding> listRepositoryBindings() {
        return bindings.findAllRepositoryBindings();
    }

    @Override
    public Optional<RepositoryBinding> findRepositoryBinding(ApplicationId applicationId, String connectorId) {
        return bindings.findRepositoryBinding(applicationId, connectorId);
    }

    @Override
    public void unbindRepository(ApplicationId applicationId, String connectorId) {
        bindings.deleteRepositoryBinding(applicationId, connectorId);
    }

    /**
     * Binds the team's tracker (ADR-018).
     *
     * <p>Nothing is checked to exist first, and that is the difference from every
     * binding above. The others name a Tower concept whose absence would make the
     * binding meaningless; this one names only a Connector and a locator, and
     * whether the locator points at a real repository is a question only the
     * Connector can answer — which is what the connection test is for.
     */
    @Override
    public IssueTrackerBinding bindIssueTracker(BindIssueTracker command) {
        return bindings.save(new IssueTrackerBinding(command.connectorId(), command.locator()));
    }

    @Override
    public List<IssueTrackerBinding> listIssueTrackerBindings() {
        return bindings.findAllIssueTrackerBindings();
    }

    @Override
    public Optional<IssueTrackerBinding> findIssueTrackerBinding(String connectorId) {
        return bindings.findIssueTrackerBinding(connectorId);
    }

    @Override
    public void unbindIssueTracker(String connectorId) {
        bindings.deleteIssueTrackerBinding(connectorId);
    }

    /**
     * Binds a job to the Environment and Application its runs concern (ADR-020).
     *
     * <p>Both are checked to exist, unlike an issue tracker binding: this one
     * names Tower's own rows on the left, and a binding for an Environment that
     * does not exist describes nothing.
     */
    @Override
    public PipelineJobBinding bindPipelineJob(BindPipelineJob command) {
        requireEnvironmentExists(command.environmentId());
        requireApplicationExists(command.applicationId());
        return bindings.save(new PipelineJobBinding(
                command.environmentId(), command.applicationId(), command.connectorId(),
                command.system(), command.job(),
                PipelineJobBinding.VersionSource.parse(command.versionSource()),
                command.versionKey(), command.versionPattern()));
    }

    @Override
    public List<PipelineJobBinding> listPipelineJobBindings() {
        return bindings.findAllPipelineJobBindings();
    }

    @Override
    public void unbindPipelineJob(EnvironmentId environmentId, ApplicationId applicationId,
                                  String connectorId) {
        bindings.deletePipelineJobBinding(environmentId, applicationId, connectorId);
    }

    @Override
    public BuildJobBinding bindBuildJob(BindBuildJob command) {
        requireApplicationExists(command.applicationId());
        return bindings.save(new BuildJobBinding(
                command.applicationId(), command.connectorId(), command.system(), command.job(),
                PipelineJobBinding.VersionSource.parse(command.versionSource()),
                command.versionKey(), command.versionPattern()));
    }

    @Override
    public List<BuildJobBinding> listBuildJobBindings() {
        return bindings.findAllBuildJobBindings();
    }

    @Override
    public void unbindBuildJob(ApplicationId applicationId, String connectorId, String job) {
        bindings.deleteBuildJobBinding(applicationId, connectorId, job);
    }

    @Override
    public ArtifactCoordinateBinding bindArtifactCoordinate(BindArtifactCoordinate command) {
        requireApplicationExists(command.applicationId());
        return bindings.save(new ArtifactCoordinateBinding(
                command.applicationId(), command.connectorId(), command.kind(),
                command.system(), command.coordinateTemplate(), command.shortCommitLength()));
    }

    @Override
    public List<ArtifactCoordinateBinding> listArtifactCoordinateBindings() {
        return bindings.findAllArtifactCoordinateBindings();
    }

    @Override
    public void unbindArtifactCoordinate(ApplicationId applicationId, String connectorId, String kind) {
        bindings.deleteArtifactCoordinateBinding(applicationId, connectorId, kind);
    }

    @Override
    public CoordinatePreview previewCoordinate(String coordinateTemplate, int shortCommitLength,
                                               String version, String commit) {
        InvalidRequestException.require(version != null && !version.isBlank(),
                "Supply a version to compose the coordinate from.");

        // Built through the binding for the same reason previewVersion is: a
        // preview that agreed with a separate implementation rather than the one
        // a real confirmation uses would be worse than none.
        var candidate = new ArtifactCoordinateBinding(
                ApplicationId.newId(), "preview", "preview", "preview",
                coordinateTemplate, shortCommitLength);
        var pretend = dev.tower.domain.application.ApplicationVersion.create(
                ApplicationId.newId(), version.trim(), null, null, commit, null);

        return candidate.compose(pretend)
                .map(composed -> new CoordinatePreview(
                        candidate.coordinateTemplate(), composed, List.of()))
                .orElseGet(() -> new CoordinatePreview(candidate.coordinateTemplate(), null,
                        missingFields(candidate, pretend)));
    }

    /**
     * Which token the template wanted and the version does not carry.
     *
     * <p>Only reached when composing failed, and only {@code commit} can be
     * absent: a version is required for an Application Version to exist at all.
     */
    private List<String> missingFields(ArtifactCoordinateBinding binding,
                                       dev.tower.domain.application.ApplicationVersion version) {
        if (version.commit() != null) {
            return List.of();
        }
        return binding.requiredFields().stream()
                .filter(field -> field.equals(ArtifactCoordinateBinding.COMMIT)
                        || field.equals(ArtifactCoordinateBinding.SHORT_COMMIT))
                .toList();
    }

    @Override
    public VersionPreview previewVersion(String versionPattern, String imageTag) {
        InvalidRequestException.require(imageTag != null && !imageTag.isBlank(),
                "Supply an image tag to try the pattern against.");

        // Built through ApplicationBinding so the preview uses exactly the code
        // a synchronization run will use. A preview that agreed with a separate
        // implementation rather than the real one would be worse than none.
        var candidate = new ApplicationBinding(
                ApplicationId.newId(), "preview", "preview", versionPattern);

        return candidate.resolveVersion(imageTag)
                .map(version -> new VersionPreview(imageTag.trim(), candidate.versionPattern(), true, version))
                .orElseGet(() -> new VersionPreview(imageTag.trim(), candidate.versionPattern(), false, null));
    }

    private void requireEnvironmentExists(EnvironmentId id) {
        if (environments.findById(id).isEmpty()) {
            throw new NotFoundException("Environment " + id + " does not exist.");
        }
    }

    private void requireApplicationExists(ApplicationId id) {
        if (applications.findById(id).isEmpty()) {
            throw new NotFoundException("Application " + id + " does not exist.");
        }
    }
}
