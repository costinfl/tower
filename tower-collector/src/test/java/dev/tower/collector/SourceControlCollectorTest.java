package dev.tower.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.tower.application.binding.RepositoryBinding;
import dev.tower.application.binding.RepositoryBinding.RefSelection;
import dev.tower.application.discovery.DiscoveredVersion;
import dev.tower.application.discovery.VersionDiscovery;
import dev.tower.connector.api.ConnectorCredential;
import dev.tower.connector.api.ConnectorException;
import dev.tower.connector.api.RepositoryLocator;
import dev.tower.connector.api.SourceControlConnector;
import dev.tower.connector.api.SourceRef;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersion;

/**
 * Issue #3, ADR-014.
 *
 * <p>A fake Connector rather than a real repository: what is under test here is
 * the interpretation — applying the binding, matching the pattern, marking what
 * Tower already holds — and driving that through JGit would test JGit. The git
 * Connector has its own tests against a real repository on disk.
 */
@DisplayName("The Source Control Collector")
class SourceControlCollectorTest {

    private static final String URL = "https://example.com/acme/api.git";

    private ApplicationId application;
    private FakeSourceConnector connector;
    private CollectorFixtures.InMemoryBindings bindings;
    private CollectorFixtures.InMemoryVersions versions;
    private CollectorFixtures.InMemoryCredentials credentials;
    private SourceControlCollector collector;

    @BeforeEach
    void setUp() {
        application = ApplicationId.newId();
        connector = new FakeSourceConnector("git");
        bindings = new CollectorFixtures.InMemoryBindings();
        versions = new CollectorFixtures.InMemoryVersions();
        credentials = new CollectorFixtures.InMemoryCredentials();
        collector = new SourceControlCollector(connector, bindings, versions, credentials);
    }

    private void bind(RefSelection selection, String pattern) {
        bindings.save(new RepositoryBinding(application, "git", URL, selection, pattern));
    }

    @Nested
    @DisplayName("applies the binding to what the repository holds")
    class Interpreting {

        @Test
        void offers_a_tag_that_matches_the_pattern_as_a_candidate() {
            bind(RefSelection.TAGS, "^v(.+)$");
            connector.refs(SourceRef.tag("v2.5.0", "abc123"));

            VersionDiscovery discovery = collector.discover(application);

            assertThat(discovery.succeeded()).isTrue();
            assertThat(discovery.candidates()).singleElement().satisfies(candidate -> {
                assertThat(candidate.version()).isEqualTo("2.5.0");
                assertThat(candidate.origin()).isEqualTo("v2.5.0");
                // A ref says which system proposed it, so a build candidate
                // beside it in the same list is distinguishable (ADR-020).
                assertThat(candidate.source()).isEqualTo("git");
                // git has no build identifier and ADR-014 refuses to derive one.
                assertThat(candidate.buildIdentifier()).isNull();
                assertThat(candidate.tag()).isEqualTo("v2.5.0");
                assertThat(candidate.branch()).isNull();
                assertThat(candidate.commit()).isEqualTo("abc123");
            });
        }

        @Test
        void reports_a_ref_the_pattern_does_not_recognise_rather_than_dropping_it() {
            // A user whose pattern is wrong needs to see what it failed on.
            bind(RefSelection.TAGS, "^v(.+)$");
            connector.refs(SourceRef.tag("v2.5.0", "abc123"), SourceRef.tag("sandbox", "def456"));

            VersionDiscovery discovery = collector.discover(application);

            assertThat(discovery.candidates()).hasSize(1);
            assertThat(discovery.unmatched()).containsExactly("sandbox");
        }

        @Test
        void does_not_report_a_ref_the_user_chose_not_to_read() {
            // Filtered by their own setting, so listing it as a problem would
            // make the setting look broken.
            bind(RefSelection.TAGS, null);
            connector.refs(SourceRef.tag("2.5.0", "abc"), SourceRef.branch("feature/x", "def"));

            VersionDiscovery discovery = collector.discover(application);

            assertThat(discovery.candidates()).extracting(DiscoveredVersion::version)
                    .containsExactly("2.5.0");
            assertThat(discovery.unmatched()).isEmpty();
        }

        @Test
        void reads_branches_when_the_binding_says_so() {
            bind(RefSelection.BRANCHES, "^release/(.+)$");
            connector.refs(SourceRef.branch("release/2.5", "abc"), SourceRef.tag("v9.9.9", "zzz"));

            VersionDiscovery discovery = collector.discover(application);

            assertThat(discovery.candidates()).singleElement().satisfies(candidate -> {
                assertThat(candidate.version()).isEqualTo("2.5");
                assertThat(candidate.branch()).isEqualTo("release/2.5");
                assertThat(candidate.tag()).isNull();
            });
        }

