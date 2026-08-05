package dev.tower.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.tower.application.binding.BuildJobBinding;
import dev.tower.application.binding.PipelineJobBinding.VersionSource;
import dev.tower.application.discovery.VersionDiscovery;
import dev.tower.connector.api.CiCdConnector;
import dev.tower.connector.api.ConnectorCredential;
import dev.tower.connector.api.ConnectorException;
import dev.tower.connector.api.PipelineLocator;
import dev.tower.connector.api.PipelineRun;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersion;

/**
 * Turning build runs into candidate Application Versions (ADR-020).
 *
 * <p>The behaviour worth testing is what separates this from its sibling: it
 * proposes rather than records, so nothing here should ever write, and a run
 * that did not succeed proposes nothing rather than being reported.
 */
@DisplayName("Turning build runs into candidate versions")
class BuildRunCollectorTest {

    private static final String CONNECTOR_ID = "test-ci";
    private static final String SYSTEM = "https://ci.example";
    private static final String JOB = "build-customer-api";
    private static final Instant MONDAY = Instant.parse("2024-08-05T09:00:00Z");

    private final ApplicationId customerApi = ApplicationId.newId();

    private FakeCiConnector connector;
    private CollectorFixtures.InMemoryBindings bindings;
    private CollectorFixtures.InMemoryVersions versions;
    private CollectorFixtures.InMemoryCredentials credentials;
    private BuildRunCollector collector;

    @BeforeEach
    void setUp() {
        connector = new FakeCiConnector();
        bindings = new CollectorFixtures.InMemoryBindings();
        versions = new CollectorFixtures.InMemoryVersions();
        credentials = new CollectorFixtures.InMemoryCredentials();
        collector = new BuildRunCollector(connector, bindings, versions, credentials);
    }

    private void bind(VersionSource source, String key) {
        bindings.save(new BuildJobBinding(customerApi, CONNECTOR_ID, SYSTEM, JOB, source, key, null));
    }

    private static PipelineRun run(String id, String outcome, Instant at, Map<String, String> values) {
        return new PipelineRun(id, "#" + id, outcome, "SUCCESS".equals(outcome), at,
                SYSTEM + "/job/" + JOB + "/" + id, values);
    }

    private VersionDiscovery discover() {
        return collector.discover(customerApi);
    }

    @Nested
    @DisplayName("proposes what a build produced")
    class Proposing {

        @Test
        void offers_the_version_a_successful_run_carried() {
            bind(VersionSource.PARAMETER, "VERSION");
            connector.answer(run("43", "SUCCESS", MONDAY, Map.of("VERSION", "2.5.0")));

            VersionDiscovery discovery = discover();

            assertThat(discovery.succeeded()).isTrue();
            assertThat(discovery.candidates()).hasSize(1);
            assertThat(discovery.candidates().get(0).version()).isEqualTo("2.5.0");
        }

        @Test
        void says_which_system_proposed_it_and_which_run() {
            // ADR-020 put it as "the screen gains a source rather than a mode":
            // a build candidate sits in the same list as a ref, so it has to say
            // which system said so.
            bind(VersionSource.PARAMETER, "VERSION");
            connector.answer(run("43", "SUCCESS", MONDAY, Map.of("VERSION", "2.5.0")));

            var candidate = discover().candidates().get(0);
            assertThat(candidate.source()).isEqualTo(CONNECTOR_ID);
            assertThat(candidate.origin()).isEqualTo(JOB + " #43");
            assertThat(candidate.buildIdentifier()).isEqualTo("43");
        }

        @Test
        void carries_no_branch_tag_or_commit() {
            // A build knows what it produced, not which ref somebody cut it
            // from. Deriving one would be wrong for the first team that builds
            // from a detached commit.
            bind(VersionSource.PARAMETER, "VERSION");
            connector.answer(run("43", "SUCCESS", MONDAY, Map.of("VERSION", "2.5.0")));

            var candidate = discover().candidates().get(0);
            assertThat(candidate.branch()).isNull();
            assertThat(candidate.tag()).isNull();
            assertThat(candidate.commit()).isNull();
        }

        @Test
        void marks_a_version_tower_already_holds() {
            // The normal case on the second look. Reported and marked rather
            // than hidden, so nobody wonders whether Tower lost it.
            bind(VersionSource.PARAMETER, "VERSION");
            versions.save(ApplicationVersion.create(customerApi, "2.5.0", null, null, null, null));
            connector.answer(run("43", "SUCCESS", MONDAY, Map.of("VERSION", "2.5.0")));

            assertThat(discover().candidates().get(0).alreadyRegistered()).isTrue();
            assertThat(discover().newVersions()).isEmpty();
        }

        @Test
        void offers_the_newest_run_first() {
            bind(VersionSource.PARAMETER, "VERSION");
            connector.answer(run("43", "SUCCESS", MONDAY, Map.of("VERSION", "2.5.0")));
            connector.answer(run("44", "SUCCESS", MONDAY.plusSeconds(3600), Map.of("VERSION", "2.6.0")));

            assertThat(discover().candidates()).extracting("version")
                    .containsExactly("2.6.0", "2.5.0");
        }

        @Test
        void proposes_a_version_once_however_many_runs_produced_it() {
            bind(VersionSource.PARAMETER, "VERSION");
            connector.answer(run("43", "SUCCESS", MONDAY, Map.of("VERSION", "2.5.0")));
            connector.answer(run("44", "SUCCESS", MONDAY.plusSeconds(60), Map.of("VERSION", "2.5.0")));

            assertThat(discover().candidates()).hasSize(1);
        }

