package dev.tower.application.service;

import dev.tower.application.port.in.ApplicationUseCases;
import dev.tower.application.port.out.ApplicationRepository;
import dev.tower.application.port.out.ApplicationVersionRepository;
import dev.tower.application.port.out.ReleasePackRepository;
import dev.tower.domain.application.Application;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.releasepack.ReleasePack;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Application and Application Version registry use cases (issue #14).
 *
 * <p>Carries no framework annotation; Spring wiring lives in tower-api.
 */
public class ApplicationService implements ApplicationUseCases {

    private final ApplicationRepository applications;
    private final ApplicationVersionRepository versions;
    private final ReleasePackRepository releasePacks;

    public ApplicationService(ApplicationRepository applications,
                              ApplicationVersionRepository versions,
                              ReleasePackRepository releasePacks) {
        this.applications = Objects.requireNonNull(applications);
        this.versions = Objects.requireNonNull(versions);
        this.releasePacks = Objects.requireNonNull(releasePacks);
    }

    @Override
    public Application register(RegisterApplication command) {
        requireNameAvailable(command.name(), null);
        return applications.save(Application.create(command.name(), command.description()));
    }

    @Override
    public Application update(UpdateApplication command) {
        Application existing = get(command.id());
        requireNameAvailable(command.name(), existing.name());
        return applications.save(existing.update(command.name(), command.description()));
    }

    @Override
    public List<Application> list() {
        return applications.findAll();
    }

    @Override
    public Application get(ApplicationId id) {
        return applications.findById(id)
                .orElseThrow(() -> new NotFoundException("Application " + id + " does not exist."));
    }

    @Override
    public void delete(ApplicationId id) {
        Application application = get(id);

        // Versions are immutable historical facts (BR-01). Deleting the
        // Application beneath them would orphan records that Release Packs and,
        // later, Observations may still refer to.
        List<ApplicationVersion> existing = versions.findAllByApplication(id);
        if (!existing.isEmpty()) {
            throw new ApplicationException("Application '" + application.name() + "' still has "
                    + existing.size() + " registered version(s). Remove them first.");
        }
        applications.deleteById(id);
    }

    @Override
    public ApplicationVersion registerVersion(RegisterVersion command) {
        Application application = get(command.applicationId());
        String version = command.version() == null ? "" : command.version().trim();

        if (versions.existsByApplicationAndVersion(command.applicationId(), version)) {
            throw new ApplicationException("Version '" + version + "' is already registered for '"
                    + application.name() + "'. Application Versions are immutable (BR-01),"
                    + " so record a different version rather than redefining this one.");
        }
        return versions.save(ApplicationVersion.create(command.applicationId(), command.version(),
                command.branch(), command.tag(), command.commit(), command.buildIdentifier()));
    }

    @Override
    public List<ApplicationVersion> listVersions() {
        return versions.findAll();
    }

    @Override
    public List<ApplicationVersion> listVersionsOf(ApplicationId applicationId) {
        get(applicationId);
        return versions.findAllByApplication(applicationId);
    }

    @Override
    public ApplicationVersion getVersion(ApplicationVersionId id) {
        return versions.findById(id)
                .orElseThrow(() -> new NotFoundException("Application Version " + id + " does not exist."));
    }

    @Override
    public void deleteVersion(ApplicationVersionId id) {
        getVersion(id);

        // A Release Pack records what a team decided to deliver together.
        // Deleting a Version out from under it would rewrite that decision.
        List<ReleasePack> containing = releasePacks.findAllContaining(id);
        if (!containing.isEmpty()) {
            String names = containing.stream().map(ReleasePack::name).sorted().collect(Collectors.joining(", "));
            throw new ApplicationException("This Application Version is contained in Release Pack(s): "
                    + names + ". Remove it from them before deleting it.");
        }
        versions.deleteById(id);
    }

    private void requireNameAvailable(String name, String currentName) {
        if (name != null && !name.equalsIgnoreCase(currentName)
                && applications.existsByNameIgnoringCase(name.trim())) {
            throw new ApplicationException("An Application named '" + name.trim() + "' already exists.");
        }
    }
}