        @Test
        void collapses_two_refs_naming_the_same_version_into_one_candidate() {
            // A tag and a release branch both resolving to 2.5.0 is ordinary, and
            // offering the user a choice with no difference in it is not helpful.
            bind(RefSelection.ALL, "^(?:v|release/)(.+)$");
            connector.refs(SourceRef.tag("v2.5.0", "abc"), SourceRef.branch("release/2.5.0", "abc"));

            assertThat(collector.discover(application).candidates()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("respects BR-01 — it never revises what Tower already holds")
    class Immutability {

        @Test
        void marks_a_version_tower_already_has_rather_than_hiding_it() {
            bind(RefSelection.TAGS, "^v(.+)$");
            connector.refs(SourceRef.tag("v2.5.0", "abc"), SourceRef.tag("v2.6.0", "def"));
            versions.save(ApplicationVersion.create(application, "2.5.0", null, null, null, null));

            VersionDiscovery discovery = collector.discover(application);

            assertThat(discovery.candidates()).hasSize(2);
            assertThat(discovery.newVersions()).extracting(DiscoveredVersion::version)
                    .containsExactly("2.6.0");
        }

        @Test
        void stores_nothing_at_all() {
            // The rule that separates this Collector from the deployment one. A
            // version created without anyone asking would be immutable too.
            bind(RefSelection.TAGS, null);
            connector.refs(SourceRef.tag("2.5.0", "abc"));

            collector.discover(application);

            assertThat(versions.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("says why it could not look, rather than looking empty")
    class Failures {

        @Test
        void an_unbound_application_says_so_instead_of_returning_nothing() {
            VersionDiscovery discovery = collector.discover(application);

            assertThat(discovery.succeeded()).isFalse();
            assertThat(discovery.failure()).contains("No repository is bound");
        }

        @Test
        void an_unreadable_repository_carries_the_connectors_own_message() {
            bind(RefSelection.TAGS, null);
            connector.failWith("Could not read " + URL + ": not authorized");

            VersionDiscovery discovery = collector.discover(application);

            assertThat(discovery.succeeded()).isFalse();
            assertThat(discovery.failure()).isEqualTo("Could not read " + URL + ": not authorized");
            assertThat(discovery.repositoryUrl()).isEqualTo(URL);
        }

        @Test
        void an_empty_repository_is_a_success_that_found_nothing() {
            // Distinct from a failure, and the distinction is the point: these
            // lead to opposite next steps.
            bind(RefSelection.TAGS, null);
            connector.refs();

            VersionDiscovery discovery = collector.discover(application);

            assertThat(discovery.succeeded()).isTrue();
            assertThat(discovery.foundNothing()).isTrue();
        }
    }

    @Nested
    @DisplayName("handles credentials the way the deployment Collector does")
    class Credentials {

        @Test
        void presents_a_stored_credential_and_clears_it_afterwards() {
            bind(RefSelection.TAGS, null);
            connector.refs(SourceRef.tag("2.5.0", "abc"));
            credentials.store("git", URL, "a-secret-token".toCharArray());

            collector.discover(application);

            assertThat(connector.credentialPresentAtCall).containsExactly(true);
            assertThat(connector.credentialsSeen).singleElement()
                    .satisfies(seen -> assertThat(seen.isPresent()).isFalse());
        }

        @Test
        void presents_none_for_a_public_repository() {
            // Normal here in a way it never is for a cluster.
            bind(RefSelection.TAGS, null);
            connector.refs(SourceRef.tag("2.5.0", "abc"));

            collector.discover(application);

            assertThat(connector.credentialPresentAtCall).containsExactly(false);
        }

        @Test
        void a_connection_test_presents_the_credential_too() {
            credentials.store("git", URL, "a-secret-token".toCharArray());

            var result = collector.checkConnection(URL);

            assertThat(result.reachable()).isTrue();
            assertThat(connector.credentialPresentAtCall).containsExactly(true);
        }

        @Test
        void a_connection_test_reports_an_unreachable_repository_rather_than_throwing() {
            connector.failWith("Could not read " + URL + ": not found");

            var result = collector.checkConnection(URL);

            assertThat(result.reachable()).isFalse();
            assertThat(result.message()).contains("not found");
        }
    }

    /** A Connector whose answers the test dictates. */
    private static final class FakeSourceConnector implements SourceControlConnector {

        private final String connectorId;
        private List<SourceRef> refs = List.of();
        private String failure;
        final List<ConnectorCredential> credentialsSeen = new ArrayList<>();
        /** Whether each credential carried a token at the moment of the call. */
        final List<Boolean> credentialPresentAtCall = new ArrayList<>();

        FakeSourceConnector(String connectorId) {
            this.connectorId = connectorId;
        }

        void refs(SourceRef... refs) {
            this.refs = List.of(refs);
        }

        void failWith(String message) {
            this.failure = message;
        }

        @Override
        public String connectorId() {
            return connectorId;
        }

        @Override
        public List<SourceRef> readRefs(RepositoryLocator locator, ConnectorCredential credential) {
            record(credential);
            if (failure != null) {
                throw new ConnectorException(failure);
            }
            return refs;
        }

        @Override
        public void checkConnection(RepositoryLocator locator, ConnectorCredential credential) {
            record(credential);
            if (failure != null) {
                throw new ConnectorException(failure);
            }
        }

        private void record(ConnectorCredential credential) {
            credentialsSeen.add(credential);
            credentialPresentAtCall.add(credential != null && credential.isPresent());
        }
    }
}
