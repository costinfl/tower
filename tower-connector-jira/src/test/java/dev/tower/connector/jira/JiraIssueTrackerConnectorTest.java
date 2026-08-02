package dev.tower.connector.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.tower.connector.api.ConnectorCredential;
import dev.tower.connector.api.ConnectorException;
import dev.tower.connector.api.IssueLocator;
import dev.tower.connector.api.IssueTrackerConnector;
import dev.tower.connector.api.TrackedIssue;
import dev.tower.testkit.connector.IssueTrackerConnectorContract;
import dev.tower.testkit.connector.RecordingHttpServer;
import dev.tower.testkit.connector.VendorDescription;

/**
 * ADR-018: reading Jira, and only reading it.
 *
 * <p>Extends the shared contract, so what the SPI promises is asserted here
 * without being restated here. What is left below is what is genuinely Jira's:
 * two credential shapes and no way to ask a site which it wants, a status
 * category that decides what a status name must not, a 404 that means two things
 * on purpose, and a version-2 path chosen so one Connector serves Cloud, Server
 * and Data Center alike.
 *
 * <p><strong>Nothing here has spoken to a real Jira.</strong> The fixtures are
 * hand-written, unlike GitHub's, because no Atlassian host is reachable from the
 * environment this was built in. A green run means the Connector agrees with what
 * I believe Jira returns. Committing {@code specs/jira/} makes Atlassian's own
 * description the judge of that belief instead; the live check in CHECKLIST.md is
 * the only thing that can settle it entirely, and it is still outstanding.
 */
@DisplayName("Reading work items from Jira")
class JiraIssueTrackerConnectorTest extends IssueTrackerConnectorContract {

    private JiraTracker jira;
    private JiraIssueTrackerConnector connector;
    private String site;

    @Override
    protected IssueTrackerConnector connectorReading(RecordingHttpServer server) {
        jira = new JiraTracker(server);
        site = server.baseUrl();
        connector = new JiraIssueTrackerConnector();
        return connector;
    }

    @Override
    protected IssueLocator locator() {
        return new IssueLocator(site);
    }

    @Override
    protected void givenIssue(CannedIssue issue) {
        jira.givenIssue(issue);
    }

    @Override
    protected void givenTheTrackerAnswers(String identifier, int status, String body) {
        server.answer(jira.pathOf(identifier), status, body);
    }

    @Override
    protected String anIdentifier() {
        return "PROJ-123";
    }

    @Override
    protected String anAbsentIdentifier() {
        return "PROJ-999";
    }

    @Override
    protected String anIdentifierFromAnotherTracker() {
        // A GitHub issue number. Jira keys its issues, so this can name nothing here.
        return "#42";
    }

    @Override
    protected String anUnfinishedStatus() {
        return "In Review";
    }

    @Override
    protected String aFinishedStatus() {
        return "Shipped it";
    }

    @Nested
    @DisplayName("fixtures are shaped the way Jira shapes them")
    class VendorShape {

        @Test
        void the_fixtures_match_atlassians_own_description() {
            // ADR-019, and the check this Connector needs most: unlike GitHub's,
            // these bodies were written from belief rather than recorded from a
            // real response. Until specs/jira/ is committed this skips, and says
            // so, rather than passing and looking like proof.
            assumeTrue(jira.hasDescription(), VendorDescription.absenceOf(JiraTracker.SLICE));

            jira.givenIssue(new CannedIssue("PROJ-1", "Anything", "In Review", false));
            jira.givenTheSiteAnswers();
            jira.givenTheCredentialIsAccepted();
        }
    }

    @Nested
    @DisplayName("reads what a release document needs")
    class Reading {

        @Test
        void reads_the_address_a_reader_can_follow() {
            jira.givenIssue(new CannedIssue("PROJ-123", "Save basket", "In Review", false));

            assertThat(connector.readIssues(locator(), List.of("PROJ-123"), ConnectorCredential.none()))
                    .singleElement()
                    .extracting(TrackedIssue::url)
                    .isEqualTo(site + "/browse/PROJ-123");
        }

        @Test
        void asks_for_two_fields_rather_than_the_whole_issue() {
            // A Jira issue document carries every custom field the site defines.
            // Tower reads two values, and the request says so.
            jira.givenIssue(new CannedIssue("PROJ-123", "Save basket", "In Review", false));

            connector.readIssues(locator(), List.of("PROJ-123"), ConnectorCredential.none());

            assertThat(server.requests()).singleElement()
                    .extracting(RecordingHttpServer.Request::query)
                    .isEqualTo("fields=summary,status");
        }

