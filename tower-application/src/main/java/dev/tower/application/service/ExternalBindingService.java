package dev.tower.application.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dev.tower.application.binding.ApplicationBinding;
import dev.tower.application.binding.EnvironmentBinding;
import dev.tower.application.port.in.ExternalBindingUseCases;
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
