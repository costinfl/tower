package dev.tower.testkit.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.tower.connector.api.ConnectorCredential;
import dev.tower.connector.api.ConnectorException;
import dev.tower.connector.api.IssueLocator;
import dev.tower.connector.api.IssueTrackerConnector;
import dev.tower.connector.api.TrackedIssue;

/**
 * What every Issue Tracking Connector must do, whoever the vendor is (ADR-019).
 *
 * <p>{@link IssueTrackerConnector} makes its promises in Javadoc: omit an
 * identifier the tracker does not have rather than reporting it, never normalise
 * a status, distinguish "not there" from "could not ask", never let a credential
 * into a message. Prose cannot fail a build. Before this class each Connector
 * re-tested those promises in its own words, unevenly, and a third tracker would
 * have started from nothing.
 *
 * <p>Extend it and a Connector is held to all of them. The subclass supplies only
 * what is vendor-specific: how to point the Connector at the server, what a
 * locator looks like, and how that vendor would write an issue on the wire.
 *
 * <p>What is left in each Connector's own test is what is genuinely its own —
 * how GitHub reads a number out of {@code acme/retail#42}, why a Jira 404 means
 * two things, which credential shape a site wants. This class covers the promises;
 * those tests cover the vendor.
 */
@DisplayName("The contract every Issue Tracking Connector keeps")
public abstract class IssueTrackerConnectorContract {

    /** A credential distinctive enough to find in a message that leaked it. */
    private static final String SECRET = "tower-testkit-credential-must-not-leak";

    protected RecordingHttpServer server;
    private IssueTrackerConnector connector;

    @BeforeEach
    final void startTheTracker() {
        server = RecordingHttpServer.started();
        connector = connectorReading(server);
    }

    @AfterEach
    final void stopTheTracker() {
        server.close();
    }

    // --- what a Connector's own test must supply -----------------------------

    /** The Connector under test, pointed at this server. */
    protected abstract IssueTrackerConnector connectorReading(RecordingHttpServer server);

    /** Where this vendor's Connector would look, on this server. */
    protected abstract IssueLocator locator();

    /**
     * Makes the tracker hold this issue, written the way this vendor writes one.
     *
     * <p>The vendor half of the contract. Whether {@code closed} is a state name,
     * a status category or a timestamp is precisely what differs between
     * trackers, and this is where that difference belongs.
     */
    protected abstract void givenIssue(CannedIssue issue);

    /** Makes a read of this identifier answer with a given status and body. */
    protected abstract void givenTheTrackerAnswers(String identifier, int status, String body);

    /** An identifier in this tracker's scheme that it does not have. */
    protected abstract String anAbsentIdentifier();

    /** An identifier in some other tracker's scheme — "#42" to Jira, "PROJ-1" to GitHub. */
    protected abstract String anIdentifierFromAnotherTracker();

    /** This tracker's own word for work in progress: "open", "In Review". */
    protected abstract String anUnfinishedStatus();

    /** This tracker's own word for work finished: "closed", "Done". */
    protected abstract String aFinishedStatus();

    /** An identifier in this tracker's scheme, for a canned issue. */
    protected abstract String anIdentifier();

    // --- the contract --------------------------------------------------------

    @Test
    @DisplayName("issues nothing but GET, whatever it is asked to do")
    void issues_nothing_but_get() {
        // ADR-001, CM-01, FR-036, observed from the far side of the socket. The
        // architecture tests prove no write path is named; this proves none was
        // taken.
        givenIssue(anOpenIssue());

        read(anIdentifier(), anAbsentIdentifier());

        assertThat(server.requests()).isNotEmpty()
                .allSatisfy(request -> assertThat(request.method()).isEqualTo("GET"));
    }

    @Test
    @DisplayName("omits an identifier the tracker does not have, rather than inventing it")
    void omits_an_absent_identifier() {
        givenIssue(anOpenIssue());

        assertThat(read(anIdentifier(), anAbsentIdentifier()))
                .extracting(TrackedIssue::identifier)
                .containsExactly(anIdentifier());
    }

    @Test
    @DisplayName("an item the tracker does not have is not a failure")
    void absence_is_not_a_failure() {
        // The SPI's central distinction: the caller tells "the tracker does not
        // have this" from "the tracker could not be reached" by whether the read
        // returned or threw. Absence must return.
        assertThat(read(anAbsentIdentifier())).isEmpty();
    }

    @Test
    @DisplayName("omits an identifier from another tracker's scheme rather than failing the read")
    void omits_a_foreign_identifier() {
        // A team that tracked work elsewhere before binding this tracker still
        // has those references on old releases. One of them must not cost them
        // every other item.
        givenIssue(anOpenIssue());

        assertThat(read(anIdentifierFromAnotherTracker(), anIdentifier()))
                .extracting(TrackedIssue::identifier)
                .containsExactly(anIdentifier());
    }

