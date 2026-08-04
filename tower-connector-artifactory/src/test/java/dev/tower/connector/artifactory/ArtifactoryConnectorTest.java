package dev.tower.connector.artifactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.tower.connector.api.ArtifactLocator;
import dev.tower.connector.api.ConnectorCredential;
import dev.tower.connector.api.ConnectorException;
import dev.tower.connector.api.StoredArtifact;
import dev.tower.testkit.connector.RecordingHttpServer;

/**
 * ADR-021: confirming artifacts, and only confirming them.
 *
 * <p>Run against a real HTTP server on a loopback port rather than a mocked
 * client, for the reason ADR-019 gives and with one claim that can be
 * established no other way. "This Connector only ever issues GET" is not
 * something a Connector can be asked; it is something a server can be asked, and
 * so it is asserted here from the far side of the socket. ADR-021 turned down
 * Artifactory's AQL search because it needs POST, and this is where that
 * decision is enforced rather than merely written down.
 *
 * <p><strong>Nothing here has spoken to a real Artifactory.</strong> No JFrog
 * host is reachable from the environment Tower is built in, and Artifactory
 * publishes no API description for ADR-019's schema check to bite on, so these
 * fixtures are hand-written and are the weakest of the three kinds Tower uses.
 * CHECKLIST.md records the live check as outstanding rather than implied by a
 * green run here.
 */
@DisplayName("Confirming artifacts in Artifactory")
class ArtifactoryConnectorTest {

    private static final String CHART = "helm-local/api-2.5.0-abc1234.tgz";
    private static final String IMAGE = "docker-local/acme/api:2.5.0-abc1234";

    /** Artifactory's own shape for a file's storage record. */
    private static final String CHART_FILE = """
            {"repo":"helm-local","path":"/api-2.5.0-abc1234.tgz",
             "created":"2024-05-01T10:00:00.000+02:00",
             "lastModified":"2024-05-01T10:00:00.000+02:00",
             "downloadUri":"https://acme.example/artifactory/helm-local/api-2.5.0-abc1234.tgz",
             "size":"18342",
             "checksums":{"sha1":"aaa","md5":"bbb","sha256":"ccc"},
             "originalChecksums":{"sha256":"ccc"}}
            """;

    private RecordingHttpServer artifactory;
    private ArtifactoryConnector connector;

    @BeforeEach
    void startArtifactory() {
        artifactory = RecordingHttpServer.started();
        connector = new ArtifactoryConnector();
    }

    @AfterEach
    void stopArtifactory() {
        artifactory.close();
    }

    private ArtifactLocator locator() {
        return new ArtifactLocator(artifactory.baseUrl());
    }

    private List<StoredArtifact> read(String... coordinates) {
        return connector.readArtifacts(locator(), List.of(coordinates), ConnectorCredential.none());
    }

    @Nested
    @DisplayName("reads and never writes")
    class ReadOnly {

        @Test
        void issues_nothing_but_get() {
            // ADR-001 made read-only structural, and this is the observation that
            // makes the claim checkable rather than asserted: whatever the
            // Connector believes it is doing, the server saw only GETs.
            artifactory.answer("/api/storage/" + CHART, CHART_FILE);
            artifactory.answer("/api/repositories", "[]");
            read(CHART);
            connector.checkConnection(locator(), ConnectorCredential.none());

            assertThat(artifactory.requests()).isNotEmpty()
                    .allMatch(request -> "GET".equals(request.method()));
        }

        @Test
        void never_asks_for_the_aql_search() {
            // The search would be the natural thing to reach for and it is a
            // POST. Named here so that adding one later fails a test that says
            // why rather than only an architecture rule that says which word.
            artifactory.answer("/api/storage/" + CHART, CHART_FILE);
            read(CHART);

            assertThat(artifactory.requests())
                    .noneMatch(request -> request.path().contains("/search"));
        }
    }

    @Nested
    @DisplayName("confirms what the repository holds")
    class Confirming {

