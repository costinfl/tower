package dev.tower.connector.jira;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.tower.connector.api.ConnectorCredential;
import dev.tower.connector.api.ConnectorException;
import dev.tower.connector.api.IssueLocator;
import dev.tower.connector.api.IssueTrackerConnector;
import dev.tower.connector.api.TrackedIssue;

/**
 * Reads work items from Jira (ADR-018).
 *
 * <p>Read-only, and structurally so: the only HTTP method this class can issue
 * is GET. Nothing here transitions an issue, comments on one, or assigns
 * anybody, and Guardrails.md lists "Creates or updates Jira issues
 * automatically" among the things Tower must never do — by name.
 *
 * <p>No Atlassian SDK, for the reason ADR-014 gives for reading git rather than
 * a vendor API and the reason the GitHub Connector carries no SDK either: Tower
 * needs a summary and a status, and the JDK's own HTTP client reads that in a few
 * lines. Jira's model is vast, and an SDK would put all of it — custom fields,
 * workflows, sprints, issue links — within easy reach of code that has no
 * business holding any of it.
 *
 * <p>Produces no Observations. That is the category rather than an omission:
 * this Connector resolves references a developer already stated, and ADR-018
 * records why an issue is not something Tower observed.
 */
@Component
public class JiraIssueTrackerConnector implements IssueTrackerConnector {

    public static final String CONNECTOR_ID = "jira";

    /**
     * The REST path, version 2.
     *
     * <p>Version 3 exists only on Jira Cloud, and differs from version 2 in how
     * it renders rich text: descriptions and comments come back as Atlassian
     * Document Format rather than a string. Tower reads neither. For a summary
     * and a status the two versions are identical, so version 2 is one path that
     * Cloud, Server and Data Center all serve — and Tower does not have to ask
     * which kind of Jira it is talking to.
     */
    private static final String API = "/rest/api/2";

