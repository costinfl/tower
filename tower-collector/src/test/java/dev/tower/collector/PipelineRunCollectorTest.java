package dev.tower.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.tower.application.binding.PipelineJobBinding;
import dev.tower.application.binding.PipelineJobBinding.VersionSource;
import dev.tower.application.sync.NotRecordedRun;
import dev.tower.application.sync.PipelineSyncReport;
import dev.tower.connector.api.CiCdConnector;
import dev.tower.connector.api.ConnectorCredential;
import dev.tower.connector.api.ConnectorException;
import dev.tower.connector.api.PipelineLocator;
import dev.tower.connector.api.PipelineRun;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.observation.Observation;

/**
 * Turning pipeline runs into Observations (ADR-020).
 *
 * <p>The behaviour worth testing here is the timeline: which runs become facts,
 * in what order, and what happens on a second pass. The reading itself is the
 * Connector's own test, and there is no Connector yet — deliberately, since
 * ADR-020 puts the vendor last.
 */
@DisplayName("Turning pipeline runs into Observations")
class PipelineRunCollectorTest {

    private static final String CONNECTOR_ID = "test-ci";
    private static final String SYSTEM = "https://ci.example";
    private static final String JOB = "deploy-uat";
    private static final Instant MONDAY = Instant.parse("2024-08-05T09:00:00Z");

    private final EnvironmentId uat = EnvironmentId.newId();
    private final ApplicationId customerApi = ApplicationId.newId();

    private FakeCiConnector connector;
    private CollectorFixtures.InMemoryBindings bindings;
    private CollectorFixtures.InMemoryObservations observations;
    private CollectorFixtures.InMemoryVersions versions;
    private CollectorFixtures.InMemoryCredentials credentials;
    private PipelineRunCollector collector;

    @BeforeEach
    void setUp() {
        connector = new FakeCiConnector();
        bindings = new CollectorFixtures.InMemoryBindings();
        observations = new CollectorFixtures.InMemoryObservations();
        versions = new CollectorFixtures.InMemoryVersions();
        credentials = new CollectorFixtures.InMemoryCredentials();
        collector = new PipelineRunCollector(connector, bindings, observations, versions,
                credentials, Clock.fixed(Instant.parse("2024-09-01T00:00:00Z"), ZoneOffset.UTC));
    }

    private void bindJob(VersionSource source, String key, String pattern) {
        bindings.save(new PipelineJobBinding(uat, customerApi, CONNECTOR_ID, SYSTEM, JOB,
                source, key, pattern));
    }

    private void bindJob() {
        bindJob(VersionSource.PARAMETER, "VERSION", null);
    }

    /** A successful run carrying VERSION, at an offset in hours from Monday. */
    private PipelineRun deployed(String runId, String version, int hoursAfterMonday) {
        return new PipelineRun(runId, "#" + runId, "SUCCESS", true,
                MONDAY.plusSeconds(hoursAfterMonday * 3600L),
                SYSTEM + "/job/" + JOB + "/" + runId, Map.of("VERSION", version));
    }

    @Nested
    @DisplayName("records what a run reported")
    class Recording {

        @Test
        void a_successful_run_becomes_an_observation_at_the_runs_own_instant() {
            // FR-077, and the reason this Connector category earns its place: a
            // polled Observation is stamped when Tower looked, a run knows when
            // it happened.
            bindJob();
            connector.answer(deployed("1", "2.5.0", 0));

            var report = collector.collect();

            assertThat(report.observationsAppended()).isEqualTo(1);
            assertThat(observations.appended).singleElement().satisfies(observation -> {
                assertThat(observation.environmentId()).isEqualTo(uat);
                assertThat(observation.observedAt()).isEqualTo(MONDAY);
                assertThat(observation.source().collector()).isEqualTo(CONNECTOR_ID);
            });
        }

        @Test
        void records_the_run_identifier_as_the_versions_build_identifier() {
            // The one field a pipeline can fill that source control cannot.
            bindJob();
            connector.answer(deployed("487", "2.5.0", 0));

            collector.collect();

            assertThat(versions.stored).singleElement()
                    .satisfies(version -> assertThat(version.buildIdentifier()).isEqualTo("487"));
        }