        @Test
        void reports_a_file_with_its_digest_size_and_arrival() {
            artifactory.answer("/api/storage/" + CHART, CHART_FILE);

            StoredArtifact artifact = read(CHART).get(0);
            assertThat(artifact.coordinate()).isEqualTo(CHART);
            assertThat(artifact.digest()).isEqualTo("sha256:ccc");
            assertThat(artifact.sizeBytes()).isEqualTo(18342);
            assertThat(artifact.storedAt()).isEqualTo(Instant.parse("2024-05-01T08:00:00Z"));
            assertThat(artifact.url()).endsWith("api-2.5.0-abc1234.tgz");
        }

        @Test
        void reads_an_image_tag_at_the_path_artifactory_keeps_its_manifest() {
            // The one piece of Artifactory's own shape anywhere in Tower: a tag
            // is a folder, and the manifest inside it is the bytes.
            artifactory.answer("/api/storage/docker-local/acme/api/2.5.0-abc1234/manifest.json",
                    """
                    {"repo":"docker-local","path":"/acme/api/2.5.0-abc1234/manifest.json",
                     "created":"2024-05-01T10:00:00.000Z","size":"1442",
                     "downloadUri":"https://acme.example/artifactory/docker-local/acme/api/2.5.0-abc1234/manifest.json",
                     "checksums":{"sha1":"ddd","sha256":"eee"}}
                    """);

            StoredArtifact artifact = read(IMAGE).get(0);
            assertThat(artifact.coordinate()).isEqualTo(IMAGE);
            assertThat(artifact.digest()).isEqualTo("sha256:eee");
        }

        @Test
        void takes_a_colon_after_the_last_slash_as_the_tag() {
            // A registry address with a port in it is the shape that breaks a
            // naive search for the first colon.
            assertThat(ArtifactoryConnector.storagePath("repo:8081/acme/api:1.0"))
                    .isEqualTo("repo:8081/acme/api/1.0/manifest.json");
            assertThat(ArtifactoryConnector.storagePath("helm-local/api-1.0.tgz"))
                    .isEqualTo("helm-local/api-1.0.tgz");
        }

        @Test
        void falls_back_to_sha1_for_an_artifact_stored_before_sha256_was_kept() {
            artifactory.answer("/api/storage/" + CHART,
                    """
                    {"created":"2019-01-01T00:00:00.000Z","size":"10","downloadUri":"http://x",
                     "checksums":{"sha1":"old","md5":"m"}}
                    """);

            assertThat(read(CHART).get(0).digest()).isEqualTo("sha1:old");
        }

        @Test
        void reports_no_digest_rather_than_inventing_one() {
            artifactory.answer("/api/storage/" + CHART,
                    "{\"created\":\"2019-01-01T00:00:00.000Z\",\"size\":\"10\"}");

            StoredArtifact artifact = read(CHART).get(0);
            assertThat(artifact.hasDigest()).isFalse();
        }

        @Test
        void reads_several_coordinates_in_the_order_they_were_asked() {
            artifactory.answer("/api/storage/" + CHART, CHART_FILE);
            artifactory.answer("/api/storage/docker-local/acme/api/2.5.0-abc1234/manifest.json",
                    "{\"created\":\"2024-05-01T10:00:00.000Z\",\"size\":\"1\","
                            + "\"checksums\":{\"sha256\":\"eee\"}}");

            assertThat(read(CHART, IMAGE)).extracting(StoredArtifact::coordinate)
                    .containsExactly(CHART, IMAGE);
        }
    }

    @Nested
    @DisplayName("keeps absence apart from silence")
    class Honesty {

        @Test
        void omits_a_coordinate_the_repository_does_not_have() {
            // 404 is the whole of "absent", and an artifact that is not there yet
            // is an ordinary situation rather than an error (FR-085).
            artifactory.answer("/api/storage/" + CHART, CHART_FILE);

            assertThat(read(CHART, IMAGE)).extracting(StoredArtifact::coordinate)
                    .containsExactly(CHART);
        }