        @Test
        void reads_the_version_from_the_run_name_when_bound_that_way() {
            bind(VersionSource.RUN_NAME, "");
            connector.answer(new PipelineRun("43", "2.5.0", "SUCCESS", true, MONDAY, "", Map.of()));

            assertThat(discover().candidates().get(0).version()).isEqualTo("2.5.0");
        }
    }

    @Nested
    @DisplayName("proposes nothing it cannot stand behind")
    class Restraint {

        @Test
        void ignores_a_run_that_did_not_succeed() {
            // A failed or still-running build has produced no version, and a
            // candidate for one would suggest registering something that does
            // not exist.
            bind(VersionSource.PARAMETER, "VERSION");
            connector.answer(run("43", "FAILURE", MONDAY, Map.of("VERSION", "2.5.0")));
            connector.answer(run("44", "RUNNING", MONDAY.plusSeconds(60), Map.of("VERSION", "2.5.1")));

            assertThat(discover().candidates()).isEmpty();
            // And not reported either: a candidate list is a list of things a
            // person can act on, and failed builds would bury them.
            assertThat(discover().unmatched()).isEmpty();
        }

        @Test
        void reports_a_run_whose_version_the_pattern_did_not_recognise() {
            // Reported rather than dropped, exactly as an unrecognised git ref
            // is: a user whose pattern is wrong needs to see what it failed on.
            bindings.save(new BuildJobBinding(customerApi, CONNECTOR_ID, SYSTEM, JOB,
                    VersionSource.PARAMETER, "VERSION", "^release-(.+)$"));
            connector.answer(run("43", "SUCCESS", MONDAY, Map.of("VERSION", "nightly")));

            assertThat(discover().candidates()).isEmpty();
            assertThat(discover().unmatched()).anyMatch(u -> u.contains("nightly"));
        }

        @Test
        void reports_a_run_that_carried_no_version_at_all() {
            bind(VersionSource.PARAMETER, "VERSION");
            connector.answer(run("43", "SUCCESS", MONDAY, Map.of("OTHER", "x")));

            assertThat(discover().unmatched()).anyMatch(u -> u.contains("carried no version"));
        }

        @Test
        void writes_no_application_version() {
            // The property the whole design turns on. BR-01 makes a version
            // immutable, so one created without anybody asking would be
            // immutable too.
            bind(VersionSource.PARAMETER, "VERSION");
            connector.answer(run("43", "SUCCESS", MONDAY, Map.of("VERSION", "2.5.0")));

            discover();

            assertThat(versions.findAll()).isEmpty();
        }

        @Test
        void reads_every_run_rather_than_only_what_is_new() {
            // Nothing is stored, so there is no "last looked" to read from — and
            // a candidate list is about what is worth registering now.
            bind(VersionSource.PARAMETER, "VERSION");
            connector.answer(run("43", "SUCCESS", MONDAY, Map.of("VERSION", "2.5.0")));

            discover();
            discover();

            assertThat(connector.sinceAsked).containsExactly(null, null);
        }
    }

    @Nested
    @DisplayName("keeps a failure apart from an empty answer")
    class Honesty {

        @Test
        void reports_a_job_that_could_not_be_read() {
            bind(VersionSource.PARAMETER, "VERSION");
            connector.failWith("Jenkins refused the credential.");

            VersionDiscovery discovery = discover();
            assertThat(discovery.succeeded()).isFalse();
            assertThat(discovery.failure()).contains("refused the credential");
        }

        @Test
        void says_so_when_no_build_job_is_bound() {
            VersionDiscovery discovery = discover();
            assertThat(discovery.succeeded()).isFalse();
            assertThat(discovery.failure()).contains("No build job is bound");
        }

        @Test
        void keeps_the_candidates_it_did_read_when_another_job_failed() {
            // One job failing is a partial answer. Saying "could not look" over
            // a list with entries in it would be false.
            bind(VersionSource.PARAMETER, "VERSION");
            bindings.save(new BuildJobBinding(customerApi, CONNECTOR_ID, SYSTEM, "build-other",
                    VersionSource.PARAMETER, "VERSION", null));
            connector.answer(run("43", "SUCCESS", MONDAY, Map.of("VERSION", "2.5.0")));
            connector.failOnJob("build-other", "That job is gone.");

            VersionDiscovery discovery = discover();
            assertThat(discovery.succeeded()).isTrue();
            assertThat(discovery.candidates()).hasSize(1);
        }
    }

    /** Answers whatever runs it was given, and fails on demand. */
    private static final class FakeCiConnector implements CiCdConnector {

        private final List<PipelineRun> runs = new ArrayList<>();
        private final List<Instant> sinceAsked = new ArrayList<>();
        private String failure;
        private String failingJob;
        private String jobFailure;

        void answer(PipelineRun run) {
            runs.add(run);
        }

        void failWith(String message) {
            this.failure = message;
        }

        void failOnJob(String job, String message) {
            this.failingJob = job;
            this.jobFailure = message;
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
            if (locator.job().equals(failingJob)) {
                throw new ConnectorException(jobFailure);
            }
            return runs.stream().limit(limit).toList();
        }

        @Override
        public void checkConnection(PipelineLocator locator, ConnectorCredential credential) {
            if (failure != null) {
                throw new ConnectorException(failure);
            }
        }
    }
}
