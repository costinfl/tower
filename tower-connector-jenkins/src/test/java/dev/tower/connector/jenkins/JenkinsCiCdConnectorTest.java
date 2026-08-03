package dev.tower.connector.jenkins;

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

import dev.tower.connector.api.ConnectorCredential;
import dev.tower.connector.api.ConnectorException;
import dev.tower.connector.api.PipelineLocator;
import dev.tower.connector.api.PipelineRun;
import dev.tower.testkit.connector.RecordingHttpServer;

/**
 * ADR-020: reading Jenkins, and only reading it.
 *
 * <p>Run against a real HTTP server on a loopback port rather than a mocked
 * client, for the reason ADR-019 gives: what is mostly under test is what Tower
 * does with the answers Jenkins actually gives — a build still running with a
 * null result, a job inside a folder, a sign-in page returned with HTTP 200 —
 * and a mocked client would only return what this test already assumed.
 *
 * <p><strong>Nothing here has spoken to a real Jenkins.</strong> No Jenkins host
 * is reachable from the environment Tower is built in, and Jenkins publishes no
 * API description for ADR-019's schema check to bite on, so these fixtures are
 * hand-written and are the weakest of the three kinds Tower uses. CHECKLIST.md
 * records the live check as outstanding rather than implied by a green run here.
 */
@DisplayName("Reading pipeline runs from Jenkins")
class JenkinsCiCdConnectorTest {

    private static final String JOB = "deploy-uat";

    private RecordingHttpServer jenkins;
    private JenkinsCiCdConnector connector;

    @BeforeEach
    void startJenkins() {
        jenkins = RecordingHttpServer.started();
        connector = new JenkinsCiCdConnector();
    }

    @AfterEach
    void stopJenkins() {
        jenkins.close();
    }

    private PipelineLocator locator() {
        return new PipelineLocator(jenkins.baseUrl(), JOB);
    }

    /** Jenkins' own shape for a finished build carrying one parameter. */
    private static String build(int number, String result, long timestamp, String parameter) {
        return """
                {"number":%d,"result":%s,"timestamp":%d,
                 "url":"http://ci/job/deploy-uat/%d/","displayName":"#%d",
                 "actions":[{},{"parameters":[{"name":"VERSION","value":"%s"}]}]}
                """.formatted(number, result, timestamp, number, number, parameter);
    }

    private void givenBuilds(String... builds) {
        jenkins.answer("/job/" + JOB + "/api/json",
                "{\"builds\":[" + String.join(",", builds) + "]}");
    }

    private List<PipelineRun> read() {
        return connector.readRuns(locator(), null, 50, ConnectorCredential.none());
    }

    @Nested
    @DisplayName("reads and never writes")
    class ReadOnly {

        @Test
        void issues_nothing_but_get() {
            // ADR-001, observed from the far side of the socket. Guardrails.md
            // names triggering a pipeline among the things Tower must never do,
            // and a Jenkins token that can read a job can usually start it.
            givenBuilds(build(1, "\"SUCCESS\"", 1722848400000L, "2.5.0"));

            read();
            connector.checkConnection(locator(), ConnectorCredential.none());

            assertThat(jenkins.requests()).isNotEmpty()
                    .allSatisfy(request -> assertThat(request.method()).isEqualTo("GET"));
        }

        @Test
        void reads_the_number_result_instant_and_parameter() {
            givenBuilds(build(487, "\"SUCCESS\"", 1722848400000L, "2.5.0"));

            assertThat(read()).singleElement().satisfies(run -> {
                assertThat(run.runId()).isEqualTo("487");
                assertThat(run.outcome()).isEqualTo("SUCCESS");
                assertThat(run.succeeded()).isTrue();
                assertThat(run.startedAt()).isEqualTo(Instant.ofEpochMilli(1722848400000L));
                assertThat(run.value("VERSION")).contains("2.5.0");
            });
        }

        @Test
        void asks_only_for_the_fields_it_reads() {
            // A busy Jenkins answers the whole build object otherwise —
            // changesets, culprits, artefacts and every plugin's additions.
            givenBuilds(build(1, "\"SUCCESS\"", 1722848400000L, "2.5.0"));

            read();

            assertThat(jenkins.requests()).singleElement().satisfies(request -> {
                assertThat(request.query()).contains("tree=builds[");
                assertThat(request.query()).contains("parameters[name,value]");
                assertThat(request.query()).doesNotContain("changeSet");
            });
        }

        @Test
        void reports_a_failed_build_with_jenkins_own_word_for_it() {
            // UNSTABLE is a Jenkins outcome nothing else has, and the reason
            // PipelineRun keeps the system's word beside the boolean.
            givenBuilds(build(1, "\"UNSTABLE\"", 1722848400000L, "2.5.0"));

            assertThat(read()).singleElement().satisfies(run -> {
                assertThat(run.outcome()).isEqualTo("UNSTABLE");
                assertThat(run.succeeded()).isFalse();
            });
        }

