package dev.tower.connector.jira;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.tower.testkit.connector.IssueTrackerConnectorContract.CannedIssue;
import dev.tower.testkit.connector.RecordingHttpServer;
import dev.tower.testkit.connector.VendorDescription;

/**
 * Jira's half of the fake: where it puts an issue, and what one looks like.
 *
 * <p><strong>These fixtures are hand-written, and that is a weaker footing than
 * GitHub's.</strong> The GitHub Connector's bodies were recorded from the real
 * API; nothing here has ever come from a Jira. No Atlassian host is reachable
 * from the environment Tower is developed in, so what is below is what I believe
 * Jira returns — which is exactly the belief that wrote the Connector, and
 * exactly why ADR-019 exists.
 *
 * <p>The check that makes them worth anything is
 * {@link VendorDescription}: once {@code specs/jira/} is committed, every body
 * served here is measured against Atlassian's own description before it is used,
 * and a field invented from memory fails the build. Until then the tests that
 * depend on it skip with a stated reason, and the live check in CHECKLIST.md is
 * the only thing that has ever spoken to a real Jira. Which is to say: nothing
 * has.
 */
final class JiraTracker {

    static final String SLICE = "specs/jira/jira-cloud.slice.json";

    /**
     * The path as Atlassian's description writes it.
     *
     * <p>Version 3 where the Connector reads version 2, deliberately: Atlassian
     * publishes a description of v3 only, and the two agree on summary and status
     * — which is the whole reason ADR-018 chose v2, the one version Cloud, Server
     * and Data Center all serve. specs/README.md records the caveat.
     */
    static final String ISSUE_PATH = "/rest/api/3/issue/{issueIdOrKey}";
    static final String SERVER_INFO_PATH = "/rest/api/3/serverInfo";
    static final String MYSELF_PATH = "/rest/api/3/myself";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RecordingHttpServer server;
    private final Optional<VendorDescription> jira = VendorDescription.at(SLICE);

    JiraTracker(RecordingHttpServer server) {
        this.server = server;
    }

    /** Puts an issue where Jira would keep it, in the shape Jira returns. */
    void givenIssue(CannedIssue issue) {
        ObjectNode body = fixture("issue.json");
        body.put("key", issue.identifier().toUpperCase(Locale.ROOT));
        ObjectNode fields = (ObjectNode) body.get("fields");
        fields.put("summary", issue.title());

        ObjectNode status = (ObjectNode) fields.get("status");
        status.put("name", issue.status());
        // Jira decides whether an issue is finished by which category its site
        // administrator put the status in, never by the status name. The fixture
        // has to say it the same way, or the Connector would be tested against a
        // shape Jira does not produce.
        ((ObjectNode) status.get("statusCategory"))
                .put("key", issue.closed() ? "done" : "indeterminate");

        answerWithChecked(pathOf(issue.identifier()), ISSUE_PATH, body);
    }

    /** The site answering anonymously, for the connection test. */
    void givenTheSiteAnswers() {
        answerWithChecked("/rest/api/2/serverInfo", SERVER_INFO_PATH, fixture("server-info.json"));
    }

    /** The site accepting a credential, for the connection test that presents one. */
    void givenTheCredentialIsAccepted() {
        answerWithChecked("/rest/api/2/myself", MYSELF_PATH, fixture("myself.json"));
    }

    String pathOf(String identifier) {
        return "/rest/api/2/issue/" + identifier.toUpperCase(Locale.ROOT);
    }

    boolean hasDescription() {
        return jira.isPresent();
    }

    private void answerWithChecked(String path, String template, ObjectNode body) {
        String json = write(body);
        jira.ifPresent(description -> description.requireResponseMatches(template, 200, json));
        server.answer(path, json);
    }

    private ObjectNode fixture(String name) {
        try (var stream = JiraTracker.class.getResourceAsStream("/fixtures/" + name)) {
            if (stream == null) {
                throw new IllegalStateException("fixtures/" + name + " is not on the test classpath.");
            }
            return (ObjectNode) JSON.readTree(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String write(ObjectNode body) {
        try {
            return JSON.writeValueAsString(body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
