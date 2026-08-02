package dev.tower.connector.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
 * ADR-018: reading GitHub Issues, and only reading them.
 *
 * <p>Extends the shared contract, so everything the SPI promises — absence is not
 * an error, a status is never normalised, a credential never reaches a message —
 * is asserted here without being restated here. What is left below is what is
 * genuinely GitHub's own: how it writes an issue number, that a 404 means two
 * different things, what its connection test can and cannot conclude.
 *
 * <p>Run against a real HTTP server on a loopback port rather than a mocked
 * client, and served bodies recorded from the real api.github.com. A mocked
 * client could only return what this test already assumed.
 */
@DisplayName("Reading work items from GitHub Issues")
class GitHubIssueTrackerConnectorTest extends IssueTrackerConnectorContract {

    private GitHubTracker github;
    private GitHubIssueTrackerConnector connector;

    @Override
    protected IssueTrackerConnector connectorReading(RecordingHttpServer server) {
        github = new GitHubTracker(server);
        connector = new GitHubIssueTrackerConnector(server.baseUrl());
        return connector;
    }

    @Override
    protected IssueLocator locator() {
        return new IssueLocator(GitHubTracker.REPOSITORY);
    }

    @Override
    protected void givenIssue(CannedIssue issue) {
        github.givenIssue(issue);
    }

    @Override
    protected void givenTheTrackerAnswers(String identifier, int status, String body) {
        server.answer(github.pathOf(identifier), status, body);
    }

    @Override
    protected String anIdentifier() {
        return "42";
    }

    @Override
    protected String anAbsentIdentifier() {
        return "999";
    }

    @Override
    protected String anIdentifierFromAnotherTracker() {
        // A Jira key. GitHub numbers its issues, so this can name nothing here.
        return "PROJ-123";
    }

    @Override
    protected String anUnfinishedStatus() {
        return "open";
    }

    @Override
    protected String aFinishedStatus() {
        return "closed";
    }

    @Nested
    @DisplayName("fixtures are shaped the way GitHub shapes them")
    class VendorShape {

        @Test
        void the_recorded_responses_still_match_githubs_own_description() {
            // ADR-019. The recordings came from the real API; this says they are
            // still what GitHub's published description says that API answers, so
            // a fixture cannot quietly drift into a shape GitHub never produces.
            assumeTrue(github.hasDescription(), VendorDescription.absenceOf(GitHubTracker.SLICE));

            github.requireRecordingsAreStillVendorShaped();
        }
    }

    @Nested
    @DisplayName("reads what a release document needs")
    class Reading {

        @Test
        void reads_the_address_a_reader_can_follow() {
            github.givenIssue(new CannedIssue("42", "Save basket", "open", false));

            assertThat(connector.readIssues(locator(), List.of("42"), ConnectorCredential.none()))
                    .singleElement()
                    .extracting(TrackedIssue::url)
                    .isEqualTo("https://github.com/acme/retail/issues/42");
        }
    }

    @Nested
    @DisplayName("connection test")
    class Connection {

        @Test
        void passes_when_the_repository_answers() {
            github.givenTheRepository();

            connector.checkConnection(locator(), ConnectorCredential.none());
        }

        @Test
        void names_both_possibilities_for_a_404_rather_than_asserting_the_wrong_one() {
            // GitHub answers 404 for a private repository a credential cannot
            // see, so "does not exist" would often be false.
            assertThatThrownBy(() -> connector.checkConnection(locator(), ConnectorCredential.none()))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("does not exist")
                    .hasMessageContaining("cannot see it");
        }

        @Test
        void says_the_credential_was_refused_when_it_was() {
            server.answer("/repos/" + GitHubTracker.REPOSITORY, 401, "{}");

            assertThatThrownBy(() -> connector.checkConnection(locator(), ConnectorCredential.none()))
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

        @Test
        void reports_the_identifier_as_written_even_when_it_had_to_be_read_first() {
            // "#42" is asked for as 42 and reported back as "#42". The contract
            // asserts this for the plain form; this is the one that was parsed.
            github.givenIssue(new CannedIssue("42", "Save basket", "open", false));

            assertThat(connector.readIssues(locator(), List.of("#42"), ConnectorCredential.none()))
                    .singleElement()
                    .extracting(TrackedIssue::identifier).isEqualTo("#42");
        }
    }
}
