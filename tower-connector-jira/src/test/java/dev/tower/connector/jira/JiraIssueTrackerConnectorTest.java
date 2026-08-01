package dev.tower.connector.jira;

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
 * ADR-018: reading Jira, and only reading it.
 *
 * <p>Run against a real HTTP server on a loopback port rather than a mocked
 * client, for the reason the GitHub Connector's test gives: what is under test is
 * largely what Tower does with the answers Jira actually gives — a 404 that means
 * two different things, a status category rather than a status name, a body that
 * is not JSON — and a mocked client would only return what this test already
 * assumed.
 *
 * <p>The one thing these tests cannot stand in for is a real Jira. Live
 * verification against a site is recorded as outstanding rather than implied by
 * a green run here.
 */
@DisplayName("Reading work items from Jira")
class JiraIssueTrackerConnectorTest {

    private HttpServer server;
    private JiraIssueTrackerConnector connector;
    private String site;

    /** Every request the server saw, so the read-only claim can be checked. */
    private final List<String> requests = new ArrayList<>();
    private final List<String> authorizations = new ArrayList<>();
    /** The query string of each request, so the fields asked for can be checked. */
    private final List<String> queries = new ArrayList<>();
    private final Map<String, String> bodies = new ConcurrentHashMap<>();
    private final Map<String, Integer> statuses = new ConcurrentHashMap<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            queries.add(exchange.getRequestURI().getQuery());
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            if (header != null) {
                authorizations.add(header);
            }
            String path = exchange.getRequestURI().getPath();
            int status = statuses.getOrDefault(path, bodies.containsKey(path) ? 200 : 404);
            byte[] body = bodies.getOrDefault(path, "{}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        site = "http://127.0.0.1:" + server.getAddress().getPort();
        connector = new JiraIssueTrackerConnector();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    /** A Jira issue as the site answers it, with the status in a category. */
    private void issue(String key, String summary, String status, String category) {
        bodies.put("/rest/api/2/issue/" + key,
                "{\"key\":\"" + key + "\",\"fields\":{\"summary\":\"" + summary + "\","
                        + "\"status\":{\"name\":\"" + status + "\","
                        + "\"statusCategory\":{\"key\":\"" + category + "\"}}}}");
    }

    private List<TrackedIssue> read(String... identifiers) {
        return connector.readIssues(new IssueLocator(site), List.of(identifiers),
                ConnectorCredential.none());
    }

    @Nested
    @DisplayName("reads and never writes")
    class ReadOnly {

        @Test
        void issues_only_GET_requests() {
            // ADR-001, enforced where it can actually be observed: whatever the
            // Connector does, the server sees nothing but GET.
            issue("PROJ-123", "Save basket", "In Progress", "indeterminate");
            bodies.put("/rest/api/2/serverInfo", "{\"version\":\"9.4.0\"}");

            read("PROJ-123");
            connector.checkConnection(new IssueLocator(site), ConnectorCredential.none());

            assertThat(requests).isNotEmpty().allSatisfy(r -> assertThat(r).startsWith("GET "));
        }

        @Test
        void reads_the_summary_status_and_address() {
            issue("PROJ-123", "Save basket", "In Progress", "indeterminate");

            assertThat(read("PROJ-123")).singleElement().satisfies(found -> {
                assertThat(found.identifier()).isEqualTo("PROJ-123");
                assertThat(found.title()).isEqualTo("Save basket");
                assertThat(found.status()).isEqualTo("In Progress");
                assertThat(found.closed()).isFalse();
                assertThat(found.url()).isEqualTo(site + "/browse/PROJ-123");
            });
        }

        @Test
        void asks_for_two_fields_rather_than_the_whole_issue() {
            // A Jira issue document carries every custom field the site defines.
            // Tower reads two values, and the request says so.
            issue("PROJ-123", "Save basket", "In Progress", "indeterminate");

            read("PROJ-123");

            assertThat(lastQuery()).isEqualTo("fields=summary,status");
        }

        @Test
        void reports_the_teams_own_word_for_the_status() {
            // ADR-018: status is the tracker's word, never normalised into a
            // Tower vocabulary. A workflow state nobody else has is still the
            // state this team is in.
            issue("PROJ-9", "Old work", "Ready for QA", "indeterminate");

            assertThat(read("PROJ-9")).singleElement()
                    .extracting(TrackedIssue::status).isEqualTo("Ready for QA");
        }

        @Test
        void takes_jiras_own_answer_for_whether_it_is_finished() {
            // The site's administrator put this status in the done category.
            // Reading the category rather than the name is what keeps Tower from
            // deciding which of a team's states count as finished.
            issue("PROJ-9", "Old work", "Shipped it", "done");

            assertThat(read("PROJ-9")).singleElement().satisfies(found -> {
                assertThat(found.status()).isEqualTo("Shipped it");
                assertThat(found.closed()).isTrue();
            });
        }

        @Test
        void does_not_read_finished_out_of_a_status_that_merely_sounds_finished() {
            // "Done" by name, but the site has it in an unfinished category —
            // a real arrangement on boards with a "Done, pending release" column.
            issue("PROJ-9", "Nearly there", "Done", "indeterminate");

            assertThat(read("PROJ-9")).singleElement()
                    .extracting(TrackedIssue::closed).isEqualTo(false);
        }

        @Test
        void keeps_the_identifier_the_team_wrote() {
            // Tower stores the identifier unchanged, so a lower-case key comes
            // back as the team wrote it even though Jira was asked in upper case.
            issue("PROJ-123", "Save basket", "In Progress", "indeterminate");

            assertThat(read("proj-123")).singleElement()
                    .extracting(TrackedIssue::identifier).isEqualTo("proj-123");
        }
    }

    @Nested
    @DisplayName("distinguishes not-there from cannot-ask")
    class Absence {

        @Test
        void an_issue_the_site_does_not_have_is_omitted_rather_than_invented() {
            issue("PROJ-123", "Save basket", "In Progress", "indeterminate");

            assertThat(read("PROJ-123", "PROJ-999"))
                    .extracting(TrackedIssue::identifier).containsExactly("PROJ-123");
        }

        @Test
        void an_identifier_that_is_not_a_jira_key_is_omitted_rather_than_failing_the_read() {
            // A team that linked GitHub issues and later bound Jira should still
            // see their other items resolve.
            issue("PROJ-123", "Save basket", "In Progress", "indeterminate");

            assertThat(read("#42", "42", "PROJ-123"))
                    .extracting(TrackedIssue::identifier).containsExactly("PROJ-123");
        }

        @Test
        void a_site_that_answers_an_error_fails_loudly() {
            // Not the same as an issue being absent: the whole read failed, and
            // the caller must be able to tell that apart.
            statuses.put("/rest/api/2/issue/PROJ-123", 500);
            bodies.put("/rest/api/2/issue/PROJ-123", "{}");

            assertThatThrownBy(() -> read("PROJ-123"))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("500");
        }

        @Test
        void a_body_that_is_not_json_is_reported_rather_than_half_read() {
            // What a login page looks like from here: HTTP 200, and HTML.
            statuses.put("/rest/api/2/issue/PROJ-123", 200);
            bodies.put("/rest/api/2/issue/PROJ-123", "<html>sign in</html>");

            assertThatThrownBy(() -> read("PROJ-123"))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("not JSON");
        }

        @Test
        void reads_each_key_once_however_often_it_is_named() {
            issue("PROJ-123", "Save basket", "In Progress", "indeterminate");

            read("PROJ-123", "PROJ-123", "proj-123");

            assertThat(requests).filteredOn(r -> r.endsWith("/issue/PROJ-123")).hasSize(2);
        }
    }

    @Nested
    @DisplayName("connection test")
    class Connection {

        @Test
        void asks_a_public_site_only_whether_it_answers() {
            // No credential, so there is nothing to accept: /myself would report
            // an authentication failure for a credential the user never supplied.
            bodies.put("/rest/api/2/serverInfo", "{\"version\":\"9.4.0\"}");

            connector.checkConnection(new IssueLocator(site), ConnectorCredential.none());

            assertThat(requests).containsExactly("GET /rest/api/2/serverInfo");
        }

        @Test
        void proves_a_credential_is_accepted_rather_than_only_that_the_site_is_up() {
            // FR-061 asks whether the credential is accepted. Only an endpoint
            // that requires one can answer that.
            bodies.put("/rest/api/2/myself", "{\"displayName\":\"A person\"}");

            connector.checkConnection(new IssueLocator(site),
                    ConnectorCredential.bearerToken("pat_token".toCharArray()));

            assertThat(requests).containsExactly("GET /rest/api/2/myself");
        }

        @Test
        void says_the_credential_was_refused_and_what_shape_jira_wants() {
            statuses.put("/rest/api/2/myself", 401);

            assertThatThrownBy(() -> connector.checkConnection(new IssueLocator(site),
                    ConnectorCredential.bearerToken("pat_token".toCharArray())))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("refused the credential")
                    .hasMessageContaining("API token");
        }

        @Test
        void says_an_address_may_not_be_a_jira_at_all_when_it_answers_404() {
            assertThatThrownBy(() -> connector.checkConnection(
                    new IssueLocator(site), ConnectorCredential.none()))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("not a Jira site");
        }

        @Test
        void refuses_a_locator_that_is_not_a_site_address() {
            assertThatThrownBy(() -> connector.checkConnection(
                    new IssueLocator("acme/retail"), ConnectorCredential.none()))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("is not a Jira site");
        }

        @Test
        void reads_a_site_written_with_a_trailing_slash() {
            // Pasted out of a browser, which is where site addresses come from.
            bodies.put("/rest/api/2/serverInfo", "{\"version\":\"9.4.0\"}");

            connector.checkConnection(new IssueLocator(site + "/"), ConnectorCredential.none());

            assertThat(requests).containsExactly("GET /rest/api/2/serverInfo");
        }
    }

    @Nested
    @DisplayName("credentials, of which Jira has two shapes")
    class Credentials {

        @Test
        void presents_a_cloud_credential_as_basic() {
            // What Atlassian's own documentation tells a Cloud user to write.
            issue("PROJ-123", "Save basket", "In Progress", "indeterminate");

            connector.readIssues(new IssueLocator(site), List.of("PROJ-123"),
                    ConnectorCredential.bearerToken("person@acme.test:api_token".toCharArray()));

            assertThat(authorizations).singleElement().satisfies(header -> {
                assertThat(header).startsWith("Basic ");
                assertThat(decoded(header)).isEqualTo("person@acme.test:api_token");
            });
        }

        @Test
        void presents_a_data_center_credential_as_a_bearer_token() {
            issue("PROJ-123", "Save basket", "In Progress", "indeterminate");

            connector.readIssues(new IssueLocator(site), List.of("PROJ-123"),
                    ConnectorCredential.bearerToken("pat_token".toCharArray()));

            assertThat(authorizations).containsExactly("Bearer pat_token");
        }

        @Test
        void presents_nothing_at_all_when_no_credential_is_stored() {
            // A public Jira reads without one, and sending an empty header would
            // turn an anonymous read into a refused one.
            issue("PROJ-123", "Save basket", "In Progress", "indeterminate");

            read("PROJ-123");

            assertThat(authorizations).isEmpty();
        }
    }

    @Nested
    @DisplayName("reads the key out of whatever the team wrote")
    class Identifiers {

        @Test
        void accepts_the_forms_teams_actually_use() {
            assertThat(JiraIssueTrackerConnector.keyOf("PROJ-123")).isEqualTo("PROJ-123");
            assertThat(JiraIssueTrackerConnector.keyOf("  PROJ-123  ")).isEqualTo("PROJ-123");
            assertThat(JiraIssueTrackerConnector.keyOf("proj-123")).isEqualTo("PROJ-123");
            assertThat(JiraIssueTrackerConnector.keyOf("https://acme.atlassian.net/browse/PROJ-123"))
                    .isEqualTo("PROJ-123");
            assertThat(JiraIssueTrackerConnector.keyOf(
                    "https://acme.atlassian.net/browse/PROJ-123?filter=-1")).isEqualTo("PROJ-123");
        }

        @Test
        void reports_no_key_for_something_that_has_none() {
            assertThat(JiraIssueTrackerConnector.keyOf("42")).isNull();
            assertThat(JiraIssueTrackerConnector.keyOf("#42")).isNull();
            assertThat(JiraIssueTrackerConnector.keyOf("123-456")).isNull();
            assertThat(JiraIssueTrackerConnector.keyOf("PROJ-")).isNull();
            assertThat(JiraIssueTrackerConnector.keyOf("")).isNull();
            assertThat(JiraIssueTrackerConnector.keyOf(null)).isNull();
        }

        @Test
        void does_not_find_a_key_inside_a_sentence() {
            // The pattern is anchored. A description that mentions a key is not
            // a reference to it, and reading one out would resolve an item the
            // release never claimed to deliver.
            assertThat(JiraIssueTrackerConnector.keyOf("fixes PROJ-123 finally")).isNull();
        }
    }

    @Nested
    @DisplayName("keeps the credential out of everything a reader can see")
    class Secrecy {

        @Test
        void a_failure_message_never_carries_the_token() {
            // NFR-028. The token is in the request object the failure came from,
            // which is exactly why the message is built from the cause instead.
            statuses.put("/rest/api/2/issue/PROJ-123", 500);
            bodies.put("/rest/api/2/issue/PROJ-123", "{}");

            assertThatThrownBy(() -> connector.readIssues(new IssueLocator(site),
                    List.of("PROJ-123"),
                    ConnectorCredential.bearerToken("pat_supersecrettoken".toCharArray())))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageNotContaining("pat_supersecrettoken");
        }

        @Test
        void a_refused_credential_is_reported_without_quoting_it() {
            statuses.put("/rest/api/2/myself", 401);

            assertThatThrownBy(() -> connector.checkConnection(new IssueLocator(site),
                    ConnectorCredential.bearerToken("person@acme.test:supersecret".toCharArray())))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageNotContaining("supersecret");
        }
    }

    private String lastQuery() {
        return queries.isEmpty() ? null : queries.get(queries.size() - 1);
    }

    private static String decoded(String basicHeader) {
        return new String(java.util.Base64.getDecoder().decode(
                basicHeader.substring("Basic ".length())), StandardCharsets.UTF_8);
    }
}
