package dev.tower.collector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.tower.application.binding.ApplicationBinding;
import dev.tower.application.binding.EnvironmentBinding;
import dev.tower.application.binding.IssueTrackerBinding;
import dev.tower.application.binding.RepositoryBinding;
import dev.tower.application.port.out.ApplicationVersionRepository;
import dev.tower.application.port.out.ConnectorCredentialsPort;
import dev.tower.application.port.out.CredentialStatus;
import dev.tower.application.port.out.ExternalBindingRepository;
import dev.tower.application.port.out.ObservationRepository;
import dev.tower.connector.api.ConnectorCredential;
import dev.tower.connector.api.ConnectorException;
import dev.tower.connector.api.DeploymentLocator;
import dev.tower.connector.api.DeploymentPlatformConnector;
import dev.tower.connector.api.RunningWorkload;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.observation.Observation;
import dev.tower.domain.observation.ObservationId;

/**
 * In-memory doubles for the Collector's ports.
 *
 * <p>A fake Connector rather than a mock cluster: the point of these tests is the
 * normalization and change comparison, and driving that through a real client
 * would test fabric8. The Kubernetes Connector has its own tests against a mock
 * API server for the part that talks to a platform.
 */
final class CollectorFixtures {

    private CollectorFixtures() {
    }

    /** A Connector whose answers the test dictates, including failures. */
    static final class FakeConnector implements DeploymentPlatformConnector {

        private final String connectorId;
        private final Map<String, List<RunningWorkload>> byScope = new HashMap<>();
        private final Map<String, String> failures = new HashMap<>();
        /** The credential objects, for asserting they were cleared afterwards. */
        final List<ConnectorCredential> credentialsSeen = new ArrayList<>();

        /**
         * Whether each credential carried a token <em>at the moment of the call</em>.
         *
         * <p>Recorded separately because the Collector clears the credential once
         * the call returns. Inspecting the object afterwards answers a different
         * question — was it cleaned up — and cannot tell you what was presented.
         */
        final List<Boolean> credentialPresentAtCall = new ArrayList<>();

        FakeConnector(String connectorId) {
            this.connectorId = connectorId;
        }

        void running(String scope, RunningWorkload... workloads) {
            byScope.put(scope, List.of(workloads));
        }

        void failsFor(String scope, String message) {
            failures.put(scope, message);
        }

        @Override
        public String connectorId() {
            return connectorId;
        }

        @Override
        public List<RunningWorkload> readWorkloads(DeploymentLocator locator, ConnectorCredential credential) {
            credentialsSeen.add(credential);
            credentialPresentAtCall.add(credential.isPresent());
            String failure = failures.get(locator.scope());
            if (failure != null) {
                throw new ConnectorException(failure);
            }
            return byScope.getOrDefault(locator.scope(), List.of());
        }

        @Override
        public void checkConnection(DeploymentLocator locator, ConnectorCredential credential) {
            credentialsSeen.add(credential);
            credentialPresentAtCall.add(credential.isPresent());
            String failure = failures.get(locator.scope());
            if (failure != null) {
                throw new ConnectorException(failure);
            }
        }
    }

    static final class InMemoryBindings implements ExternalBindingRepository {

        // Issue tracker bindings (ADR-018). Keyed by Connector alone, unlike
        // every binding below: a team has one tracker, and it belongs to no
        // single Environment or Application.
        private final Map<String, IssueTrackerBinding> issueTrackerBindings = new HashMap<>();

        @Override
        public IssueTrackerBinding save(IssueTrackerBinding binding) {
            issueTrackerBindings.put(binding.connectorId(), binding);
            return binding;
        }

        @Override
        public Optional<IssueTrackerBinding> findIssueTrackerBinding(String connectorId) {
            return Optional.ofNullable(issueTrackerBindings.get(connectorId));
        }

        @Override
        public List<IssueTrackerBinding> findAllIssueTrackerBindings() {
            return new ArrayList<>(issueTrackerBindings.values());
        }

        @Override
        public void deleteIssueTrackerBinding(String connectorId) {
            issueTrackerBindings.remove(connectorId);
        }


        private final List<EnvironmentBinding> environmentBindings = new ArrayList<>();
        private final List<ApplicationBinding> applicationBindings = new ArrayList<>();

        @Override
        public EnvironmentBinding save(EnvironmentBinding binding) {
            environmentBindings.add(binding);
            return binding;
        }

        @Override
        public ApplicationBinding save(ApplicationBinding binding) {
            applicationBindings.add(binding);
            return binding;
        }

        @Override
        public Optional<EnvironmentBinding> findEnvironmentBinding(EnvironmentId id, String connectorId) {
            return environmentBindings.stream()
                    .filter(b -> b.environmentId().equals(id) && b.connectorId().equals(connectorId))
                    .findFirst();
        }

        @Override
        public List<EnvironmentBinding> findAllEnvironmentBindings(String connectorId) {
            return environmentBindings.stream().filter(b -> b.connectorId().equals(connectorId)).toList();
        }

        @Override
        public List<EnvironmentBinding> findAllEnvironmentBindings() {
            return List.copyOf(environmentBindings);
        }

        @Override
        public void deleteEnvironmentBinding(EnvironmentId id, String connectorId) {
            environmentBindings.removeIf(
                    b -> b.environmentId().equals(id) && b.connectorId().equals(connectorId));
        }

