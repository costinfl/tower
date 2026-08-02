package dev.tower.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.tower.application.binding.IssueTrackerBinding;
import dev.tower.application.port.out.WorkItemCollector.CollectedWorkItem;
import dev.tower.connector.api.ConnectorCredential;
import dev.tower.connector.api.ConnectorException;
import dev.tower.connector.api.IssueLocator;
import dev.tower.connector.api.IssueTrackerConnector;
import dev.tower.connector.api.TrackedIssue;

/**
 * The Collector between a tracker Connector and the application layer (ADR-018).
 *
 * <p>What is worth testing here is the seam, not the reading: which locator the
 * Connector is handed, whether the credential is cleared, and what happens when
 * no tracker is bound. The reading itself is the Connector's own test.
 */
@DisplayName("Carrying work item identifiers to a tracker")
class IssueTrackerCollectorTest {

    private static final String CONNECTOR_ID = "test-tracker";

    private RecordingConnector connector;
    private CollectorFixtures.InMemoryBindings bindings;
    private CollectorFixtures.InMemoryCredentials credentials;
    private IssueTrackerCollector collector;

    @BeforeEach
    void setUp() {
        connector = new RecordingConnector();
        bindings = new CollectorFixtures.InMemoryBindings();
        credentials = new CollectorFixtures.InMemoryCredentials();
        collector = new IssueTrackerCollector(connector, bindings, credentials);
    }

    private void bindTracker(String locator) {
        bindings.save(new IssueTrackerBinding(CONNECTOR_ID, locator));
    }

    @Nested
    @DisplayName("reading")
    class Reading {

        @Test
        void hands_the_connector_the_locator_the_user_bound() {
            bindTracker("acme/retail");
            connector.answer(new TrackedIssue("42", "Save basket", "open", false, "url"));

            collector.read(List.of("42"));

            assertThat(connector.locatorsAsked).containsExactly("acme/retail");
        }

        @Test
        void passes_the_tracker_answers_straight_through() {
            bindTracker("acme/retail");
            connector.answer(new TrackedIssue("42", "Save basket", "in progress", false,
                    "https://example.invalid/42"));

            assertThat(collector.read(List.of("42"))).containsExactly(
                    new CollectedWorkItem("42", "Save basket", "in progress", false,
                            "https://example.invalid/42"));
        }

        @Test
        void asks_nothing_when_the_release_names_no_work_items() {
            // No binding either: a team that never linked an item has no reason
            // to have configured a tracker, and asking would fail for them.
            assertThat(collector.read(List.of())).isEmpty();
            assertThat(connector.locatorsAsked).isEmpty();
        }