        @Test
        void throws_rather_than_omitting_when_the_credential_is_refused() {
            // The distinction FR-085 turns on. A refused credential quietly
            // reported as absence would have somebody rebuilding an artifact
            // that was there all along.
            artifactory.fail("/api/storage/" + CHART, 403);

            assertThatThrownBy(() -> read(CHART)).isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("refused");
        }

        @Test
        void throws_for_a_server_error_rather_than_returning_nothing() {
            artifactory.fail("/api/storage/" + CHART, 503);

            assertThatThrownBy(() -> read(CHART)).isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("503");
        }

        @Test
        void reports_a_sign_in_page_returned_with_http_200() {
            artifactory.answer("/api/storage/" + CHART, 200, "<html><body>Sign in</body></html>");

            assertThatThrownBy(() -> read(CHART)).isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("sign-in page");
        }

        @Test
        void omits_a_coordinate_that_addresses_a_folder() {
            // Usually a template that lost its file extension. Reporting it
            // present would have a document claim bytes that do not exist.
            artifactory.answer("/api/storage/helm-local/api", """
                    {"repo":"helm-local","path":"/api",
                     "children":[{"uri":"/api-2.5.0-abc1234.tgz","folder":false}]}
                    """);

            assertThat(read("helm-local/api")).isEmpty();
        }

        @Test
        void asks_nothing_for_a_blank_coordinate() {
            assertThat(read("", "  ")).isEmpty();
            assertThat(artifactory.requests()).isEmpty();
        }
    }

    @Nested
    @DisplayName("presents a credential the way Artifactory expects")
    class Credentials {

        @Test
        void sends_an_access_token_as_a_bearer() {
            assertThat(ArtifactoryConnector.authorization("token-only".toCharArray()))
                    .isEqualTo("Bearer token-only");
        }

        @Test
        void sends_a_name_and_key_as_basic() {
            assertThat(ArtifactoryConnector.authorization("alice:key".toCharArray()))
                    .isEqualTo("Basic " + Base64.getEncoder()
                            .encodeToString("alice:key".getBytes(StandardCharsets.UTF_8)));
        }

        @Test
        void presents_no_header_at_all_when_there_is_no_credential() {
            // Sending an empty credential turns an anonymous read into a refused
            // one, and a repository that allows anonymous read is ordinary.
            artifactory.answer("/api/storage/" + CHART, CHART_FILE);
            read(CHART);

            assertThat(artifactory.requests().get(0).authorization()).isNull();
        }

        @Test
        void never_puts_the_credential_in_a_failure_message() {
            // NFR-028, and the promise most likely to be quietly dropped.
            artifactory.fail("/api/storage/" + CHART, 403);
            char[] secret = "s3cr3t-token".toCharArray();

            assertThatThrownBy(() -> connector.readArtifacts(locator(), List.of(CHART),
                    ConnectorCredential.bearerToken(secret)))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageNotContaining("s3cr3t-token");
        }
    }

    @Nested
    @DisplayName("tests a connection without changing anything")
    class Connection {

        @Test
        void accepts_a_repository_that_answers() {
            artifactory.answer("/api/repositories", "[]");

            connector.checkConnection(locator(), ConnectorCredential.none());

            assertThat(artifactory.requests()).allMatch(r -> "GET".equals(r.method()));
        }

        @Test
        void names_both_meanings_of_a_403() {
            artifactory.fail("/api/repositories", 403);

            assertThatThrownBy(() -> connector.checkConnection(locator(), ConnectorCredential.none()))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("not accepted")
                    .hasMessageContaining("may not list");
        }

        @Test
        void explains_a_404_as_the_wrong_base_address() {
            // The mistake a first-time user makes: the service's base commonly
            // ends in /artifactory, and giving the host alone answers 404.
            artifactory.fail("/api/repositories", 404);

            assertThatThrownBy(() -> connector.checkConnection(locator(), ConnectorCredential.none()))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("/artifactory");
        }

        @Test
        void refuses_a_locator_that_is_not_an_address() {
            assertThatThrownBy(() -> connector.checkConnection(
                    new ArtifactLocator("acme.jfrog.io"), ConnectorCredential.none()))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("not an Artifactory address");
        }
    }
}