        @Test
        void applies_the_version_pattern_to_what_the_parameter_held() {
            bindJob(VersionSource.PARAMETER, "VERSION", "^release-(.+)$");
            connector.answer(deployed("1", "release-2.5.0", 0));

            collector.collect();

            assertThat(versions.stored).singleElement()
                    .satisfies(version -> assertThat(version.version()).isEqualTo("2.5.0"));
        }

        @Test
        void reads_the_version_from_the_runs_name_when_that_is_where_it_lives() {
            bindJob(VersionSource.RUN_NAME, "", "^deploy (.+)$");
            connector.answer(new PipelineRun("1", "deploy 2.5.0", "SUCCESS", true,
                    MONDAY, "url", Map.of()));

            collector.collect();

            assertThat(versions.stored).singleElement()
                    .satisfies(version -> assertThat(version.version()).isEqualTo("2.5.0"));
        }
    }

    @Nested
    @DisplayName("follows the timeline rather than the latest state")
    class Timeline {

        @Test
        void records_a_redeployment_of_an_earlier_version_as_a_change() {
            // The case that would be lost by comparing every run against the
            // single newest Observation: 2.5.0, then 2.6.0, then 2.5.0 again is
            // three changes, and the third is a rollback somebody needs to see.
            bindJob();
            connector.answer(deployed("1", "2.5.0", 0));
            connector.answer(deployed("2", "2.6.0", 1));
            connector.answer(deployed("3", "2.5.0", 2));

            var report = collector.collect();

            assertThat(report.observationsAppended()).isEqualTo(3);
            assertThat(observations.appended).extracting(Observation::observedAt)
                    .isSorted();
        }

        @Test
        void does_not_record_a_run_that_deployed_what_was_already_there() {
            // ADR-011 unchanged: asking the same question twice and receiving the
            // same answer is not two facts.
            bindJob();
            connector.answer(deployed("1", "2.5.0", 0));
            connector.answer(deployed("2", "2.5.0", 1));

            assertThat(collector.collect().observationsAppended()).isEqualTo(1);
        }

        @Test
        void reads_runs_oldest_first_however_the_system_returned_them() {
            // Appending out of order would compare a run against Observations
            // that did not exist when it happened.
            bindJob();
            connector.answer(deployed("3", "2.5.0", 2));
            connector.answer(deployed("2", "2.6.0", 1));
            connector.answer(deployed("1", "2.5.0", 0));

            collector.collect();

            assertThat(observations.appended).extracting(Observation::observedAt).isSorted();
        }

        @Test
        void a_second_pass_over_the_same_runs_records_nothing() {
            // FR-081. Each run finds the Observation it produced sitting
            // immediately before it, naming the same version.
            bindJob();
            connector.answer(deployed("1", "2.5.0", 0));
            connector.answer(deployed("2", "2.6.0", 1));
            collector.collect();

            var second = collector.collect();

            assertThat(second.observationsAppended()).isZero();
            assertThat(second.confirmsNothingWasDeployed()).isTrue();
        }

        @Test
        void asks_only_for_runs_since_the_newest_it_recorded() {
            bindJob();
            connector.answer(deployed("1", "2.5.0", 0));
            collector.collect();

            collector.collect();

            assertThat(connector.sinceAsked).containsExactly(null, MONDAY);
        }
    }

    @Nested
    @DisplayName("reports what it read and did not record")
    class NotRecorded {

        @Test
        void a_run_that_failed_is_reported_rather_than_recorded() {
            // FR-078: a failed run is not evidence that anything was deployed.
            bindJob();
            connector.answer(new PipelineRun("1", "#1", "FAILURE", false,
                    MONDAY, "url", Map.of("VERSION", "2.5.0")));

            var report = collector.collect();

            assertThat(report.observationsAppended()).isZero();
            assertThat(report.notRecorded()).singleElement().satisfies(run -> {
                assertThat(run.runId()).isEqualTo("1");
                assertThat(run.outcome()).isEqualTo("FAILURE");
                assertThat(run.reason()).contains("did not report success");
            });
        }

        @Test
        void an_unstable_run_is_not_assumed_to_have_worked() {
            // The outcome a CI system has that nothing else does, and the one a
            // boolean alone would have thrown away.
            bindJob();
            connector.answer(new PipelineRun("1", "#1", "UNSTABLE", false,
                    MONDAY, "url", Map.of("VERSION", "2.5.0")));

            assertThat(collector.collect().notRecorded()).singleElement()
                    .extracting(NotRecordedRun::outcome).isEqualTo("UNSTABLE");
        }