        @Test
        void does_not_read_finished_out_of_a_status_that_merely_sounds_finished() {
            // "Done" by name, but the site has it in an unfinished category — a
            // real arrangement on boards with a "Done, pending release" column.
            // Reading the name instead of the category would call this shipped.
            jira.givenIssue(new CannedIssue("PROJ-9", "Nearly there", "Done", false));

            assertThat(connector.readIssues(locator(), List.of("PROJ-9"), ConnectorCredential.none()))
                    .singleElement()
                    .extracting(TrackedIssue::closed).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("connection test")
    class Connection {

        @Test
        void asks_a_public_site_only_whether_it_answers() {
            // No credential, so there is nothing to accept: /myself would report
            // an authentication failure for a credential the user never supplied.
            jira.givenTheSiteAnswers();

            connector.checkConnection(locator(), ConnectorCredential.none());

            assertThat(server.requests()).singleElement()
                    .extracting(RecordingHttpServer.Request::path)
                    .isEqualTo("/rest/api/2/serverInfo");
        }

        @Test
        void proves_a_credential_is_accepted_rather_than_only_that_the_site_is_up() {
            // FR-061 asks whether the credential is accepted. Only an endpoint
            // that requires one can answer that.
            jira.givenTheCredentialIsAccepted();

            connector.checkConnection(locator(),
                    ConnectorCredential.bearerToken("pat_token".toCharArray()));

            assertThat(server.requests()).singleElement()
                    .extracting(RecordingHttpServer.Request::path)
                    .isEqualTo("/rest/api/2/myself");
        }

        @Test
        void says_the_credential_was_refused_and_what_shape_jira_wants() {
            server.answer("/rest/api/2/myself", 401, "{}");

            assertThatThrownBy(() -> connector.checkConnection(locator(),
                    ConnectorCredential.bearerToken("pat_token".toCharArray())))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("refused the credential")
                    .hasMessageContaining("API token");
        }

        @Test
        void says_an_address_may_not_be_a_jira_at_all_when_it_answers_404() {
            assertThatThrownBy(() -> connector.checkConnection(locator(), ConnectorCredential.none()))
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
            jira.givenTheSiteAnswers();

            connector.checkConnection(new IssueLocator(site + "/"), ConnectorCredential.none());

            assertThat(server.requests()).singleElement()
                    .extracting(RecordingHttpServer.Request::path)
                    .isEqualTo("/rest/api/2/serverInfo");
        }
    }

    @Nested
    @DisplayName("credentials, of which Jira has two shapes")
    class Credentials {

        @Test
        void presents_a_cloud_credential_as_basic() {
            // What Atlassian's own documentation tells a Cloud user to write.
            jira.givenIssue(new CannedIssue("PROJ-123", "Save basket", "In Review", false));

            connector.readIssues(locator(), List.of("PROJ-123"),
                    ConnectorCredential.bearerToken("person@acme.test:api_token".toCharArray()));

            assertThat(server.requests()).singleElement().satisfies(request -> {
                assertThat(request.authorization()).startsWith("Basic ");
                assertThat(decoded(request.authorization())).isEqualTo("person@acme.test:api_token");
            });
        }

        @Test
        void presents_a_data_center_credential_as_a_bearer_token() {
            jira.givenIssue(new CannedIssue("PROJ-123", "Save basket", "In Review", false));

            connector.readIssues(locator(), List.of("PROJ-123"),
                    ConnectorCredential.bearerToken("pat_token".toCharArray()));

            assertThat(server.requests()).singleElement()
                    .extracting(RecordingHttpServer.Request::authorization)
                    .isEqualTo("Bearer pat_token");
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
            // The pattern is anchored. A description that mentions a key is not a
            // reference to it, and reading one out would resolve an item the
            // release never claimed to deliver.
            assertThat(JiraIssueTrackerConnector.keyOf("fixes PROJ-123 finally")).isNull();
        }

        @Test
        void reports_the_identifier_as_written_even_though_jira_was_asked_in_upper_case() {
            jira.givenIssue(new CannedIssue("PROJ-123", "Save basket", "In Review", false));

            assertThat(connector.readIssues(locator(), List.of("proj-123"), ConnectorCredential.none()))
                    .singleElement()
                    .extracting(TrackedIssue::identifier).isEqualTo("proj-123");
        }
    }

    private static String decoded(String basicHeader) {
        return new String(Base64.getDecoder().decode(
                basicHeader.substring("Basic ".length())), StandardCharsets.UTF_8);
    }
}
