package dev.tower.collector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.tower.application.binding.RepositoryBinding;
import dev.tower.application.discovery.DiscoveredVersion;
import dev.tower.application.discovery.VersionDiscovery;
import dev.tower.application.port.out.ApplicationVersionRepository;
import dev.tower.application.port.out.ConnectorCredentialsPort;
import dev.tower.application.port.out.ExternalBindingRepository;
import dev.tower.application.port.out.SourceVersionCollector;
import dev.tower.application.sync.ConnectionTest;
import dev.tower.connector.api.ConnectorCredential;
import dev.tower.connector.api.ConnectorException;
import dev.tower.connector.api.RepositoryLocator;
import dev.tower.connector.api.SourceControlConnector;
import dev.tower.connector.api.SourceRef;
import dev.tower.domain.application.ApplicationId;

/**
 * Turns the refs a repository holds into candidate Application Versions
 * (issue #3, ADR-014).
 *
 * <p>The sibling of {@link DeploymentCollector}, and deliberately the quieter
 * one. That Collector appends Observations, because what a platform is running is
 * a fact about the world Tower saw for itself. A ref is not that — it is a list
 * of names someone may want to register — so this Collector stores nothing at
 * all. BR-01 makes an Application Version immutable, and a version created
 * without anyone asking would be immutable too.
 *
 * <p>What it does instead is apply the binding: filter the refs to the kinds the
 * user selected, run the version pattern over their names, and mark the ones
 * Tower already holds. The last of those is why discovery is useful on the second
 * run as well as the first.
 *
 * <p>Refs the pattern does not recognise are reported rather than dropped, the
 * same rule the deployment Collector follows for workloads it cannot attribute
 * (FR-060). A user whose pattern is wrong needs to see what it failed on; a
 * silently shorter list gives them nothing to work from.
 */
@Component
public class SourceControlCollector implements SourceVersionCollector {

    private final SourceControlConnector connector;
    private final ExternalBindingRepository bindings;
    private final ApplicationVersionRepository versions;
    private final ConnectorCredentialsPort credentials;

    public SourceControlCollector(SourceControlConnector connector,
                                  ExternalBindingRepository bindings,
                                  ApplicationVersionRepository versions,
                                  ConnectorCredentialsPort credentials) {
        this.connector = Objects.requireNonNull(connector);
        this.bindings = Objects.requireNonNull(bindings);
        this.versions = Objects.requireNonNull(versions);
        this.credentials = Objects.requireNonNull(credentials);
    }

    @Override
    public String connectorId() {
        return connector.connectorId();
    }

    @Override
    public VersionDiscovery discover(ApplicationId applicationId) {
        String connectorId = connector.connectorId();
        Optional<RepositoryBinding> bound = bindings.findRepositoryBinding(applicationId, connectorId);
        if (bound.isEmpty()) {
            // Not a failure of the repository — there is no repository. Said in
            // words rather than returned as an empty list, which would read as
            // "your repository is empty" and send someone to look at the wrong
            // thing.
            return VersionDiscovery.failed(applicationId, null,
                    "No repository is bound to this Application for the " + connectorId + " Connector.");
        }

        RepositoryBinding binding = bound.get();
        var locator = new RepositoryLocator(binding.repositoryUrl());
        ConnectorCredential credential = credentialFor(connectorId, binding.repositoryUrl());

        List<SourceRef> refs;
        try {
            refs = connector.readRefs(locator, credential);
        } catch (ConnectorException e) {
            // Taken as-is: a ConnectorException already names the repository it
            // failed on, and prefixing it again produced text in Milestone 2 that
            // said the locator twice in one line.
            return VersionDiscovery.failed(applicationId, binding.repositoryUrl(), e.getMessage());
        } finally {
            credential.clear();
        }

        return interpret(applicationId, binding, refs);
    }

    @Override
    public ConnectionTest checkConnection(String repositoryUrl) {
        String connectorId = connector.connectorId();
        ConnectorCredential credential = credentialFor(connectorId, repositoryUrl);
        // Recorded before the call, because the credential is cleared by it.
        boolean presented = credential.isPresent();
        try {
            connector.checkConnection(new RepositoryLocator(repositoryUrl), credential);
            // ConnectionTest carries a scope because a Deployment Platform has
            // one. A repository does not, so it is stated rather than left as a
            // null a screen would have to render.
            //
            // A public repository reads with no credential, and saying one was
            // accepted would tell a user their token works before they have saved
            // one.
            return presented
                    ? ConnectionTest.reachable(connectorId, repositoryUrl, "whole repository")
                    : ConnectionTest.reachableAnonymously(
                            connectorId, repositoryUrl, "whole repository");
        } catch (ConnectorException e) {
            return ConnectionTest.unreachable(connectorId, repositoryUrl, "whole repository", e.getMessage());
        } finally {
            credential.clear();
        }
    }

    private VersionDiscovery interpret(ApplicationId applicationId, RepositoryBinding binding,
                                       List<SourceRef> refs) {
        // Keyed by version so two refs naming the same version collapse to one
        // candidate rather than offering the user a choice with no difference in
        // it. A tag and a release branch both resolving to 2.5.0 is ordinary.
        Map<String, DiscoveredVersion> byVersion = new LinkedHashMap<>();
        List<String> unmatched = new ArrayList<>();

        for (SourceRef ref : refs) {
            if (!selected(binding, ref)) {
                // Filtered out by the user's own choice of ref kinds. Not
                // reported as unmatched: they asked not to see it, and listing it
                // as a problem would make the setting look broken.
                continue;
            }

            Optional<String> version = binding.resolveVersion(ref.name());
            if (version.isEmpty()) {
                unmatched.add(ref.name());
                continue;
            }

            byVersion.putIfAbsent(version.get(), DiscoveredVersion.fromRef(
                    version.get(),
                    connector.connectorId(),
                    ref.name(),
                    ref.isBranch() ? ref.name() : null,
                    ref.isTag() ? ref.name() : null,
                    ref.commit(),
                    versions.findByApplicationAndVersion(applicationId, version.get()).isPresent()));
        }

        return VersionDiscovery.found(applicationId, binding.repositoryUrl(),
                List.copyOf(byVersion.values()), List.copyOf(unmatched));
    }

    private boolean selected(RepositoryBinding binding, SourceRef ref) {
        return ref.isTag()
                ? binding.refSelection().includesTags()
                : binding.refSelection().includesBranches();
    }

    /**
     * The credential for this repository, or none.
     *
     * <p>A public repository needs no credential, and that is the common case for
     * a Source Control Connector in a way it never is for a cluster — so an
     * absent secret is normal here rather than a misconfiguration.
     */
    private ConnectorCredential credentialFor(String connectorId, String repositoryUrl) {
        return credentials.secretFor(connectorId, repositoryUrl)
                .map(secret -> {
                    try {
                        return ConnectorCredential.bearerToken(secret);
                    } finally {
                        Arrays.fill(secret, '\0');
                    }
                })
                .orElseGet(ConnectorCredential::none);
    }
}