        @Override
        public Optional<ApplicationBinding> findApplicationBinding(ApplicationId id, String connectorId) {
            return applicationBindings.stream()
                    .filter(b -> b.applicationId().equals(id) && b.connectorId().equals(connectorId))
                    .findFirst();
        }

        @Override
        public List<ApplicationBinding> findAllApplicationBindings(String connectorId) {
            return applicationBindings.stream().filter(b -> b.connectorId().equals(connectorId)).toList();
        }

        @Override
        public List<ApplicationBinding> findAllApplicationBindings() {
            return List.copyOf(applicationBindings);
        }

        @Override
        public void deleteApplicationBinding(ApplicationId id, String connectorId) {
            applicationBindings.removeIf(
                    b -> b.applicationId().equals(id) && b.connectorId().equals(connectorId));
        }

        private final Map<String, RepositoryBinding> repositoryBindings = new HashMap<>();

        @Override
        public RepositoryBinding save(RepositoryBinding binding) {
            repositoryBindings.put(binding.applicationId() + "@" + binding.connectorId(), binding);
            return binding;
        }

        @Override
        public Optional<RepositoryBinding> findRepositoryBinding(ApplicationId id, String connectorId) {
            return Optional.ofNullable(repositoryBindings.get(id + "@" + connectorId));
        }

        @Override
        public List<RepositoryBinding> findAllRepositoryBindings(String connectorId) {
            return repositoryBindings.values().stream()
                    .filter(b -> b.connectorId().equals(connectorId)).toList();
        }

        @Override
        public List<RepositoryBinding> findAllRepositoryBindings() {
            return new ArrayList<>(repositoryBindings.values());
        }

        @Override
        public void deleteRepositoryBinding(ApplicationId id, String connectorId) {
            repositoryBindings.remove(id + "@" + connectorId);
        }
    }

    /** Append-only, like the real one: no update, no delete. */
    static final class InMemoryObservations implements ObservationRepository {

        final List<Observation> appended = new ArrayList<>();

        @Override
        public Observation append(Observation observation) {
            appended.add(observation);
            return observation;
        }

        @Override
        public Optional<Observation> findById(ObservationId id) {
            return appended.stream().filter(o -> o.id().equals(id)).findFirst();
        }

        @Override
        public List<Observation> findAllInEnvironment(EnvironmentId environmentId) {
            return appended.stream().filter(o -> o.environmentId().equals(environmentId)).toList();
        }

        @Override
        public List<Observation> findAllOfVersions(java.util.Collection<ApplicationVersionId> versionIds) {
            return appended.stream().filter(o -> versionIds.contains(o.applicationVersionId())).toList();
        }

        @Override
        public List<Observation> findAll() {
            return List.copyOf(appended);
        }
    }

    static final class InMemoryVersions implements ApplicationVersionRepository {

        private final List<ApplicationVersion> stored = new ArrayList<>();

        @Override
        public ApplicationVersion save(ApplicationVersion version) {
            stored.add(version);
            return version;
        }

        @Override
        public Optional<ApplicationVersion> findById(ApplicationVersionId id) {
            return stored.stream().filter(v -> v.id().equals(id)).findFirst();
        }

        @Override
        public List<ApplicationVersion> findAll() {
            return List.copyOf(stored);
        }

        @Override
        public List<ApplicationVersion> findAllByApplication(ApplicationId applicationId) {
            return stored.stream().filter(v -> v.applicationId().equals(applicationId)).toList();
        }

        @Override
        public List<ApplicationVersion> findAllById(List<ApplicationVersionId> ids) {
            return stored.stream().filter(v -> ids.contains(v.id())).toList();
        }

        @Override
        public boolean existsByApplicationAndVersion(ApplicationId applicationId, String version) {
            return findByApplicationAndVersion(applicationId, version).isPresent();
        }

        @Override
        public Optional<ApplicationVersion> findByApplicationAndVersion(ApplicationId applicationId, String version) {
            return stored.stream()
                    .filter(v -> v.applicationId().equals(applicationId) && v.version().equals(version))
                    .findFirst();
        }

        @Override
        public void deleteById(ApplicationVersionId id) {
            stored.removeIf(v -> v.id().equals(id));
        }
    }

    static final class InMemoryCredentials implements ConnectorCredentialsPort {

        private final Map<String, char[]> stored = new HashMap<>();

        /** Set when reading should fail the way a changed master key fails. */
        private String unreadable;

        void failToRead(String message) {
            this.unreadable = message;
        }

        @Override
        public void store(String connectorId, String target, char[] secret) {
            stored.put(connectorId + "@" + target, secret.clone());
        }

        @Override
        public Optional<char[]> secretFor(String connectorId, String target) {
            if (unreadable != null) {
                throw new dev.tower.application.service.CredentialsUnreadableException(unreadable, null);
            }
            return Optional.ofNullable(stored.get(connectorId + "@" + target)).map(char[]::clone);
        }

        @Override
        public CredentialStatus status(String connectorId, String target) {
            return stored.containsKey(connectorId + "@" + target)
                    ? CredentialStatus.configuredAt(connectorId, target, java.time.Instant.EPOCH)
                    : CredentialStatus.absent(connectorId, target);
        }

        @Override
        public void forget(String connectorId, String target) {
            stored.remove(connectorId + "@" + target);
        }
    }
}
