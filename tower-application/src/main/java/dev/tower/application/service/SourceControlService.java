package dev.tower.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.tower.application.binding.RepositoryBinding;
import dev.tower.application.discovery.VersionDiscovery;
import dev.tower.application.port.in.SourceControlUseCases;
import dev.tower.application.port.out.ApplicationRepository;
import dev.tower.application.port.out.ExternalBindingRepository;
import dev.tower.application.port.out.SourceVersionCollector;
import dev.tower.application.sync.ConnectionTest;
import dev.tower.domain.application.ApplicationId;

/**
 * Source control discovery use cases (issue #3, ADR-014).
 *
 * <p>Carries no framework annotation, like every service here; Spring wiring
 * lives in tower-api.
 *
 * <p>Thin on purpose. The interpretation — applying the binding, matching the
 * pattern, marking what Tower already holds — belongs to the Collector, which is
 * where Connector-Model.md puts normalization. What this adds is the checks that
 * are about Tower's own model rather than about a repository: that the
 * Application exists at all, and that asking about every Application means every
 * Application someone has actually bound.
 *
 * <p>Takes a list of Collectors rather than one, so adding a second Source
 * Control Connector later needs no change here. Spring injects an empty list when
 * none is present, which is the correct behaviour for a build without connector
 * modules.
 */
public class SourceControlService implements SourceControlUseCases {

    private final List<SourceVersionCollector> collectors;
    private final ExternalBindingRepository bindings;
    private final ApplicationRepository applications;

    public SourceControlService(List<SourceVersionCollector> collectors,
                                ExternalBindingRepository bindings,
                                ApplicationRepository applications) {
        this.collectors = List.copyOf(Objects.requireNonNull(collectors));
        this.bindings = Objects.requireNonNull(bindings);
        this.applications = Objects.requireNonNull(applications);
    }

    @Override
    public VersionDiscovery discover(ApplicationId applicationId) {
        // An unknown Application is a bad request, unlike an unreachable
        // repository: the caller named something that does not exist, and no
        // amount of fixing configuration will change the answer.
        if (applications.findById(applicationId).isEmpty()) {
            throw new NotFoundException("Application " + applicationId + " does not exist.");
        }
        if (collectors.isEmpty()) {
            return VersionDiscovery.failed(applicationId, null,
                    "No Source Control Connector is installed.");
        }

        // First Collector that has a binding for this Application answers. With
        // one Source Control Connector installed this is simply "the Collector";
        // the loop is what keeps a second one from needing a change here.
        for (SourceVersionCollector collector : collectors) {
            if (bindings.findRepositoryBinding(applicationId, collector.connectorId()).isPresent()) {
                return collector.discover(applicationId);
            }
        }
        return VersionDiscovery.failed(applicationId, null,
                "No repository is bound to this Application.");
    }

    @Override
    public List<VersionDiscovery> discoverAll() {
        List<VersionDiscovery> discoveries = new ArrayList<>();
        for (SourceVersionCollector collector : collectors) {
            for (RepositoryBinding binding : bindings.findAllRepositoryBindings(collector.connectorId())) {
                discoveries.add(collector.discover(binding.applicationId()));
            }
        }
        return List.copyOf(discoveries);
    }

    @Override
    public ConnectionTest checkConnection(String repositoryUrl) {
        InvalidRequestException.require(repositoryUrl != null && !repositoryUrl.isBlank(),
                "Supply the repository URL to test.");
        if (collectors.isEmpty()) {
            throw new InvalidRequestException("No Source Control Connector is installed.");
        }
        return collectors.get(0).checkConnection(repositoryUrl.trim());
    }
}
