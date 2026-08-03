package dev.tower.connector.jenkins;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.tower.connector.api.CiCdConnector;
import dev.tower.connector.api.ConnectorCredential;
import dev.tower.connector.api.ConnectorException;
import dev.tower.connector.api.PipelineLocator;
import dev.tower.connector.api.PipelineRun;

/**
 * Reads pipeline runs from Jenkins (ADR-020).
 *
 * <p>Read-only, and this is the Connector category where that guarantee is least
 * automatic. A Jenkins token that can read a job can very often start it, and
 * Guardrails.md lists triggering pipelines among the things Tower must never do.
 * There is no operation here that starts, stops, retries or configures anything;
 * the only HTTP method this class can issue is GET, and its own test asserts that
 * from the far side of a socket.
 *
 * <p>The disposable module, deliberately. ADR-020 was written for a Jenkins with
 * a fair chance of being decommissioned inside a year, and the shape of that
 * decision is that everything above this — the SPI, the Collector, the bindings,
 * the persistence, the API — outlives it. What is here is four fields and the
 * paths they live at.
 */
@Component
public class JenkinsCiCdConnector implements CiCdConnector {

    public static final String CONNECTOR_ID = "jenkins";

    /**
     * The fields asked for, and no more.
     *
     * <p>Jenkins answers the whole build object otherwise, which on a busy
     * instance runs to changesets, culprits, artefacts and every plugin's own
     * additions. Tower reads a number, a result, a timestamp, a name and the
     * parameters, so it asks for those. The {@code tree} parameter has been in
     * Jenkins since 1.4xx and is the one piece of its API worth relying on.
     */
    private static final String RUN_FIELDS =
            "builds[number,result,timestamp,url,displayName,"
                    + "actions[parameters[name,value],environment]]";

    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public JenkinsCiCdConnector() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                // Followed, but only ever for a GET: this client issues nothing
                // else. Jenkins behind a reverse proxy redirects often enough
                // that not following them would fail ordinary installations.
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String connectorId() {
        return CONNECTOR_ID;
    }

    @Override
    public List<PipelineRun> readRuns(PipelineLocator locator, Instant since, int limit,
                                      ConnectorCredential credential) {
        if (limit <= 0) {
            throw new ConnectorException("A read must ask for at least one run.");
        }

        JsonNode body = readJson(
                address(jobUrl(locator) + "/api/json?tree="
                        + encoded(RUN_FIELDS + "{0," + limit + "}")),
                credential, "Could not read " + locator);

        List<PipelineRun> runs = new ArrayList<>();
        for (JsonNode build : body.path("builds")) {
            PipelineRun run = runOf(build, locator);
            // Filtered here rather than in the request: Jenkins cannot select
            // builds by time, so the bound is applied to what it returned. The
            // limit above is what keeps that from reading a decade of history.
            if (since == null || run.startedAt().isAfter(since)) {
                runs.add(run);
            }
        }
        return runs;
    }

    @Override
    public void checkConnection(PipelineLocator locator, ConnectorCredential credential) {
        HttpResponse<String> response = get(
                address(jobUrl(locator) + "/api/json?tree=name"), credential,
                "Could not reach Jenkins at " + locator.system());

        if (response.statusCode() == 401) {
            throw new ConnectorException("Jenkins refused the credential for " + locator.system()
                    + ". The credential is your user name, a colon, and an API token from your"
                    + " Jenkins user page — not your password.");
        }
        if (response.statusCode() == 403) {
            // Jenkins answers 403 both for a credential it does not accept and
            // for one that is accepted but may not read this job. Naming both
            // beats asserting the wrong one.
            throw new ConnectorException("Jenkins refused the request for " + locator
                    + ". Either the credential was not accepted, or it may not read this job.");
        }
        if (response.statusCode() == 404) {
            throw new ConnectorException("Jenkins has no job at " + locator
                    + ". A job inside a folder is written with each folder in the path,"
                    + " for example team/deploy-uat.");
        }
        if (response.statusCode() / 100 != 2) {
            throw new ConnectorException("Jenkins answered HTTP " + response.statusCode()
                    + " for " + locator + ".");
        }
    }

    /**
     * One build, as a run.
     *
     * <p>{@code result} is null while a build is still going. That is reported as
     * the outcome "RUNNING" rather than as an empty string, because a reader
     * seeing a run listed with no outcome would reasonably wonder whether Tower
     * failed to read it. It is not a success, so the Collector will not record it
     * (FR-078), and the next read will find it finished.
     */
    private PipelineRun runOf(JsonNode build, PipelineLocator locator) {
        String result = build.path("result").isNull() || build.path("result").asText("").isBlank()
                ? "RUNNING"
                : build.path("result").asText();

        return new PipelineRun(
                build.path("number").asText(""),
                build.path("displayName").asText(""),
                result,
                "SUCCESS".equals(result),
                Instant.ofEpochMilli(build.path("timestamp").asLong()),
                build.path("url").asText(jobUrl(locator) + "/" + build.path("number").asText("")),
                namedValues(build));
    }