        @Test
        void reports_a_build_still_going_as_running_rather_than_as_nothing() {
            // Jenkins leaves result null while a build is in flight. An empty
            // outcome would read as "Tower failed to read this".
            givenBuilds(build(1, "null", 1722848400000L, "2.5.0"));

            assertThat(read()).singleElement().satisfies(run -> {
                assertThat(run.outcome()).isEqualTo("RUNNING");
                assertThat(run.succeeded()).isFalse();
            });
        }
    }

    @Nested
    @DisplayName("finds the value wherever the build kept it")
    class NamedValues {

        @Test
        void reads_an_environment_variable_as_readily_as_a_parameter() {
            // The two are one thing to a CI system: a parameter is surfaced to
            // the run as a variable, and a team says either meaning the same.
            jenkins.answer("/job/" + JOB + "/api/json", """
                    {"builds":[{"number":1,"result":"SUCCESS","timestamp":1722848400000,
                     "url":"u","displayName":"#1",
                     "actions":[{"environment":{"DEPLOY_VERSION":"2.5.0","BUILD_ID":"1"}}]}]}
                    """);

            assertThat(read()).singleElement()
                    .satisfies(run -> assertThat(run.value("DEPLOY_VERSION")).contains("2.5.0"));
        }

        @Test
        void a_parameter_wins_over_an_environment_variable_of_the_same_name() {
            // The parameter is what somebody chose for that build. The
            // environment also holds Jenkins' own variables, and letting those
            // overwrite it would silently change what a binding reads.
            jenkins.answer("/job/" + JOB + "/api/json", """
                    {"builds":[{"number":1,"result":"SUCCESS","timestamp":1722848400000,
                     "url":"u","displayName":"#1",
                     "actions":[{"environment":{"VERSION":"from-environment"}},
                                {"parameters":[{"name":"VERSION","value":"from-parameter"}]}]}]}
                    """);

            assertThat(read()).singleElement()
                    .satisfies(run -> assertThat(run.value("VERSION")).contains("from-parameter"));
        }

        @Test
        void a_build_with_no_named_values_reports_none_rather_than_failing() {
            jenkins.answer("/job/" + JOB + "/api/json", """
                    {"builds":[{"number":1,"result":"SUCCESS","timestamp":1722848400000,
                     "url":"u","displayName":"#1","actions":[{},{}]}]}
                    """);

            assertThat(read()).singleElement()
                    .satisfies(run -> assertThat(run.values()).isEmpty());
        }
    }

    @Nested
    @DisplayName("addresses jobs the way Jenkins does")
    class Addressing {

        @Test
        void puts_a_folder_path_into_jenkins_own_shape() {
            // team/deploy-uat is /job/team/job/deploy-uat. The one piece of
            // Jenkins' shape anywhere in Tower, and it lives here so a locator
            // stays a locator everywhere else.
            jenkins.answer("/job/team/job/deploy-uat/api/json", "{\"builds\":[]}");

            connector.readRuns(new PipelineLocator(jenkins.baseUrl(), "team/deploy-uat"),
                    null, 50, ConnectorCredential.none());

            assertThat(jenkins.requests()).singleElement()
                    .satisfies(request ->
                            assertThat(request.path()).isEqualTo("/job/team/job/deploy-uat/api/json"));
        }

        @Test
        void refuses_a_system_that_is_not_an_address() {
            assertThatThrownBy(() -> connector.readRuns(
                    new PipelineLocator("ci.acme.example", JOB), null, 50, ConnectorCredential.none()))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("not a Jenkins address");
        }

        @Test
        void reads_a_system_written_with_a_trailing_slash() {
            jenkins.answer("/job/" + JOB + "/api/json", "{\"builds\":[]}");

            connector.readRuns(new PipelineLocator(jenkins.baseUrl() + "/", JOB),
                    null, 50, ConnectorCredential.none());

            assertThat(jenkins.requests()).singleElement()
                    .satisfies(request ->
                            assertThat(request.path()).isEqualTo("/job/" + JOB + "/api/json"));
        }
    }

    @Nested
    @DisplayName("bounds what it reads")
    class Bounds {

        @Test
        void asks_jenkins_for_no_more_than_the_limit() {
            givenBuilds(build(1, "\"SUCCESS\"", 1722848400000L, "2.5.0"));

            connector.readRuns(locator(), null, 10, ConnectorCredential.none());

            assertThat(jenkins.requests()).singleElement()
                    .satisfies(request -> assertThat(request.query()).contains("{0,10}"));
        }

