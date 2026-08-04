package dev.tower.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.tower.application.binding.ApplicationBinding;
import dev.tower.application.binding.ArtifactCoordinateBinding;
import dev.tower.application.binding.EnvironmentBinding;
import dev.tower.application.binding.IssueTrackerBinding;
import dev.tower.application.binding.PipelineJobBinding;
import dev.tower.application.binding.RepositoryBinding;
import dev.tower.application.port.in.ArtifactUseCases.Confirmation;
import dev.tower.application.port.in.ArtifactUseCases.State;
import dev.tower.application.port.out.AcceptedArtifactRepository;
import dev.tower.application.port.out.ApplicationVersionRepository;
import dev.tower.application.port.out.ArtifactCollector;
import dev.tower.application.port.out.ExternalBindingRepository;
import dev.tower.application.sync.ConnectionTest;
import dev.tower.domain.application.AcceptedArtifact;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.environment.EnvironmentId;

@DisplayName("Confirming an Application Version's artifacts")
class ArtifactServiceTest {

    private static final String SYSTEM = "https://artifacts.example.com";
    private static final String COMMIT = "abc1234def5678901234567890abcdef12345678";
    private static final String IMAGE = "docker-local/acme/api:2.5.0-abc1234";
    private static final String CHART = "helm-local/api-2.5.0-abc1234.tgz";

    private final ApplicationId applicationId = ApplicationId.newId();
    private final ApplicationVersion version =
            ApplicationVersion.create(applicationId, "2.5.0", null, null, COMMIT, null);

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2024-06-01T09:00:00Z"), ZoneOffset.UTC);

    private InMemoryVersions versions;
    private InMemoryBindings bindings;
    private InMemoryAccepted accepted;
    private StubCollector collector;

    @BeforeEach
    void setUp() {
        versions = new InMemoryVersions();
        versions.save(version);
        bindings = new InMemoryBindings();
        accepted = new InMemoryAccepted();
        collector = new StubCollector();
    }

    private ArtifactService service() {
        return new ArtifactService(versions, bindings, accepted, List.of(collector), CLOCK);
    }

    private void bind(String kind, String template) {
        bindings.save(new ArtifactCoordinateBinding(
                applicationId, "artifactory", kind, SYSTEM, template, 0));
    }

    private Confirmation confirm() {
        return service().confirm(version.id());
    }

    @Nested
    @DisplayName("composes the coordinate and asks")
    class Asking {

        @Test
        void reports_an_artifact_the_repository_holds() {
            bind("image", "docker-local/acme/api:{version}-{shortCommit}");
            collector.holds(IMAGE, "sha256:1111");

            var artifact = confirm().artifacts().get(0);
            assertThat(artifact.state()).isEqualTo(State.PRESENT);
            assertThat(artifact.coordinate()).isEqualTo(IMAGE);
            assertThat(artifact.digest()).isEqualTo("sha256:1111");
        }

        @Test
        void reports_every_kind_the_application_has_a_template_for() {
            // Three kinds on one Application is the ordinary case rather than an
            // edge: a build publishes the application, an image and a chart.
            bind("image", "docker-local/acme/api:{version}-{shortCommit}");
            bind("chart", "helm-local/api-{version}-{shortCommit}.tgz");
            collector.holds(IMAGE, "sha256:1111");
            collector.holds(CHART, "sha1:2222");

            assertThat(confirm().artifacts()).extracting("kind").containsExactly("chart", "image");
        }

        @Test
        void asks_one_repository_once_for_several_coordinates() {
            // Grouping is not only frugal: it is the only way a Connector gets to
            // answer several questions in whatever manner suits it.
            bind("image", "docker-local/acme/api:{version}-{shortCommit}");
            bind("chart", "helm-local/api-{version}-{shortCommit}.tgz");

            confirm();

            assertThat(collector.reads()).hasSize(1);
            assertThat(collector.reads().get(0)).containsExactlyInAnyOrder(IMAGE, CHART);
        }

        @Test
        void asks_nothing_when_the_application_has_no_template() {
            assertThat(confirm().hasTemplates()).isFalse();
            assertThat(collector.reads()).isEmpty();
        }
    }

    @Nested
    @DisplayName("keeps absence apart from silence")
    class Honesty {

        @Test
        void reports_a_coordinate_the_repository_does_not_hold_as_absent() {
            // Ordinary rather than alarming on its own: a build may not have run.
            bind("chart", "helm-local/api-{version}-{shortCommit}.tgz");

            var artifact = confirm().artifacts().get(0);
            assertThat(artifact.state()).isEqualTo(State.ABSENT);
            assertThat(artifact.coordinate()).isEqualTo(CHART);
        }