    /**
     * A Jira issue key: a project key, a hyphen, a number.
     *
     * <p>Anchored, so a sentence that merely contains a key does not become one.
     * The leading character must be a letter because Jira requires that of a
     * project key, which is also what keeps a bare number — the shape GitHub
     * uses — from being mistaken for a Jira issue.
     */
    private static final Pattern ISSUE_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9]*-[0-9]+");

    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public JiraIssueTrackerConnector() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                // Redirects are followed, but only ever for a GET: this client
                // issues nothing else. Jira Cloud redirects a site URL more
                // often than most, so not following them would fail ordinary
                // configurations.
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
     * <p>One request per key rather than one JQL search. A search would be fewer
     * round trips, but it answers a different question: a key absent from the
     * results could be an issue that does not exist, one the credential cannot
     * see, or one a malformed query silently excluded, and Tower would have no
     * way to tell those apart. Asking for a specific issue gets a specific
     * answer.
     *
     * <p>Duplicates in the input are read once. A key the site does not have is
     * omitted rather than reported, per the SPI contract.
     */
    @Override
    public List<TrackedIssue> readIssues(IssueLocator locator, List<String> identifiers,
                                         ConnectorCredential credential) {
        String site = siteOf(locator);
        Set<String> unique = new LinkedHashSet<>(identifiers);

        List<TrackedIssue> found = new ArrayList<>();
        for (String identifier : unique) {
            String key = keyOf(identifier);
            if (key == null) {
                // Not something Jira could have a key for — "42" or "#42", say,
                // from a team that used to track work elsewhere. Omitted like any
                // other identifier the tracker does not have, rather than failing
                // the whole read for one reference.
                continue;
            }
            issue(site, key, identifier, credential).ifPresent(found::add);
        }
        return found;
    }

    @Override
    public void checkConnection(IssueLocator locator, ConnectorCredential credential) {
        String site = siteOf(locator);

        // Two different questions, and the endpoint follows which one is being
        // asked. With a credential, FR-061 wants to know it is accepted, and
        // /myself is the endpoint that actually proves that. Without one there is
        // nothing to accept, so the question is only whether this is a Jira that
        // answers — and /serverInfo answers that anonymously, where /myself would
        // report an authentication failure for a credential the user never
        // supplied.
        boolean authenticated = credential != null && credential.isPresent();
        String path = authenticated ? "/myself" : "/serverInfo";

        HttpResponse<String> response = get(address(site + API + path), credential,
                "Could not reach Jira at " + site);

        if (response.statusCode() == 401) {
            throw new ConnectorException("Jira refused the credential for " + site
                    + ". For Jira Cloud the credential is your account email, a colon, and an API"
                    + " token; for Server or Data Center it is a personal access token.");
        }
        if (response.statusCode() == 403) {
            throw new ConnectorException("Jira accepted the credential for " + site
                    + " but refused the request (HTTP 403).");
        }
        if (response.statusCode() == 404) {
            throw new ConnectorException("Jira answered 404 for " + site + API + path
                    + ". Either that address is not a Jira site, or its REST API is not enabled"
                    + " there. A site is given as its base address, for example"
                    + " https://acme.atlassian.net.");
        }
        if (response.statusCode() / 100 != 2) {
            throw new ConnectorException("Jira answered HTTP " + response.statusCode()
                    + " for " + site + ".");
        }
    }

    private Optional<TrackedIssue> issue(String site, String key, String identifier,
                                         ConnectorCredential credential) {
        // Two fields, asked for by name. A Jira issue document carries every
        // custom field the site defines and can run to hundreds of lines; Tower
        // reads two values, and asking for exactly those keeps the rest of
        // somebody's issue out of this process entirely.
        HttpResponse<String> response = get(
                address(site + API + "/issue/" + key + "?fields=summary,status"), credential,
                "Could not read " + identifier + " from " + site);

        if (response.statusCode() == 404) {
            // Jira answers 404 both for an issue that does not exist and for one
            // the credential cannot see — deliberately, so that a stranger cannot
            // learn which keys are real. Tower cannot tell those apart either, so
            // it omits the item and says no more than it knows.
            return Optional.empty();
        }
        if (response.statusCode() / 100 != 2) {
            throw new ConnectorException("Jira answered HTTP " + response.statusCode()
                    + " reading " + identifier + " from " + site + ".");
        }

        JsonNode node;
        try {
            node = json.readTree(response.body());
        } catch (Exception e) {
            throw new ConnectorException("Jira returned something that is not JSON for "
                    + identifier + " at " + site + ".");
        }

        JsonNode status = node.path("fields").path("status");
        return Optional.of(new TrackedIssue(
                identifier,
                node.path("fields").path("summary").asText(""),
                // The team's own word for where it stands — "In Review", "Ready
                // for QA", whatever their workflow calls it. Never normalised.
                status.path("name").asText(""),
                // Whether it is finished is Jira's own answer, not Tower's
                // reading of it. Every Jira status belongs to a status category,
                // and "done" is the one the site's administrator put it in.
                // Guessing from the status name would have Tower deciding which
                // of a team's states count as finished, which ADR-018 says it has
                // no standing to do.
                "done".equalsIgnoreCase(status.path("statusCategory").path("key").asText("")),
                site + "/browse/" + key));
    }

    private HttpResponse<String> get(URI uri, ConnectorCredential credential, String failureMessage) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "tower");

        // A credential is optional: a public Jira reads without one, and a team
        // looking at their own public project should not have to mint a token.
        if (credential != null && credential.isPresent()) {
            char[] token = credential.token();
            try {
                request.header("Authorization", authorization(token));
            } finally {
                // Cleared immediately. The token never becomes a field, never
                // enters a message, and never reaches a log (NFR-028).
                Arrays.fill(token, '\0');
            }
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
     * The Authorization header for a stored secret.
     *
     * <p>Jira has two credential shapes and no way to ask a site which it wants.
     * Cloud requires HTTP Basic with an account email and an API token; Server
     * and Data Center use a personal access token as a bearer. A colon
     * distinguishes them, because it is the separator Basic already needs and
     * neither an API token nor a personal access token contains one.
     *
     * <p>Chosen over a second configuration field the user would have to
     * understand: Atlassian's own documentation tells a Cloud user to write
     * {@code email:token}, so a credential of that shape says which kind it is
     * without anybody being asked.
     */
    static String authorization(char[] token) {
        String secret = new String(token);
        int separator = secret.indexOf(':');
        if (separator < 0) {
            return "Bearer " + secret;
        }
        return "Basic " + Base64.getEncoder()
                .encodeToString(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The site address the locator names.
     *
     * <p>Validated here rather than anywhere above, because this is the only
     * layer entitled to know that a Jira locator is a site address at all
     * (ADR-018). A trailing slash is dropped so the paths below join cleanly.
     */
    private String siteOf(IssueLocator locator) {
        if (locator == null) {
            throw new ConnectorException("A Jira locator is required: the site address,"
                    + " for example https://acme.atlassian.net.");
        }
        String value = locator.value().trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new ConnectorException("'" + locator.value() + "' is not a Jira site."
                    + " Give it as the site address, for example https://acme.atlassian.net.");
        }
        return value;
    }

    /**
     * The issue key an identifier names, or null when it names none.
     *
     * <p>Accepts the key on its own and the browse URL teams paste out of the
     * address bar, because both are things people actually write down. The key is
     * upper-cased for the request, since that is how Jira stores it — but what
     * Tower reports back is the identifier the team wrote, unchanged (ADR-018).
     */
    static String keyOf(String identifier) {
        if (identifier == null) {
            return null;
        }
        String text = identifier.trim();

        // A pasted link: everything after the last slash is the key, and Jira's
        // own browse URL puts it there.
        int slash = text.lastIndexOf('/');
        if (slash >= 0) {
            text = text.substring(slash + 1);
        }
        // A query string or fragment on a pasted link.
        int cut = text.indexOf('?');
        if (cut >= 0) {
            text = text.substring(0, cut);
        }
        cut = text.indexOf('#');
        if (cut >= 0) {
            text = text.substring(0, cut);
        }

        if (!ISSUE_KEY.matcher(text).matches()) {
            return null;
        }
        return text.toUpperCase(Locale.ROOT);
    }

    /**
     * The address to read, or a stated failure.
     *
     * <p>Built with the constructor rather than {@code URI.create}, as in the
     * GitHub Connector: a site address with a space in it produces a
     * {@code ConnectorException} naming it, rather than an
     * {@code IllegalArgumentException} escaping past callers told to expect only
     * the former.
     */
    private URI address(String text) {
        try {
            return new URI(text);
        } catch (URISyntaxException e) {
            throw new ConnectorException("'" + text + "' is not a valid Jira address.");
        }
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