        @Test
        void drops_runs_older_than_the_instant_it_was_given() {
            // Jenkins cannot select builds by time, so the bound is applied to
            // what it returned. The limit is what keeps that from reading a
            // decade of history in the first place.
            givenBuilds(build(1, "\"SUCCESS\"", 1722848400000L, "2.4.0"),
                    build(2, "\"SUCCESS\"", 1722852000000L, "2.5.0"));

            var runs = connector.readRuns(locator(), Instant.ofEpochMilli(1722848400000L), 50,
                    ConnectorCredential.none());

            assertThat(runs).extracting(PipelineRun::runId).containsExactly("2");
        }

        @Test
        void refuses_a_read_that_asks_for_nothing() {
            assertThatThrownBy(() -> connector.readRuns(locator(), null, 0, ConnectorCredential.none()))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("at least one run");
        }
    }

    @Nested
    @DisplayName("connection test")
    class Connection {

        @Test
        void passes_when_the_job_answers() {
            jenkins.answer("/job/" + JOB + "/api/json", "{\"name\":\"deploy-uat\"}");

            connector.checkConnection(locator(), ConnectorCredential.none());
        }

        @Test
        void says_the_credential_was_refused_and_what_shape_jenkins_wants() {
            jenkins.answer("/job/" + JOB + "/api/json", 401, "{}");

            assertThatThrownBy(() -> connector.checkConnection(locator(),
                    ConnectorCredential.bearerToken("someone:token".toCharArray())))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("API token")
                    .hasMessageContaining("not your password");
        }

        @Test
        void names_both_possibilities_for_a_403_rather_than_asserting_the_wrong_one() {
            jenkins.answer("/job/" + JOB + "/api/json", 403, "{}");

            assertThatThrownBy(() -> connector.checkConnection(locator(), ConnectorCredential.none()))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("not accepted")
                    .hasMessageContaining("may not read this job");
        }

        @Test
        void explains_a_missing_job_in_terms_of_folders() {
            // The commonest configuration mistake with Jenkins: a job inside a
            // folder written without its folder.
            assertThatThrownBy(() -> connector.checkConnection(locator(), ConnectorCredential.none()))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("team/deploy-uat");
        }
    }

    @Nested
    @DisplayName("credentials")
    class Credentials {

        @Test
        void presents_the_credential_as_basic_which_is_all_jenkins_has() {
            givenBuilds(build(1, "\"SUCCESS\"", 1722848400000L, "2.5.0"));

            connector.readRuns(locator(), null, 50,
                    ConnectorCredential.bearerToken("someone:api_token".toCharArray()));

            assertThat(jenkins.requests()).singleElement().satisfies(request -> {
                assertThat(request.authorization()).startsWith("Basic ");
                assertThat(new String(Base64.getDecoder().decode(
                        request.authorization().substring("Basic ".length())), StandardCharsets.UTF_8))
                        .isEqualTo("someone:api_token");
            });
        }

        @Test
        void presents_nothing_at_all_when_there_is_none() {
            // A Jenkins that allows anonymous read needs none, and plenty of
            // internal ones do. An empty header would turn that into a refusal.
            givenBuilds(build(1, "\"SUCCESS\"", 1722848400000L, "2.5.0"));

            read();

            assertThat(jenkins.requests()).singleElement()
                    .satisfies(request -> assertThat(request.authorization()).isNull());
        }

        @Test
        void a_failure_message_never_carries_the_credential() {
            // NFR-028. The credential is inside the request object the failure
            // came from, which is why the message is built from the cause.
            jenkins.answer("/job/" + JOB + "/api/json", 500, "{}");

            assertThatThrownBy(() -> connector.readRuns(locator(), null, 50,
                    ConnectorCredential.bearerToken("someone:tower-must-not-leak".toCharArray())))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageNotContaining("tower-must-not-leak");
        }
    }

    @Nested
    @DisplayName("says what went wrong")
    class Failures {

        @Test
        void reports_a_sign_in_page_returned_with_http_200() {
            // The failure a description-driven check would never have found,
            // because no description describes it.
            jenkins.answer("/job/" + JOB + "/api/json", 200, "<html><body>Sign in</body></html>");

            assertThatThrownBy(this::readOnce)
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("not JSON")
                    .hasMessageContaining("sign-in page");
        }

        @Test
        void reports_a_server_error_rather_than_returning_no_runs() {
            // An empty list would be indistinguishable from a job that has never
            // run, and would make Tower report that nothing was ever deployed.
            jenkins.answer("/job/" + JOB + "/api/json", 503, "{}");

            assertThatThrownBy(this::readOnce)
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("503");
        }

        @Test
        void a_job_that_has_never_run_reads_as_empty_rather_than_failing() {
            jenkins.answer("/job/" + JOB + "/api/json", "{\"builds\":[]}");

            assertThat(read()).isEmpty();
        }

        private void readOnce() {
            connector.readRuns(locator(), null, 50, ConnectorCredential.none());
        }
    }
}