    /**
     * Everything the build carried by name.
     *
     * <p>Parameters and environment variables are merged into one map, because to
     * a CI system they are one thing: a build parameter is surfaced to the run as
     * an environment variable, and a team asked where their version lives will
     * say "a parameter" or "an environment variable" meaning the same value. The
     * binding names one key and this decides where it came from.
     *
     * <p>Parameters win where both carry a name. They are what somebody chose for
     * that build; the environment also holds Jenkins' own variables, and letting
     * those overwrite a parameter would silently change what a binding reads.
     */
    private Map<String, String> namedValues(JsonNode build) {
        Map<String, String> values = new LinkedHashMap<>();
        for (JsonNode action : build.path("actions")) {
            JsonNode environment = action.path("environment");
            if (environment.isObject()) {
                environment.fields().forEachRemaining(
                        field -> values.put(field.getKey(), field.getValue().asText("")));
            }
        }
        for (JsonNode action : build.path("actions")) {
            for (JsonNode parameter : action.path("parameters")) {
                String name = parameter.path("name").asText("");
                if (!name.isBlank()) {
                    values.put(name, parameter.path("value").asText(""));
                }
            }
        }
        return values;
    }

    /**
     * Where a job lives, in Jenkins' own path shape.
     *
     * <p>{@code team/deploy-uat} becomes {@code /job/team/job/deploy-uat}, which
     * is how Jenkins addresses a job inside a folder. This is the one piece of
     * Jenkins' shape that exists anywhere in Tower, and it exists here rather
     * than in the binding so that a locator stays a locator: nothing above the
     * Connector may know that a job path has {@code /job/} between its segments.
     */
    private String jobUrl(PipelineLocator locator) {
        String system = locator.system();
        while (system.endsWith("/")) {
            system = system.substring(0, system.length() - 1);
        }
        String lower = system.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new ConnectorException("'" + locator.system() + "' is not a Jenkins address."
                    + " Give it as the server address, for example https://ci.acme.example.");
        }

        StringBuilder path = new StringBuilder(system);
        for (String segment : locator.job().split("/")) {
            if (!segment.isBlank()) {
                path.append("/job/").append(segment.trim());
            }
        }
        return path.toString();
    }

    private JsonNode readJson(URI uri, ConnectorCredential credential, String failureMessage) {
        HttpResponse<String> response = get(uri, credential, failureMessage);
        if (response.statusCode() == 404) {
            throw new ConnectorException(failureMessage + ": Jenkins has no such job.");
        }
        if (response.statusCode() / 100 != 2) {
            throw new ConnectorException(
                    failureMessage + ": Jenkins answered HTTP " + response.statusCode() + ".");
        }
        try {
            return json.readTree(response.body());
        } catch (Exception e) {
            // What a login page looks like from here: HTTP 200, and HTML.
            throw new ConnectorException(failureMessage + ": Jenkins returned something that is"
                    + " not JSON. An address that answers HTML where the API was asked for is"
                    + " usually a sign-in page.");
        }
    }

    private HttpResponse<String> get(URI uri, ConnectorCredential credential, String failureMessage) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", "tower");

        // Optional: a Jenkins that allows anonymous read needs none, and plenty
        // of internal ones do.
        if (credential != null && credential.isPresent()) {
            char[] token = credential.token();
            try {
                // Basic, with the user name and API token Jenkins issues. It has
                // no bearer scheme of its own, so unlike Jira there is nothing to
                // choose between.
                request.header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString(new String(token).getBytes(StandardCharsets.UTF_8)));
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
     * The address to read, or a stated failure.
     *
     * <p>Built with the constructor rather than {@code URI.create}, as in the
     * other Connectors: a job name with a space in it produces a
     * {@code ConnectorException} naming it rather than an
     * {@code IllegalArgumentException} escaping past callers told to expect only
     * the former.
     */
    /**
     * A query value Jenkins will understand and a URI will accept.
     *
     * <p>Jenkins' tree expression is written with brackets and braces —
     * {@code builds[number,result]{0,50}} — and none of those may appear raw in a
     * URI. Encoding them is not decoration: without it every read fails before it
     * is sent, which is how this was found.
     */
    private static String encoded(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private URI address(String text) {
        try {
            return new URI(text);
        } catch (URISyntaxException e) {
            throw new ConnectorException("'" + text + "' is not a valid Jenkins address."
                    + " A job name containing a space or a percent sign needs escaping.");
        }
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
