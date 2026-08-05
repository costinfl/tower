package dev.tower.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import dev.tower.application.binding.BuildJobBinding;
import dev.tower.application.binding.RepositoryBinding;
import dev.tower.application.discovery.DiscoveredVersion;
import dev.tower.application.discovery.VersionDiscovery;
import dev.tower.application.port.in.SourceControlUseCases;
import dev.tower.application.port.out.ApplicationRepository;
import dev.tower.application.port.out.BuildVersionCollector;
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
 *
 * <p>Since ADR-020 there are two kinds of Collector rather than one, and this
 * merges what they propose into a single list. That was ADR-020's decision — "the
 * version discovery screen gains a source rather than a mode" — and it is why the
 * merge lives here rather than in the Viewer: a ref and a build run are the same
 * kind of proposal, and a screen that had to know which tab to look under would
 * be the mode ADR-020 declined.
 */
public class SourceControlService implements SourceControlUseCases {

    private final List<SourceVersionCollector> collectors;
    private final List<BuildVersionCollector> buildCollectors;
    private final ExternalBindingRepository bindings;
    private final ApplicationRepository applications;

    public SourceControlService(List<SourceVersionCollector> collectors,
                                List<BuildVersionCollector> buildCollectors,
                                ExternalBindingRepository bindings,
                                ApplicationRepository applications) {
        this.collectors = List.copyOf(Objects.requireNonNull(collectors));
        this.buildCollectors = List.copyOf(Objects.requireNonNull(buildCollectors));
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
        // Both sources are asked, and what they propose is merged (ADR-020).
        List<VersionDiscovery> answers = new ArrayList<>();
        sourceControlFor(applicationId).ifPresent(answers::add);
        for (BuildVersionCollector collector : buildCollectors) {
            if (!bindings.findBuildJobBindings(applicationId).isEmpty()) {
                VersionDiscovery answer = collector.discover(applicationId);
                // A Collector with no binding of its own says so, and that is not
                // worth showing beside one that answered.
                if (answer.succeeded() || answers.isEmpty()) {
                    answers.add(answer);
                }
            }
        }

        if (answers.isEmpty()) {
            return VersionDiscovery.failed(applicationId, null,
                    "Nothing is bound to this Application that could propose a version."
                            + " Bind a repository or a build job on the Connectors page.");
        }
        return answers.size() == 1 ? answers.get(0) : merge(applicationId, answers);
    }

    /**
     * The source control answer, if any Collector has a repository bound.
     *
     * <p>First Collector with a binding answers. With one Source Control
     * Connector installed this is simply "the Collector"; the loop is what keeps
     * a second one from needing a change here.
     */
    private java.util.Optional<VersionDiscovery> sourceControlFor(ApplicationId applicationId) {
        for (SourceVersionCollector collector : collectors) {
            if (bindings.findRepositoryBinding(applicationId, collector.connectorId()).isPresent()) {
                return java.util.Optional.of(collector.discover(applicationId));
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Two sources' candidates in one list.
     *
     * <p>A version proposed by both appears once, and the first source to offer
     * it wins — source control, because it is asked first and because a ref
     * carries a commit where a build run does not. Nothing is lost by that: the
     * two are proposing the same version, and {@code source} says which system a
     * reader is looking at.
     *
     * <p>A failure from one source does not empty the other's candidates. Both
     * failing is reported; one failing is a partial answer, and saying "could
     * not look" over a list with entries in it would be false.
     */
    private VersionDiscovery merge(ApplicationId applicationId, List<VersionDiscovery> answers) {
        Map<String, DiscoveredVersion> byVersion = new LinkedHashMap<>();
        List<String> unmatched = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        String locator = null;

        for (VersionDiscovery answer : answers) {
            if (answer.failure() != null) {
                failures.add(answer.failure());
                continue;
            }
            if (locator == null) {
                locator = answer.repositoryUrl();
            }
            answer.candidates().forEach(c -> byVersion.putIfAbsent(c.version(), c));
            unmatched.addAll(answer.unmatched());
        }

        if (byVersion.isEmpty() && !failures.isEmpty()) {
            return VersionDiscovery.failed(applicationId, locator, String.join(" ", failures));
        }
        return VersionDiscovery.found(applicationId, locator,
                List.copyOf(byVersion.values()), List.copyOf(unmatched));
    }

    /**
     * Every Application something is bound for, each with both sources merged.
     *
     * <p>Keyed by Application rather than by binding, because an Application with
     * a repository <em>and</em> a build job is one thing to look at, not two.
     */
    @Override
    public List<VersionDiscovery> discoverAll() {
        Set<ApplicationId> bound = new LinkedHashSet<>();
        for (SourceVersionCollector collector : collectors) {
            bindings.findAllRepositoryBindings(collector.connectorId())
                    .forEach(binding -> bound.add(binding.applicationId()));
        }
        for (BuildVersionCollector collector : buildCollectors) {
            for (BuildJobBinding binding : bindings.findAllBuildJobBindings(collector.connectorId())) {
                bound.add(binding.applicationId());
            }
        }
        return bound.stream().map(this::discover).toList();
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
