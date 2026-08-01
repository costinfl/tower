package dev.tower.connector.github;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.tower.connector.api.ConnectorCredential;
import dev.tower.connector.api.ConnectorException;
import dev.tower.connector.api.IssueLocator;
import dev.tower.connector.api.IssueTrackerConnector;
import dev.tower.connector.api.TrackedIssue;

/**
 * Reads work items from GitHub Issues (ADR-018).
 *
 * <p>Read-only, and structurally so: the only HTTP method this class can issue
 * is GET. There is no code path here that opens, closes, comments on or labels
 * anything, and Guardrails.md lists creating or updating issues among the things
 * Tower must never do.
 *
 * <p>No GitHub SDK, for the same reason ADR-014 reads git rather than a vendor
 * API: what Tower needs is three fields deep — a title, a state and a URL — and
 * the JDK's own HTTP client reads that in a few lines. An SDK would pull a large
 * dependency tree to save nothing, and would put the whole of GitHub's model
 * within easy reach of code that has no business holding it.
 *
 * <p>Produces no Observations. That is not an omission but the category: this
 * Connector resolves references a developer already stated, and ADR-018 records
 * why an issue is not something Tower observed.
 */
@Component
public class GitHubIssueTrackerConnector implements IssueTrackerConnector {

    public static final String CONNECTOR_ID = "github-issues";

    /**
     * Where the API lives.
     *
     * <p>Overridable so a GitHub Enterprise instance can be reached, and so the
     * tests can point at a local server rather than the internet.
     */
    private final String apiBase;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public GitHubIssueTrackerConnector() {
        this("https://api.github.com");
    }

    public GitHubIssueTrackerConnector(String apiBase) {
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                // Redirects are followed, but only ever for a GET: this client
                // issues nothing else.
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String connectorId() {
        return CONNECTOR_ID;
    }

    /**
     * Reads the named issues, one request each.
     *
     * <p>One request per identifier rather than a list query. A release names a
     * handful of items, and asking for exactly those is both simpler and more
     * honest than paging every issue in a repository and filtering: a filtered
     * list cannot tell "this issue does not exist" apart from "it was not on the
     * page I read".
     *
     * <p>Duplicates in the input are read once. An identifier the repository does
     * not have is omitted rather than reported, per the SPI contract — the caller
     * distinguishes that from an unreachable tracker by whether this returned.
     */
    @Override
    public List<TrackedIssue> readIssues(IssueLocator locator, List<String> identifiers,
                                         ConnectorCredential credential) {
        String repository = repositoryOf(locator);
        Set<String> unique = new LinkedHashSet<>(identifiers);

        List<TrackedIssue> found = new ArrayList<>();
        for (String identifier : unique) {
            String number = numberOf(identifier);
            if (number == null) {
                // Not something GitHub could have a number for. Omitted like any
                // other identifier the tracker does not have, rather than failing
                // the whole read for one malformed reference.
                continue;
            }
            issue(repository, number, identifier, credential).ifPresent(found::add);
        }
        return found;
    }

    @Override
    public void checkConnection(IssueLocator locator, ConnectorCredential credential) {
        String repository = repositoryOf(locator);
        HttpResponse<String> response = get(
                address(apiBase + "/repos/" + repository), credential,
                "Could not reach GitHub for " + repository);

        if (response.statusCode() == 404) {
            // 404 is what GitHub returns for a private repository the credential
            // cannot see, so the message names both possibilities rather than
            // asserting the wrong one.
            throw new ConnectorException("GitHub returned 404 for " + repository
                    + ". Either the repository does not exist, or the credential cannot see it.");
        }
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new ConnectorException("GitHub refused the credential for " + repository
                    + " (HTTP " + response.statusCode() + ").");
        }
        if (response.statusCode() / 100 != 2) {
            throw new ConnectorException("GitHub answered HTTP " + response.statusCode()
                    + " for " + repository + ".");
        }
    }

