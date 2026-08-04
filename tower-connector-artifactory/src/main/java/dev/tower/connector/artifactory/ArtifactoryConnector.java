package dev.tower.connector.artifactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.tower.connector.api.ArtifactLocator;
import dev.tower.connector.api.ArtifactRepositoryConnector;
import dev.tower.connector.api.ConnectorCredential;
import dev.tower.connector.api.ConnectorException;
import dev.tower.connector.api.StoredArtifact;

/**
 * Confirms artifacts in JFrog Artifactory (ADR-021).
 *
 * <p>GET only, and that is what the design bought rather than restraint that
 * might slip. Artifactory's capable read is AQL, which is
 * {@code POST /api/search/aql}; ADR-001's structural form forbids a Connector
 * from calling anything named {@code post}, so a search would fail the
 * architecture rule while being, semantically, a read. It is not needed. Because
 * the coordinate is composed from a version Tower already holds, this asks a
 * direct question at a direct address.
 *
 * <p>One endpoint does all of it: {@code /api/storage/{path}} answers with a
 * file's checksums, size, download address and the instant the repository
 * received it, and answers 404 for a path it does not have. That 404 is the
 * whole of "absent", and it is why a coordinate the repository does not hold is
 * omitted rather than raised (FR-085).
 *
 * <p>Two coordinate shapes reach it, and only one line tells them apart. A file
 * is a path — {@code helm-local/api-2.5.0-abc1234.tgz}. A container image is a
 * path with a tag after a colon — {@code docker-local/acme/api:2.5.0-abc1234} —
 * and Artifactory stores that tag's manifest at
 * {@code docker-local/acme/api/2.5.0-abc1234/manifest.json}. Knowing that is the
 * only piece of Artifactory's own shape anywhere in Tower, and it lives here for
 * the reason Jenkins' {@code /job/} lives in that Connector: nothing above may
 * know it.
 *
 * <p>Reads nothing that would make Tower a second repository browser. No listing,
 * no promotion history, no properties, no repository configuration — only
 * whether named bytes are present and what they are.
 */
@Component
public class ArtifactoryConnector implements ArtifactRepositoryConnector {

    public static final String CONNECTOR_ID = "artifactory";

    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public ArtifactoryConnector() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                // Followed, but only ever for a GET: this client issues nothing
                // else. Artifactory behind a load balancer redirects often
                // enough that not following would fail ordinary installations.
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String connectorId() {
        return CONNECTOR_ID;
    }

    @Override
    public List<StoredArtifact> readArtifacts(ArtifactLocator locator, List<String> coordinates,
                                              ConnectorCredential credential) {
        String base = baseOf(locator);

        List<StoredArtifact> found = new ArrayList<>();
        for (String coordinate : coordinates) {
            if (coordinate == null || coordinate.isBlank()) {
                continue;
            }
            // One request per coordinate. Artifactory can answer several at once
            // only through AQL, which needs POST; a handful of round trips for a
            // release's artifacts is the price of keeping ADR-001 structural,
            // and it is a price worth paying at this volume.
            storageOf(base, coordinate.trim(), credential).ifPresent(found::add);
        }
        return found;
    }

    @Override
    public void checkConnection(ArtifactLocator locator, ConnectorCredential credential) {
        String base = baseOf(locator);
        HttpResponse<String> response = get(address(base + "/api/repositories"), credential,
                "Could not reach Artifactory at " + locator.system());

        if (response.statusCode() == 401) {
            throw new ConnectorException("Artifactory refused the credential for "
                    + locator.system() + ". An access token is given on its own; a user name and"
                    + " an API key are given as name:key.");
        }
        if (response.statusCode() == 403) {
            // Artifactory answers 403 both for a credential it does not accept
            // and for one accepted but not permitted to list. Naming both beats
            // asserting the wrong one.
            throw new ConnectorException("Artifactory refused the request for " + locator.system()
                    + ". Either the credential was not accepted, or it may not list repositories.");
        }
        if (response.statusCode() == 404) {
            throw new ConnectorException("Artifactory has no API at " + locator.system()
                    + ". The address is the service's own base, which on most installations ends"
                    + " in /artifactory, for example https://acme.jfrog.io/artifactory.");
        }
        if (response.statusCode() / 100 != 2) {
            throw new ConnectorException("Artifactory answered HTTP " + response.statusCode()
                    + " for " + locator.system() + ".");
        }
    }

    /**
     * One coordinate's storage record, or nothing when the repository does not
     * have it.
     *
     * <p>Empty on 404 and only on 404. Every other unsuccessful status throws,
     * because FR-085 turns on the difference: "the repository has nothing here"
     * and "Tower could not ask" send a reader in opposite directions, and a
     * refused credential quietly reported as absence would have somebody
     * rebuilding an artifact that was there all along.
     */
    private java.util.Optional<StoredArtifact> storageOf(
            String base, String coordinate, ConnectorCredential credential) {

        String failureMessage = "Could not confirm " + coordinate;
        HttpResponse<String> response = get(
                address(base + "/api/storage/" + storagePath(coordinate)), credential, failureMessage);

        if (response.statusCode() == 404) {
            return java.util.Optional.empty();
        }
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new ConnectorException(failureMessage
                    + ": Artifactory refused the request. Either the credential was not accepted,"
                    + " or it may not read this repository.");
        }
        if (response.statusCode() / 100 != 2) {
            throw new ConnectorException(
                    failureMessage + ": Artifactory answered HTTP " + response.statusCode() + ".");
        }

        JsonNode body;
        try {
            body = json.readTree(response.body());
        } catch (Exception e) {
            // What a sign-in page looks like from here: HTTP 200, and HTML.
            throw new ConnectorException(failureMessage + ": Artifactory returned something that"
                    + " is not JSON. An address that answers HTML where the API was asked for is"
                    + " usually a sign-in page.");
        }