        @Test
        void fails_loudly_when_no_tracker_is_bound() {
            assertThatThrownBy(() -> collector.read(List.of("42")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No tracker is bound");
        }

        @Test
        void reads_without_a_credential_when_none_is_stored() {
            // The ordinary case for a public repository, and not a
            // misconfiguration to be reported.
            bindTracker("acme/retail");
            connector.answer(new TrackedIssue("42", "Save basket", "open", false, "url"));

            collector.read(List.of("42"));

            assertThat(connector.credentialWasPresent).isFalse();
        }

        @Test
        void presents_the_stored_credential_and_clears_it_afterwards() {
            bindTracker("acme/retail");
            credentials.store(CONNECTOR_ID, "acme/retail", "ghp_secret".toCharArray());
            connector.answer(new TrackedIssue("42", "Save basket", "open", false, "url"));

            collector.read(List.of("42"));

            assertThat(connector.credentialWasPresent).isTrue();
            // Cleared rather than left in the heap for as long as the object
            // lives. A modest measure, as the credentials port itself says.
            assertThat(connector.credentialSeen.isPresent()).isFalse();
        }

        @Test
        void lets_a_tracker_failure_reach_the_caller() {
            // WorkItemService contains this and still shows every reference the
            // release carries. Swallowing it here would leave it showing the
            // items as simply unknown to the tracker, which is a different and
            // untrue statement.
            bindTracker("acme/retail");
            connector.failWith("GitHub answered HTTP 503 for acme/retail.");

            assertThatThrownBy(() -> collector.read(List.of("42")))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("503");
        }
    }

    @Nested
    @DisplayName("connection test")
    class Connection {

        @Test
        void reports_reachable_when_the_tracker_answers() {
            bindTracker("acme/retail");

            var test = collector.checkConnection();

            assertThat(test.reachable()).isTrue();
            assertThat(test.connectorId()).isEqualTo(CONNECTOR_ID);
            assertThat(test.target()).isEqualTo("acme/retail");
        }

        @Test
        void does_not_claim_a_credential_was_accepted_when_none_was_presented() {
            // A public tracker reads with no token. Saying one was accepted
            // would tell a user their token works before they have saved one.
            bindTracker("acme/retail");

            assertThat(collector.checkConnection().message())
                    .doesNotContain("credential was accepted")
                    .contains("No credential was presented");
        }

        @Test
        void says_the_credential_was_accepted_when_one_was() {
            bindTracker("acme/retail");
            credentials.store(CONNECTOR_ID, "acme/retail", "ghp_secret".toCharArray());

            assertThat(collector.checkConnection().message())
                    .isEqualTo("Connected and the credential was accepted.");
        }

        @Test
        void reports_rather_than_throws_when_the_tracker_refuses() {
            bindTracker("acme/retail");
            connector.failWith("GitHub refused the credential for acme/retail (HTTP 401).");

            var test = collector.checkConnection();

            assertThat(test.reachable()).isFalse();
            assertThat(test.message()).contains("refused the credential");
        }

        @Test
        void reports_credentials_it_cannot_decrypt_rather_than_failing_the_request() {
            // A master key that no longer matches the credentials file. The
            // screen asking "can you reach this tracker?" should say what is
            // wrong, not answer with a server error that hides the reason.
            bindTracker("acme/retail");
            credentials.failToRead("The stored credential cannot be decrypted with the current key.");

            var test = collector.checkConnection();

            assertThat(test.reachable()).isFalse();
            assertThat(test.message()).contains("cannot be decrypted");
            assertThat(connector.locatorsAsked).isEmpty();
        }

        @Test
        void says_nothing_is_bound_rather_than_reporting_a_network_problem() {
            var test = collector.checkConnection();

            assertThat(test.reachable()).isFalse();
            assertThat(test.message()).isEqualTo("No tracker is bound to this Connector yet.");
            assertThat(connector.locatorsAsked).isEmpty();
        }
    }

    /** A Connector that records what it was asked and answers what it was told to. */
    private static final class RecordingConnector implements IssueTrackerConnector {

        private final List<String> locatorsAsked = new ArrayList<>();
        private final List<TrackedIssue> answers = new ArrayList<>();
        private String failure;
        private boolean credentialWasPresent;
        private ConnectorCredential credentialSeen;

        void answer(TrackedIssue issue) {
            answers.add(issue);
        }

        void failWith(String message) {
            this.failure = message;
        }

        @Override
        public String connectorId() {
            return CONNECTOR_ID;
        }

        @Override
        public List<TrackedIssue> readIssues(IssueLocator locator, List<String> identifiers,
                                             ConnectorCredential credential) {
            record(locator, credential);
            if (failure != null) {
                throw new ConnectorException(failure);
            }
            return List.copyOf(answers);
        }

        @Override
        public void checkConnection(IssueLocator locator, ConnectorCredential credential) {
            record(locator, credential);
            if (failure != null) {
                throw new ConnectorException(failure);
            }
        }

        private void record(IssueLocator locator, ConnectorCredential credential) {
            locatorsAsked.add(locator.value());
            credentialSeen = credential;
            credentialWasPresent = credential.isPresent();
        }
    }

}