        @Test
        void reports_a_repository_that_could_not_be_read_as_unread_rather_than_absent() {
            // FR-085 exists for this line. "Not there" and "could not ask" are
            // different facts, and presenting either as the other would send
            // somebody looking for a build that ran, or reassure them about one
            // that did not.
            bind("image", "docker-local/acme/api:{version}-{shortCommit}");
            collector.failsWith("the repository refused the credential");

            var artifact = confirm().artifacts().get(0);
            assertThat(artifact.state()).isEqualTo(State.UNREAD);
            assertThat(artifact.detail()).contains("the repository refused the credential");
        }

        @Test
        void still_lists_every_template_when_the_repository_is_unreachable() {
            // A version does not become less true because a repository is down,
            // and a screen showing nothing would say the opposite.
            bind("image", "docker-local/acme/api:{version}-{shortCommit}");
            bind("chart", "helm-local/api-{version}-{shortCommit}.tgz");
            collector.failsWith("connection refused");

            assertThat(confirm().artifacts()).hasSize(2)
                    .allMatch(artifact -> artifact.state() == State.UNREAD);
        }

        @Test
        void reports_a_template_this_version_cannot_address() {
            // Composing something with a hole in it would ask the repository
            // about a coordinate no build ever wrote.
            ApplicationVersion noCommit =
                    ApplicationVersion.create(applicationId, "2.6.0", null, null, null, null);
            versions.save(noCommit);
            bind("image", "docker-local/acme/api:{version}-{shortCommit}");

            var artifact = service().confirm(noCommit.id()).artifacts().get(0);
            assertThat(artifact.state()).isEqualTo(State.NOT_ADDRESSABLE);
            assertThat(artifact.coordinate()).isNull();
            assertThat(artifact.detail()).contains("carries no commit");
            assertThat(collector.reads()).isEmpty();
        }

        @Test
        void reports_a_connector_that_is_not_installed_rather_than_failing() {
            bindings.save(new ArtifactCoordinateBinding(applicationId, "nexus", "image",
                    SYSTEM, "{version}", 0));

            var artifact = confirm().artifacts().get(0);
            assertThat(artifact.state()).isEqualTo(State.UNREAD);
            assertThat(artifact.detail()).contains("No Connector named 'nexus' is installed.");
        }