    private java.util.Optional<TrackedIssue> issue(String repository, String number,
                                                   String identifier, ConnectorCredential credential) {
        HttpResponse<String> response = get(
                address(apiBase + "/repos/" + repository + "/issues/" + number), credential,
                "Could not read " + identifier + " from " + repository);

        if (response.statusCode() == 404) {
            return java.util.Optional.empty();
        }
        if (response.statusCode() / 100 != 2) {
            throw new ConnectorException("GitHub answered HTTP " + response.statusCode()
                    + " reading " + identifier + " from " + repository + ".");
        }

        JsonNode node;
        try {
            node = json.readTree(response.body());
        } catch (Exception e) {
            throw new ConnectorException("GitHub returned something that is not JSON for "
                    + identifier + " in " + repository + ".");
        }

        // GitHub's issues endpoint answers for pull requests too, and reports
        // them with a pull_request member. Tower does not hide that: if a team
        // wrote a PR number as the work item their release delivers, that is what
        // they said, and inventing a "not found" would be a lie about the tracker.
        String state = node.path("state").asText("");
        return java.util.Optional.of(new TrackedIssue(
                identifier,
                node.path("title").asText(""),
                state,
                "closed".equalsIgnoreCase(state),
                node.path("html_url").asText("")));
    }

    /**
     * The address to read, or a stated failure.
     *
     * <p>Built with the constructor rather than {@code URI.create} for two
     * reasons. A locator with a space or a stray character in it produces a
     * {@code ConnectorException} naming the address, instead of an
     * {@code IllegalArgumentException} escaping past every caller that was told
     * to expect only the former. And the architecture test that keeps Connectors
     * read-only rejects calls to any method named {@code create}, which is a
     * blunt rule doing its job: a Connector should not be reaching for one.
     */
    private URI address(String text) {
        try {
            return new URI(text);
        } catch (URISyntaxException e) {
            throw new ConnectorException("'" + text + "' is not a valid GitHub address.");
        }
    }

    private HttpResponse<String> get(URI uri, ConnectorCredential credential, String failureMessage) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "tower");

        // A token is optional: public repositories read without one, and a team
        // reading their own public issues should not have to mint a credential
        // to do it.
        if (credential != null && credential.isPresent()) {
            // Copied into the header and the copy dropped immediately. The token
            // never becomes a field, never enters a message, and never reaches a
            // log (NFR-028).
            char[] token = credential.token();
            request.header("Authorization", "Bearer " + new String(token));
            java.util.Arrays.fill(token, '\0');
        }

        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectorException(failureMessage + ": the read was interrupted.");
        } catch (Exception e) {
            // The cause's message, never the request: the Authorization header is
            // in that object, and NFR-028 keeps credentials out of anything a user
            // or a log can see.
            throw new ConnectorException(failureMessage + ": " + describe(e));
        }
    }

    /**
     * The {@code owner/repo} the locator names.
     *
     * <p>Validated here rather than anywhere above, because this is the only
     * layer entitled to know that a GitHub locator has that shape at all
     * (ADR-018).
     */
    private String repositoryOf(IssueLocator locator) {
        if (locator == null) {
            throw new ConnectorException("A GitHub locator is required, in the form owner/repository.");
        }
        String value = locator.value();
        String[] parts = value.split("/");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new ConnectorException("'" + value + "' is not a GitHub repository."
                    + " Give it as owner/repository, for example acme/retail.");
        }
        return value;
    }

    /**
     * The issue number an identifier names, or null when it names none.
     *
     * <p>GitHub issues are numbered, and teams write that number several ways:
     * "42", "#42", or "acme/retail#42" when quoting across repositories. All
     * three are accepted and reduced to the number, because the identifier is
     * whatever the team wrote and Tower stores it unchanged (ADR-018).
     *
     * <p>A cross-repository form is read for its number only. Tower has one
     * tracker bound, so "other/repo#42" is looked up in the bound repository —
     * and if it is not there, the answer is an honest "the tracker does not have
     * this" rather than a silent read from somewhere else.
     */
    static String numberOf(String identifier) {
        if (identifier == null) {
            return null;
        }
        String text = identifier.trim();
        int hash = text.lastIndexOf('#');
        if (hash >= 0) {
            text = text.substring(hash + 1);
        }
        if (text.isEmpty() || !text.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return text;
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