        @Test
        void a_run_without_the_bound_parameter_names_what_it_did_carry() {
            // FR-080. A user whose binding names the wrong parameter needs to see
            // the ones that were there.
            bindJob();
            connector.answer(new PipelineRun("1", "#1", "SUCCESS", true, MONDAY, "url",
                    Map.of("BUILD_ID", "77", "GIT_COMMIT", "abc123")));

            assertThat(collector.collect().notRecorded()).singleElement()
                    .satisfies(run -> assertThat(run.reason())
                            .contains("VERSION")
                            .contains("BUILD_ID")
                            .contains("GIT_COMMIT"));
        }

        @Test
        void a_value_the_pattern_does_not_recognise_names_both() {
            bindJob(VersionSource.PARAMETER, "VERSION", "^release-(.+)$");
            connector.answer(deployed("1", "2.5.0", 0));

            assertThat(collector.collect().notRecorded()).singleElement()
                    .satisfies(run -> assertThat(run.reason())
                            .contains("2.5.0")
                            .contains("^release-(.+)$"));
        }

        @Test
        void nothing_unrecordable_is_silently_dropped() {
            bindJob();
            connector.answer(new PipelineRun("1", "#1", "FAILURE", false, MONDAY, "url", Map.of()));
            connector.answer(new PipelineRun("2", "#2", "SUCCESS", true,
                    MONDAY.plusSeconds(60), "url", Map.of()));
            connector.answer(deployed("3", "2.5.0", 1));

            var report = collector.collect();

            assertThat(report.runsRead()).isEqualTo(3);
            assertThat(report.observationsAppended()).isEqualTo(1);
            assertThat(report.notRecorded()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("survives a job it cannot read")
    class Failure {

        @Test
        void records_the_failure_and_keeps_what_it_already_read() {
            bindJob();
            connector.failWith("Jenkins answered HTTP 503 for deploy-uat.");

            var report = collector.collect();

            assertThat(report.failures()).containsExactly("Jenkins answered HTTP 503 for deploy-uat.");
            assertThat(report.readEverythingItWasAskedTo()).isFalse();
            assertThat(report.confirmsNothingWasDeployed()).isFalse();
        }

        @Test
        void says_nothing_was_deployed_only_when_it_could_actually_look() {
            // A failed read and a quiet job are the same number of Observations
            // and completely different statements.
            bindJob();

            assertThat(collector.collect().confirmsNothingWasDeployed()).isTrue();
        }
    }

    @Nested
    @DisplayName("connection test")
    class Connection {

        @Test
        void does_not_claim_a_credential_was_accepted_when_none_was_presented() {
            assertThat(collector.checkConnection(SYSTEM, JOB).message())
                    .contains("No credential was presented");
        }

        @Test
        void reports_rather_than_throws_when_the_system_refuses() {
            connector.failWith("Jenkins refused the credential.");

            var test = collector.checkConnection(SYSTEM, JOB);

            assertThat(test.reachable()).isFalse();
            assertThat(test.message()).contains("refused the credential");
        }

        @Test
        void reports_credentials_it_cannot_decrypt_rather_than_failing_the_request() {
            credentials.failToRead("The stored credential cannot be decrypted with the current key.");

            var test = collector.checkConnection(SYSTEM, JOB);

            assertThat(test.reachable()).isFalse();
            assertThat(test.message()).contains("cannot be decrypted");
        }
    }

    /** A CI Connector that answers what it was told to and records what it was asked. */
    private static final class FakeCiConnector implements CiCdConnector {

        private final List<PipelineRun> runs = new ArrayList<>();
        private final List<Instant> sinceAsked = new ArrayList<>();
        private String failure;

        void answer(PipelineRun run) {
            runs.add(run);
        }

        void failWith(String message) {
            this.failure = message;
        }

        @Override
        public String connectorId() {
            return CONNECTOR_ID;
        }

        @Override
        public List<PipelineRun> readRuns(PipelineLocator locator, Instant since, int limit,
                                          ConnectorCredential credential) {
            sinceAsked.add(since);
            if (failure != null) {
                throw new ConnectorException(failure);
            }
            return runs.stream()
                    .filter(run -> since == null || run.startedAt().isAfter(since))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void checkConnection(PipelineLocator locator, ConnectorCredential credential) {
            if (failure != null) {
                throw new ConnectorException(failure);
            }
        }
    }
}
