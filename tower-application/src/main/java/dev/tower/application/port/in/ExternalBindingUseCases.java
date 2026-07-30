package dev.tower.application.port.in;

import java.util.List;
import java.util.Optional;

import dev.tower.application.binding.ApplicationBinding;
import dev.tower.application.binding.EnvironmentBinding;
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
}
