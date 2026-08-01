package dev.tower.connector.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.tower.connector.api.ConnectorCredential;
import dev.tower.connector.api.ConnectorException;
import dev.tower.connector.api.IssueLocator;
import dev.tower.connector.api.TrackedIssue;

/**
 * ADR-018: reading GitHub Issues, and only reading them.
 *
 * <p>Run against a real HTTP server on a loopback port rather than a mocked
 * client. What is under test is largely what Tower does with the answers GitHub
 * actually gives — a 404 for an issue that does not exist, a 404 for a private
 * repository, a body that is not JSON — and a mocked client would only return
 * what this test already assumed.
 */
@DisplayName("Reading work items from GitHub Issues")
class GitHubIssueTrackerConnectorTest {

    private HttpServer server;
    private GitHubIssueTrackerConnector connector;

    /** Every request the server saw, so the read-only claim can be checked. */
    private final List<String> requests = new ArrayList<>();
    private final Map<String, String> bodies = new ConcurrentHashMap<>();
    private final Map<String, Integer> statuses = new ConcurrentHashMap<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            String path = exchange.getRequestURI().getPath();
            int status = statuses.getOrDefault(path, bodies.containsKey(path) ? 200 : 404);
            byte[] body = bodies.getOrDefault(path, "{}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        connector = new GitHubIssueTrackerConnector(
                "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void issue(int number, String title, String state) {
        bodies.put("/repos/acme/retail/issues/" + number,
                "{\"number\":" + number + ",\"title\":\"" + title + "\",\"state\":\"" + state
                        + "\",\"html_url\":\"https://github.com/acme/retail/issues/" + number + "\"}");
    }

    private List<TrackedIssue> read(String... identifiers) {
        return connector.readIssues(new IssueLocator("acme/retail"), List.of(identifiers),
                ConnectorCredential.none());
    }

    @Nested
    @DisplayName("reads and never writes")
    class ReadOnly {

        @Test
        void issues_only_GET_requests() {
            // ADR-001, enforced where it can actually be observed: whatever the
            // Connector does, the server sees nothing but GET.
            issue(42, "Save basket", "open");
            statuses.put("/repos/acme/retail", 200);
            bodies.put("/repos/acme/retail", "{\"full_name\":\"acme/retail\"}");

            read("42");
            connector.checkConnection(new IssueLocator("acme/retail"), ConnectorCredential.none());

            assertThat(requests).isNotEmpty().allSatisfy(r -> assertThat(r).startsWith("GET "));
        }

        @Test
        void reads_the_title_state_and_address() {
            issue(42, "Save basket", "open");

            assertThat(read("42")).singleElement().satisfies(found -> {
                assertThat(found.identifier()).isEqualTo("42");
                assertThat(found.title()).isEqualTo("Save basket");
                assertThat(found.status()).isEqualTo("open");
                assertThat(found.closed()).isFalse();
                assertThat(found.url()).isEqualTo("https://github.com/acme/retail/issues/42");
            });
        }

        @Test
        void reports_closed_using_githubs_own_word_for_the_state() {
            // ADR-018: status is the tracker's word, not normalised into a Tower
            // vocabulary. Only "is it finished" is normalised, because it is the
            // one thing every tracker agrees on.
            issue(7, "Old work", "closed");

            assertThat(read("7")).singleElement().satisfies(found -> {
                assertThat(found.status()).isEqualTo("closed");
                assertThat(found.closed()).isTrue();
            });
        }

        @Test
        void keeps_the_identifier_the_team_wrote() {
            // Tower stores the identifier unchanged, so what comes back must
            // match what went in rather than GitHub's canonical number.
            issue(42, "Save basket", "open");

            assertThat(read("#42")).singleElement()
                    .extracting(TrackedIssue::identifier).isEqualTo("#42");
        }
    }

    @Nested
    @DisplayName("distinguishes not-there from cannot-ask")
    class Absence {

        @Test
        void an_issue_the_repository_does_not_have_is_omitted_rather_than_invented() {
            issue(42, "Save basket", "open");

            assertThat(read("42", "999"))
                    .extracting(TrackedIssue::identifier).containsExactly("42");
        }

