package dev.tower.connector.github;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.tower.testkit.connector.IssueTrackerConnectorContract.CannedIssue;
import dev.tower.testkit.connector.RecordingHttpServer;
import dev.tower.testkit.connector.VendorDescription;

/**
 * GitHub's half of the fake: where it puts an issue, and what one looks like.
 *
 * <p>The bodies are not written by hand. They start from a response recorded from
 * the real api.github.com — {@code fixtures/issue.json}, taken from this
 * repository's own issue #3 — and only the fields a test is actually about are
 * changed. A hand-written body would carry my beliefs about GitHub, which are
 * the same beliefs that wrote the Connector, so a test built on one could only
 * confirm that the two agree (ADR-019).
 *
 * <p>Every body is checked against GitHub's own published description before it
 * is served. That check is here rather than in a test of its own so that it
 * cannot be forgotten: a fixture that has drifted out of shape fails at the
 * moment it is used, in whichever test used it.
 */
final class GitHubTracker {

    static final String SLICE = "specs/github/api.github.com.slice.json";
    static final String REPOSITORY = "acme/retail";
    static final String ISSUE_PATH = "/repos/{owner}/{repo}/issues/{issue_number}";
    static final String REPOSITORY_PATH = "/repos/{owner}/{repo}";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RecordingHttpServer server;
    private final Optional<VendorDescription> github = VendorDescription.at(SLICE);

    GitHubTracker(RecordingHttpServer server) {
        this.server = server;
    }

    /** Puts an issue where GitHub would keep it, in the shape GitHub returns. */
    void givenIssue(CannedIssue issue) {
        ObjectNode body = recorded("issue.json");
        body.put("number", Integer.parseInt(issue.identifier()));
        body.put("title", issue.title());
        body.put("state", issue.status());
        body.put("html_url", "https://github.com/" + REPOSITORY + "/issues/" + issue.identifier());
        // GitHub says an issue is finished by carrying a closing timestamp as
        // well as the state. Both are set, because a fixture that said "closed"
        // with no closed_at would be a shape GitHub never produces.
        if (issue.closed()) {
            body.put("closed_at", "2026-01-15T09:30:00Z");
        } else {
            body.putNull("closed_at");
            body.putNull("closed_by");
            body.putNull("state_reason");
        }
        answerWithChecked(pathOf(issue.identifier()), ISSUE_PATH, body);
    }

    /** Puts the repository itself there, for the connection test. */
    void givenTheRepository() {
        answerWithChecked("/repos/" + REPOSITORY, REPOSITORY_PATH, recorded("repository.json"));
    }

    String pathOf(String identifier) {
        return "/repos/" + REPOSITORY + "/issues/" + identifier;
    }

    private void answerWithChecked(String path, String template, ObjectNode body) {
        String json = write(body);
        // Skipped rather than passed when the description is not committed: see
        // VendorDescription.absenceOf, and specs/README.md.
        github.ifPresent(description -> description.requireResponseMatches(template, 200, json));
        server.answer(path, json);
    }

    /**
     * Confirms a recorded fixture is still what GitHub says it answers.
     *
     * <p>Separate from the substitution above so that the recording itself is
     * checked, not only the version a test has edited.
     */
    void requireRecordingsAreStillVendorShaped() {
        assertThat(github)
                .as(VendorDescription.absenceOf(SLICE))
                .isPresent();
        github.get().requireResponseMatches(ISSUE_PATH, 200, write(recorded("issue.json")));
        github.get().requireResponseMatches(REPOSITORY_PATH, 200, write(recorded("repository.json")));
    }

    boolean hasDescription() {
        return github.isPresent();
    }

    private ObjectNode recorded(String name) {
        try (var stream = GitHubTracker.class.getResourceAsStream("/fixtures/" + name)) {
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