    @Test
    @DisplayName("reports the identifier exactly as the team wrote it")
    void keeps_the_identifier_as_written() {
        // ADR-018: Tower stores what the team wrote. A Connector may need to
        // normalise it to ask the tracker, but what comes back is theirs.
        givenIssue(anOpenIssue());

        assertThat(read(anIdentifier())).singleElement()
                .extracting(TrackedIssue::identifier).isEqualTo(anIdentifier());
    }

    @Test
    @DisplayName("reports the tracker's own word for the status, unnormalised")
    void does_not_normalise_the_status() {
        // ADR-018. Trackers disagree about which states exist, and choosing which
        // of a team's states counts as which is a judgement Tower has no standing
        // to make.
        givenIssue(anOpenIssue());

        assertThat(read(anIdentifier())).singleElement().satisfies(found -> {
            assertThat(found.status()).isEqualTo(anUnfinishedStatus());
            assertThat(found.closed()).isFalse();
        });
    }

    @Test
    @DisplayName("reports finished when the tracker says finished")
    void reports_finished_when_the_tracker_does() {
        // The one thing that is normalised, because it is the one thing every
        // tracker agrees exists. The tracker's own word survives beside it.
        givenIssue(new CannedIssue(anIdentifier(), "Work already done", aFinishedStatus(), true));

        assertThat(read(anIdentifier())).singleElement().satisfies(found -> {
            assertThat(found.closed()).isTrue();
            assertThat(found.status()).isEqualTo(aFinishedStatus());
        });
    }

    @Test
    @DisplayName("reads the title the tracker holds")
    void reads_the_title() {
        givenIssue(anOpenIssue());

        assertThat(read(anIdentifier())).singleElement()
                .extracting(TrackedIssue::title).isEqualTo(anOpenIssue().title());
    }

    @Test
    @DisplayName("fails loudly when the tracker answers an error")
    void a_tracker_error_is_not_mistaken_for_absence() {
        // The other side of absence: the whole read failed, and returning an
        // empty list would tell the caller the items do not exist.
        givenTheTrackerAnswers(anIdentifier(), 500, "{}");

        assertThatThrownBy(() -> read(anIdentifier()))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("500");
    }

    @Test
    @DisplayName("reports a body that is not JSON rather than half-reading it")
    void a_body_that_is_not_json_is_reported() {
        // What a login page or a proxy error looks like from here: HTTP 200, and
        // HTML.
        givenTheTrackerAnswers(anIdentifier(), 200, "<html>sign in</html>");

        assertThatThrownBy(() -> read(anIdentifier()))
                .isInstanceOf(ConnectorException.class);
    }

    @Test
    @DisplayName("reads an identifier once however often it is named")
    void reads_each_identifier_once() {
        givenIssue(anOpenIssue());

        read(anIdentifier(), anIdentifier());

        assertThat(server.requests()).hasSize(1);
    }

    @Test
    @DisplayName("never lets the credential into a failure message")
    void a_failure_message_never_carries_the_credential() {
        // NFR-028, and the promise a new Connector is most likely to drop: the
        // credential is inside the request object the failure came from, so a
        // message built from that object leaks it without anyone meaning to.
        givenTheTrackerAnswers(anIdentifier(), 500, "{}");

        assertThatThrownBy(() -> connector.readIssues(locator(), List.of(anIdentifier()),
                ConnectorCredential.bearerToken(SECRET.toCharArray())))
                .isInstanceOf(ConnectorException.class)
                .hasMessageNotContaining(SECRET);
    }

    @Test
    @DisplayName("presents nothing at all when there is no credential")
    void presents_no_credential_when_there_is_none() {
        // A public tracker reads without one. An empty Authorization header would
        // turn an anonymous read into a refused one.
        givenIssue(anOpenIssue());

        read(anIdentifier());

        assertThat(server.requests())
                .allSatisfy(request -> assertThat(request.authorization()).isNull());
    }

    // --- helpers -------------------------------------------------------------

    private CannedIssue anOpenIssue() {
        return new CannedIssue(anIdentifier(), "Save the basket between visits",
                anUnfinishedStatus(), false);
    }

    private List<TrackedIssue> read(String... identifiers) {
        return connector.readIssues(locator(), List.of(identifiers), ConnectorCredential.none());
    }

    /**
     * An issue as a test wants it, before any vendor has written it down.
     *
     * @param closed what the tracker should say about whether it is finished, in
     *               whatever way that tracker says it
     */
    public record CannedIssue(String identifier, String title, String status, boolean closed) {
    }
}