        @Test
        void an_identifier_that_is_not_a_number_is_omitted_rather_than_failing_the_read() {
            // A team that linked "PROJ-123" and later bound GitHub should still
            // see their other items resolve.
            issue(42, "Save basket", "open");

            assertThat(read("PROJ-123", "42"))
                    .extracting(TrackedIssue::identifier).containsExactly("42");
        }

        @Test
        void a_repository_that_answers_an_error_fails_loudly() {
            // Not the same as an issue being absent: the whole read failed, and
            // the caller must be able to tell that apart.
            statuses.put("/repos/acme/retail/issues/42", 500);
            bodies.put("/repos/acme/retail/issues/42", "{}");

            assertThatThrownBy(() -> read("42"))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("500");
        }

        @Test
        void a_body_that_is_not_json_is_reported_rather_than_half_read() {
            statuses.put("/repos/acme/retail/issues/42", 200);
            bodies.put("/repos/acme/retail/issues/42", "<html>not json</html>");

            assertThatThrownBy(() -> read("42"))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("not JSON");
        }

        @Test
        void reads_each_identifier_once_however_often_it_is_named() {
            issue(42, "Save basket", "open");

            read("42", "42", "#42");

            assertThat(requests).filteredOn(r -> r.endsWith("/issues/42")).hasSize(2);
        }
    }

    @Nested
    @DisplayName("connection test")
    class Connection {

        @Test
        void passes_when_the_repository_answers() {
            statuses.put("/repos/acme/retail", 200);
            bodies.put("/repos/acme/retail", "{\"full_name\":\"acme/retail\"}");

            connector.checkConnection(new IssueLocator("acme/retail"), ConnectorCredential.none());
        }

        @Test
        void names_both_possibilities_for_a_404_rather_than_asserting_the_wrong_one() {
            // GitHub answers 404 for a private repository a credential cannot
            // see, so "does not exist" would often be false.
            assertThatThrownBy(() -> connector.checkConnection(
                    new IssueLocator("acme/retail"), ConnectorCredential.none()))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("does not exist")
                    .hasMessageContaining("cannot see it");
        }

        @Test
        void says_the_credential_was_refused_when_it_was() {
            statuses.put("/repos/acme/retail", 401);

            assertThatThrownBy(() -> connector.checkConnection(
                    new IssueLocator("acme/retail"), ConnectorCredential.none()))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("refused the credential");
        }

        @Test
        void refuses_a_locator_that_is_not_a_repository() {
            assertThatThrownBy(() -> connector.checkConnection(
                    new IssueLocator("just-a-name"), ConnectorCredential.none()))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("owner/repository");
        }
    }

    @Nested
    @DisplayName("reads the number out of whatever the team wrote")
    class Identifiers {

        @Test
        void accepts_the_forms_teams_actually_use() {
            assertThat(GitHubIssueTrackerConnector.numberOf("42")).isEqualTo("42");
            assertThat(GitHubIssueTrackerConnector.numberOf("#42")).isEqualTo("42");
            assertThat(GitHubIssueTrackerConnector.numberOf("  #42  ")).isEqualTo("42");
            assertThat(GitHubIssueTrackerConnector.numberOf("acme/retail#42")).isEqualTo("42");
        }

        @Test
        void reports_no_number_for_something_that_has_none() {
            assertThat(GitHubIssueTrackerConnector.numberOf("PROJ-123")).isNull();
            assertThat(GitHubIssueTrackerConnector.numberOf("#")).isNull();
            assertThat(GitHubIssueTrackerConnector.numberOf("")).isNull();
            assertThat(GitHubIssueTrackerConnector.numberOf(null)).isNull();
        }
    }

    @Nested
    @DisplayName("keeps the credential out of everything a reader can see")
    class Secrecy {

        @Test
        void a_failure_message_never_carries_the_token() {
            // NFR-028. The token is in the request object the failure came from,
            // which is exactly why the message is built from the cause instead.
            statuses.put("/repos/acme/retail/issues/42", 500);
            bodies.put("/repos/acme/retail/issues/42", "{}");
            var credential = ConnectorCredential.bearerToken("ghp_supersecrettoken".toCharArray());

            assertThatThrownBy(() -> connector.readIssues(
                    new IssueLocator("acme/retail"), List.of("42"), credential))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageNotContaining("ghp_supersecrettoken");
        }
    }
}