        @Test
        void refuses_a_version_that_does_not_exist() {
            assertThatThrownBy(() -> service().confirm(ApplicationVersionId.newId()))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("stores nothing")
    class Storage {

        @Test
        void writes_no_binding_and_no_version_while_confirming() {
            // FR-084 in a test: there is no artifact table in the schema, and
            // this is the assertion that would fail if somebody added one and
            // started filling it.
            bind("image", "docker-local/acme/api:{version}-{shortCommit}");
            collector.holds(IMAGE, "sha256:1111");

            int bindingsBefore = bindings.findAllArtifactCoordinateBindings().size();
            int versionsBefore = versions.findAll().size();

            confirm();

            assertThat(bindings.findAllArtifactCoordinateBindings()).hasSize(bindingsBefore);
            assertThat(versions.findAll()).hasSize(versionsBefore);
        }
    }

    @Nested
    @DisplayName("shows what somebody accepted beside what the repository says")
    class Acceptance {

        @Test
        void records_a_digest_a_person_accepted() {
            bind("image", "docker-local/acme/api:{version}-{shortCommit}");
            collector.holds(IMAGE, "sha256:1111");

            service().accept(new dev.tower.application.port.in.ArtifactUseCases.AcceptDigest(
                    version.id(), "image", IMAGE, "sha256:1111"));

            var artifact = confirm().artifacts().get(0);
            assertThat(artifact.acceptedDigest()).isEqualTo("sha256:1111");
            assertThat(artifact.state()).isEqualTo(State.PRESENT);
        }

        @Test
        void reports_a_tag_that_was_pushed_over() {
            // The fact a team almost never learns any other way, and the reason
            // an artifact is shown beside what Tower holds rather than merely
            // looked up.
            bind("image", "docker-local/acme/api:{version}-{shortCommit}");
            service().accept(new dev.tower.application.port.in.ArtifactUseCases.AcceptDigest(
                    version.id(), "image", IMAGE, "sha256:1111"));
            collector.holds(IMAGE, "sha256:2222");

            var artifact = confirm().artifacts().get(0);
            assertThat(artifact.state()).isEqualTo(State.DIVERGED);
            assertThat(artifact.hasDiverged()).isTrue();
            assertThat(artifact.digest()).isEqualTo("sha256:2222");
            assertThat(artifact.acceptedDigest()).isEqualTo("sha256:1111");
        }

        @Test
        void does_not_correct_the_accepted_digest_when_the_repository_disagrees() {
            // A handover already given to another team does not change because
            // somebody re-published an image (ADR-018's rule, unchanged).
            bind("image", "docker-local/acme/api:{version}-{shortCommit}");
            service().accept(new dev.tower.application.port.in.ArtifactUseCases.AcceptDigest(
                    version.id(), "image", IMAGE, "sha256:1111"));
            collector.holds(IMAGE, "sha256:2222");

            confirm();

            assertThat(accepted.find(version.id(), "image")).get()
                    .extracting(AcceptedArtifact::digest).isEqualTo("sha256:1111");
        }

        @Test
        void claims_no_divergence_for_an_artifact_nobody_accepted() {
            // Nothing to have drifted from, exactly as for a work item reference
            // carrying no accepted title.
            bind("image", "docker-local/acme/api:{version}-{shortCommit}");
            collector.holds(IMAGE, "sha256:2222");

            var artifact = confirm().artifacts().get(0);
            assertThat(artifact.state()).isEqualTo(State.PRESENT);
            assertThat(artifact.acceptedDigest()).isNull();
        }

        @Test
        void shows_an_accepted_digest_even_when_the_repository_cannot_be_read() {
            // What a document prints does not depend on a repository answering.
            bind("image", "docker-local/acme/api:{version}-{shortCommit}");
            service().accept(new dev.tower.application.port.in.ArtifactUseCases.AcceptDigest(
                    version.id(), "image", IMAGE, "sha256:1111"));
            collector.failsWith("connection refused");

            var artifact = confirm().artifacts().get(0);
            assertThat(artifact.state()).isEqualTo(State.UNREAD);
            assertThat(artifact.acceptedDigest()).isEqualTo("sha256:1111");
        }

        @Test
        void accepting_again_replaces_rather_than_appends() {
            bind("image", "docker-local/acme/api:{version}-{shortCommit}");
            service().accept(new dev.tower.application.port.in.ArtifactUseCases.AcceptDigest(
                    version.id(), "image", IMAGE, "sha256:1111"));
            service().accept(new dev.tower.application.port.in.ArtifactUseCases.AcceptDigest(
                    version.id(), "image", IMAGE, "sha256:2222"));

            assertThat(accepted.findAllFor(version.id())).hasSize(1);
        }

        @Test
        void withdraws_an_acceptance_made_in_error() {
            bind("image", "docker-local/acme/api:{version}-{shortCommit}");
            service().accept(new dev.tower.application.port.in.ArtifactUseCases.AcceptDigest(
                    version.id(), "image", IMAGE, "sha256:1111"));

            service().withdrawAcceptance(version.id(), "Image");

            assertThat(accepted.findAllFor(version.id())).isEmpty();
        }

        @Test
        void refuses_a_digest_for_a_version_that_does_not_exist() {
            assertThatThrownBy(() -> service().accept(new dev.tower.application.port.in.ArtifactUseCases.AcceptDigest(
                    ApplicationVersionId.newId(), "image", IMAGE, "sha256:1111")))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void refuses_an_acceptance_carrying_no_digest() {
            // A coordinate on its own names a tag, and a tag can be pushed over.
            assertThatThrownBy(() -> service().accept(new dev.tower.application.port.in.ArtifactUseCases.AcceptDigest(
                    version.id(), "image", IMAGE, "  ")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("digest");
        }
    }

    @Nested
    @DisplayName("tests a connection")
    class Connection {

        @Test
        void reports_a_connector_that_is_not_installed() {
            assertThat(service().testConnection("nexus", SYSTEM).reachable()).isFalse();
        }

        @Test
        void asks_the_connector_when_one_is_installed() {
            assertThat(service().testConnection("artifactory", SYSTEM).reachable()).isTrue();
        }

        @Test
        void refuses_a_test_that_names_no_repository() {
            assertThatThrownBy(() -> service().testConnection("artifactory", " "))
                    .isInstanceOf(InvalidRequestException.class);
        }
    }

    private static final class InMemoryAccepted implements AcceptedArtifactRepository {

        private final Map<String, AcceptedArtifact> stored = new LinkedHashMap<>();

        private static String key(ApplicationVersionId id, String kind) {
            return id + "|" + AcceptedArtifact.normaliseKind(kind);
        }

        @Override
        public AcceptedArtifact save(AcceptedArtifact one) {
            stored.put(key(one.applicationVersionId(), one.kind()), one);
            return one;
        }

        @Override
        public Optional<AcceptedArtifact> find(ApplicationVersionId id, String kind) {
            return Optional.ofNullable(stored.get(key(id, kind)));
        }

        @Override
        public List<AcceptedArtifact> findAllFor(ApplicationVersionId id) {
            return stored.values().stream()
                    .filter(one -> one.applicationVersionId().equals(id)).toList();
        }

        @Override
        public List<AcceptedArtifact> findAllFor(List<ApplicationVersionId> ids) {
            return stored.values().stream()
                    .filter(one -> ids.contains(one.applicationVersionId())).toList();
        }

        @Override
        public void delete(ApplicationVersionId id, String kind) {
            stored.remove(key(id, kind));
        }
    }

    /** Answers about the coordinates it was told to hold, and records what it was asked. */
    private static final class StubCollector implements ArtifactCollector {

        private final Map<String, String> held = new LinkedHashMap<>();
        private final List<List<String>> reads = new ArrayList<>();
        private String failure;

        void holds(String coordinate, String digest) {
            held.put(coordinate, digest);
        }

        void failsWith(String message) {
            this.failure = message;
        }

        List<List<String>> reads() {
            return reads;
        }

        @Override
        public String connectorId() {
            return "artifactory";
        }

        @Override
        public List<ConfirmedArtifact> read(String system, List<String> coordinates) {
            reads.add(List.copyOf(coordinates));
            if (failure != null) {
                throw new IllegalStateException(failure);
            }
            // Omits what it does not have, exactly as the SPI requires.
            return coordinates.stream()
                    .filter(held::containsKey)
                    .map(coordinate -> new ConfirmedArtifact(coordinate, held.get(coordinate),
                            Instant.parse("2024-05-01T10:00:00Z"), 1024,
                            system + "/" + coordinate))
                    .toList();
        }

        @Override
        public ConnectionTest checkConnection(String system) {
            return ConnectionTest.reachable(connectorId(), system, "whole repository");
        }
    }

    private static final class InMemoryVersions implements ApplicationVersionRepository {

        private final Map<ApplicationVersionId, ApplicationVersion> stored = new LinkedHashMap<>();

        @Override
        public ApplicationVersion save(ApplicationVersion version) {
            stored.put(version.id(), version);
            return version;
        }

        @Override
        public Optional<ApplicationVersion> findById(ApplicationVersionId id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public List<ApplicationVersion> findAll() {
            return List.copyOf(stored.values());
        }

        @Override
        public List<ApplicationVersion> findAllByApplication(ApplicationId applicationId) {
            return stored.values().stream()
                    .filter(v -> v.applicationId().equals(applicationId)).toList();
        }

        @Override
        public List<ApplicationVersion> findAllById(List<ApplicationVersionId> ids) {
            return ids.stream().map(stored::get).filter(java.util.Objects::nonNull).toList();
        }

        @Override
        public boolean existsByApplicationAndVersion(ApplicationId applicationId, String version) {
            return findByApplicationAndVersion(applicationId, version).isPresent();
        }

        @Override
        public Optional<ApplicationVersion> findByApplicationAndVersion(
                ApplicationId applicationId, String version) {
            return stored.values().stream()
                    .filter(v -> v.applicationId().equals(applicationId) && v.version().equals(version))
                    .findFirst();
        }

        @Override
        public void deleteById(ApplicationVersionId id) {
            stored.remove(id);
        }
    }

    /**
     * Holds artifact coordinate bindings and refuses everything else.
     *
     * <p>The refusals are deliberate rather than lazy: a service that quietly
     * started reading a repository binding here would pass against a fake that
     * returned empty lists, and fail against the real one.
     */
    private static final class InMemoryBindings implements ExternalBindingRepository {

        private final Map<String, ArtifactCoordinateBinding> artifacts = new LinkedHashMap<>();

        @Override
        public ArtifactCoordinateBinding save(ArtifactCoordinateBinding binding) {
            artifacts.put(binding.applicationId() + "|" + binding.connectorId() + "|" + binding.kind(),
                    binding);
            return binding;
        }

        @Override
        public Optional<ArtifactCoordinateBinding> findArtifactCoordinateBinding(
                ApplicationId applicationId, String connectorId, String kind) {
            return Optional.ofNullable(artifacts.get(applicationId + "|" + connectorId + "|"
                    + ArtifactCoordinateBinding.normaliseKind(kind)));
        }

        @Override
        public List<ArtifactCoordinateBinding> findArtifactCoordinateBindings(ApplicationId applicationId) {
            return artifacts.values().stream()
                    .filter(b -> b.applicationId().equals(applicationId))
                    .sorted(java.util.Comparator.comparing(ArtifactCoordinateBinding::kind))
                    .toList();
        }

        @Override
        public List<ArtifactCoordinateBinding> findAllArtifactCoordinateBindings(String connectorId) {
            return artifacts.values().stream()
                    .filter(b -> b.connectorId().equals(connectorId)).toList();
        }

        @Override
        public List<ArtifactCoordinateBinding> findAllArtifactCoordinateBindings() {
            return List.copyOf(artifacts.values());
        }

        @Override
        public void deleteArtifactCoordinateBinding(
                ApplicationId applicationId, String connectorId, String kind) {
            artifacts.remove(applicationId + "|" + connectorId + "|"
                    + ArtifactCoordinateBinding.normaliseKind(kind));
        }

        private static UnsupportedOperationException notThisTest() {
            return new UnsupportedOperationException(
                    "Confirming an artifact reads artifact coordinate bindings and nothing else.");
        }

        @Override
        public EnvironmentBinding save(EnvironmentBinding binding) {
            throw notThisTest();
        }

        @Override
        public Optional<EnvironmentBinding> findEnvironmentBinding(
                EnvironmentId environmentId, String connectorId) {
            throw notThisTest();
        }

        @Override
        public List<EnvironmentBinding> findAllEnvironmentBindings(String connectorId) {
            throw notThisTest();
        }

        @Override
        public List<EnvironmentBinding> findAllEnvironmentBindings() {
            throw notThisTest();
        }

        @Override
        public void deleteEnvironmentBinding(EnvironmentId environmentId, String connectorId) {
            throw notThisTest();
        }

        @Override
        public ApplicationBinding save(ApplicationBinding binding) {
            throw notThisTest();
        }

        @Override
        public Optional<ApplicationBinding> findApplicationBinding(
                ApplicationId applicationId, String connectorId) {
            throw notThisTest();
        }

        @Override
        public List<ApplicationBinding> findAllApplicationBindings(String connectorId) {
            throw notThisTest();
        }

        @Override
        public List<ApplicationBinding> findAllApplicationBindings() {
            throw notThisTest();
        }

        @Override
        public void deleteApplicationBinding(ApplicationId applicationId, String connectorId) {
            throw notThisTest();
        }

        @Override
        public RepositoryBinding save(RepositoryBinding binding) {
            throw notThisTest();
        }

        @Override
        public Optional<RepositoryBinding> findRepositoryBinding(
                ApplicationId applicationId, String connectorId) {
            throw notThisTest();
        }

        @Override
        public List<RepositoryBinding> findAllRepositoryBindings(String connectorId) {
            throw notThisTest();
        }

        @Override
        public List<RepositoryBinding> findAllRepositoryBindings() {
            throw notThisTest();
        }

        @Override
        public void deleteRepositoryBinding(ApplicationId applicationId, String connectorId) {
            throw notThisTest();
        }

        @Override
        public IssueTrackerBinding save(IssueTrackerBinding binding) {
            throw notThisTest();
        }

        @Override
        public Optional<IssueTrackerBinding> findIssueTrackerBinding(String connectorId) {
            throw notThisTest();
        }

        @Override
        public List<IssueTrackerBinding> findAllIssueTrackerBindings() {
            throw notThisTest();
        }

        @Override
        public void deleteIssueTrackerBinding(String connectorId) {
            throw notThisTest();
        }

        @Override
        public PipelineJobBinding save(PipelineJobBinding binding) {
            throw notThisTest();
        }

        @Override
        public Optional<PipelineJobBinding> findPipelineJobBinding(
                EnvironmentId environmentId, ApplicationId applicationId, String connectorId) {
            throw notThisTest();
        }

        @Override
        public List<PipelineJobBinding> findAllPipelineJobBindings(String connectorId) {
            throw notThisTest();
        }

        @Override
        public List<PipelineJobBinding> findAllPipelineJobBindings() {
            throw notThisTest();
        }

        @Override
        public void deletePipelineJobBinding(
                EnvironmentId environmentId, ApplicationId applicationId, String connectorId) {
            throw notThisTest();
        }
    }
}