        if (body.has("children")) {
            // A folder, not a file. The coordinate addresses a directory —
            // usually a template missing its file extension — and reporting it
            // present would have a document claim bytes that do not exist.
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(new StoredArtifact(coordinate, digestOf(body),
                receivedAt(body), sizeOf(body), body.path("downloadUri").asText("")));
    }

    /**
     * Where Artifactory keeps the bytes a coordinate names.
     *
     * <p>A file is already a path. A container image reference carries a tag
     * after a colon, and Artifactory keeps that tag's manifest in a folder named
     * for the tag — so {@code docker-local/acme/api:2.5.0-abc1234} is stored at
     * {@code docker-local/acme/api/2.5.0-abc1234/manifest.json}.
     *
     * <p>The colon is looked for after the last slash, so a registry address with
     * a port in it — {@code repo:8081/acme/api:1.0} would be the shape that
     * breaks a naive search — cannot be mistaken for a tag separator.
     */
    static String storagePath(String coordinate) {
        int lastSlash = coordinate.lastIndexOf('/');
        int colon = coordinate.indexOf(':', lastSlash + 1);
        if (colon < 0) {
            return coordinate;
        }
        String image = coordinate.substring(0, colon);
        String tag = coordinate.substring(colon + 1);
        if (tag.isBlank()) {
            throw new ConnectorException("'" + coordinate + "' ends in a colon with no tag after it.");
        }
        return image + "/" + tag + "/manifest.json";
    }

    /**
     * The repository's identity for these bytes.
     *
     * <p>Written {@code sha256:<hex>} rather than as a bare hex string. That is
     * not the normalisation {@link StoredArtifact} forbids — the value is
     * Artifactory's own, untouched — it is the algorithm the field name already
     * stated, spelled where it can be read. Tower only ever compares a digest for
     * equality, so what matters is that the same bytes always spell the same way,
     * and this is also the form a container registry prints.
     *
     * <p>Falls back to SHA-1, which is what an older repository holds for an
     * artifact uploaded before SHA-256 checksums were kept. A weak digest is
     * still a better answer than none, and it is labelled for what it is.
     */
    private String digestOf(JsonNode body) {
        JsonNode checksums = body.path("checksums");
        String sha256 = checksums.path("sha256").asText("");
        if (!sha256.isBlank()) {
            return "sha256:" + sha256;
        }
        String sha1 = checksums.path("sha1").asText("");
        return sha1.isBlank() ? "" : "sha1:" + sha1;
    }

    /**
     * When the repository received this, which is neither when it was built nor
     * when it was deployed.
     *
     * <p>Artifactory writes an ISO-8601 instant with an offset that is not always
     * {@code Z}, so this parses an offset date-time rather than an instant.
     * Absent rather than guessed when it cannot be read: a wrong timestamp beside
     * a right digest would be the more misleading of the two.
     */
    private Instant receivedAt(JsonNode body) {
        String created = body.path("created").asText("");
        if (created.isBlank()) {
            created = body.path("lastModified").asText("");
        }
        if (created.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(created).toInstant();
        } catch (RuntimeException e) {
            try {
                return Instant.parse(created);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    /** Artifactory writes the size as a string, which is why this is not asLong. */
    private long sizeOf(JsonNode body) {
        try {
            return Long.parseLong(body.path("size").asText("0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * The service's own base address.
     *
     * <p>Validated here rather than anywhere above, because this is the only
     * layer entitled to know that an artifact locator is a URL at all (ADR-021).
     * A trailing slash is dropped so the paths below join cleanly.
     */
    private String baseOf(ArtifactLocator locator) {
        String system = locator.system();
        while (system.endsWith("/")) {
            system = system.substring(0, system.length() - 1);
        }
        String lower = system.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new ConnectorException("'" + locator.system() + "' is not an Artifactory address."
                    + " Give it as the service's base address, for example"
                    + " https://acme.jfrog.io/artifactory.");
        }
        return system;
    }

    private HttpResponse<String> get(URI uri, ConnectorCredential credential, String failureMessage) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", "tower");

        // A credential is optional: a repository that allows anonymous read needs
        // none, and a team should not have to mint a token to confirm a chart
        // they published openly.
        if (credential != null && credential.isPresent()) {
            char[] token = credential.token();
            try {
                request.header("Authorization", authorization(token));
            } finally {
                // Cleared immediately. The token never becomes a field, never
                // enters a message and never reaches a log (NFR-028).
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
            // in that object, and NFR-028 keeps credentials out of anything a
            // user or a log can see.
            throw new ConnectorException(failureMessage + ": " + describe(e));
        }
    }

    /**
     * The Authorization header for a stored secret.
     *
     * <p>The rule the Jira Connector uses, for the same reason and with the same
     * separator. Artifactory accepts an access token as a bearer and a user name
     * with an API key as HTTP Basic, and there is no way to ask an instance which
     * it wants. A colon distinguishes them, because it is the separator Basic
     * already needs and an access token does not contain one.
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
     * The address to read, or a stated failure.
     *
     * <p>Built with the constructor rather than {@code URI.create}, as in the
     * other Connectors: a coordinate with a space in it produces a
     * {@code ConnectorException} naming it rather than an
     * {@code IllegalArgumentException} escaping past callers told to expect only
     * the former. It is also why {@code create} does not appear here at all —
     * the architecture rule that keeps this Connector read-only refuses that
     * name.
     */
    private URI address(String text) {
        try {
            return new URI(text);
        } catch (URISyntaxException e) {
            throw new ConnectorException("'" + text + "' is not a valid Artifactory address."
                    + " A coordinate containing a space or a percent sign needs escaping.");
        }
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
