package dev.tower.collector;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.tower.application.binding.ApplicationBinding;
import dev.tower.application.binding.EnvironmentBinding;
import dev.tower.application.sync.SyncRun;
import dev.tower.application.sync.UnrecognizedWorkload;
import dev.tower.collector.CollectorFixtures.FakeConnector;
import dev.tower.collector.CollectorFixtures.InMemoryBindings;
import dev.tower.collector.CollectorFixtures.InMemoryCredentials;
import dev.tower.collector.CollectorFixtures.InMemoryObservations;
import dev.tower.collector.CollectorFixtures.InMemoryVersions;
import dev.tower.connector.api.RunningWorkload;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.observation.Observation;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("The Deployment Collector")
class DeploymentCollectorTest {

    private static final String CONNECTOR = "kubernetes";
    private static final String CLUSTER = "https://api.cluster.example:6443";
    private static final String NAMESPACE = "customer-uat";
    private static final String IMAGE = "registry.example/acme/customer-api";
    private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
    private static final Instant DEPLOYED_AT = Instant.parse("2026-07-28T09:15:00Z");

    private FakeConnector connector;
    private InMemoryBindings bindings;
    private InMemoryObservations observations;
    private InMemoryVersions versions;
    private InMemoryCredentials credentials;
    private DeploymentCollector collector;

    private EnvironmentId uat;
    private ApplicationId customerApi;

    @BeforeEach
    void setUp() {
        connector = new FakeConnector(CONNECTOR);
        bindings = new InMemoryBindings();
        observations = new InMemoryObservations();
        versions = new InMemoryVersions();
        credentials = new InMemoryCredentials();
        collector = new DeploymentCollector(connector, bindings, observations, versions, credentials,
                Clock.fixed(NOW, ZoneOffset.UTC));

        uat = EnvironmentId.newId();
        customerApi = ApplicationId.newId();
    }

    private void bindEnvironment() {
        bindings.save(new EnvironmentBinding(uat, CONNECTOR, CLUSTER, NAMESPACE));
    }

    private void bindApplication(String versionPattern) {
        bindings.save(new ApplicationBinding(customerApi, CONNECTOR, IMAGE, versionPattern));
    }

    private static RunningWorkload workload(String tag) {
        return RunningWorkload.tagged("customer-api/app", IMAGE, tag, DEPLOYED_AT);
    }

    @Nested
    @DisplayName("records a change as an Observation")
    class Change {

        @Test
        void appends_an_observation_the_first_time_a_version_is_seen() {
            bindEnvironment();
            bindApplication(null);
            connector.running(NAMESPACE, workload("2026.08.1"));

            SyncRun run = collector.collect();

            assertThat(run.observationsAppended()).isEqualTo(1);
            assertThat(observations.appended).hasSize(1);
            assertThat(observations.appended.get(0).environmentId()).isEqualTo(uat);
            assertThat(observations.appended.get(0).applicationId()).isEqualTo(customerApi);
        }

        @Test
        void records_the_collector_as_the_source_so_provenance_survives() {
            bindEnvironment();
            bindApplication(null);
            connector.running(NAMESPACE, workload("2026.08.1"));

            collector.collect();

            var source = observations.appended.get(0).source();
            assertThat(source.collector()).isEqualTo(CONNECTOR);
            assertThat(source.isManual()).isFalse();
            assertThat(source.isImported()).isFalse();
        }

        @Test
        void preserves_the_platform_timestamp_rather_than_the_time_of_synchronization() {
            bindEnvironment();
            bindApplication(null);
            connector.running(NAMESPACE, workload("2026.08.1"));

            collector.collect();

            assertThat(observations.appended.get(0).observedAt()).isEqualTo(DEPLOYED_AT);
        }

        @Test
        void extracts_the_version_using_the_bound_pattern() {
            bindEnvironment();
            bindApplication("^release-(.+)$");
            connector.running(NAMESPACE, workload("release-2026.08.1"));

            collector.collect();

            var version = versions.findById(observations.appended.get(0).applicationVersionId()).orElseThrow();
            assertThat(version.version()).isEqualTo("2026.08.1");
        }

        @Test
        void appends_again_when_the_deployed_version_actually_changes() {
            bindEnvironment();
            bindApplication(null);
            connector.running(NAMESPACE, workload("2026.08.1"));
            collector.collect();

            connector.running(NAMESPACE, RunningWorkload.tagged(
                    "customer-api/app", IMAGE, "2026.08.2", DEPLOYED_AT.plusSeconds(3600)));
            SyncRun second = collector.collect();

            assertThat(second.observationsAppended()).isEqualTo(1);
            assertThat(observations.appended).hasSize(2);
        }
    }

    @Nested
    @DisplayName("records nothing when nothing changed — ADR-011")
    class NoChange {

        @Test
        void appends_no_observation_when_the_same_version_is_still_deployed() {
            bindEnvironment();
            bindApplication(null);
            connector.running(NAMESPACE, workload("2026.08.1"));
            collector.collect();

            SyncRun second = collector.collect();

            assertThat(second.observationsAppended()).isZero();
            assertThat(observations.appended).hasSize(1);
        }

        @Test
        void still_records_that_the_run_happened_so_liveness_is_not_lost() {
            bindEnvironment();
            bindApplication(null);
            connector.running(NAMESPACE, workload("2026.08.1"));
            collector.collect();

            SyncRun second = collector.collect();

            assertThat(second.outcome()).isEqualTo(SyncRun.Outcome.SUCCEEDED);
            assertThat(second.foundNoChange()).isTrue();
            assertThat(second.finishedAt()).isNotNull();
        }

        @Test
        void polling_many_times_does_not_grow_the_store() {
            bindEnvironment();
            bindApplication(null);
            connector.running(NAMESPACE, workload("2026.08.1"));

            for (int i = 0; i < 50; i++) {
                collector.collect();
            }

            // The defect ADR-011 exists to prevent: 50 runs, one fact.
            assertThat(observations.appended).hasSize(1);
        }

        @Test
        void reuses_the_application_version_rather_than_recording_a_second_one() {
            bindEnvironment();
            bindApplication(null);
            connector.running(NAMESPACE, workload("2026.08.1"));

            collector.collect();
            collector.collect();

            assertThat(versions.findAllByApplication(customerApi)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("reports what it cannot attribute rather than guessing — ADR-012, FR-060")
    class Unrecognized {

        @Test
        void reports_an_image_no_application_is_bound_to() {
            bindEnvironment();
            connector.running(NAMESPACE, RunningWorkload.tagged(
                    "mystery/app", "registry.example/acme/mystery", "1.0", DEPLOYED_AT));

            SyncRun run = collector.collect();

            assertThat(run.observationsAppended()).isZero();
            assertThat(run.unrecognized()).singleElement()
                    .extracting(UnrecognizedWorkload::reason)
                    .asString().contains("No Application is bound");
        }

        @Test
        void reports_a_tag_the_version_pattern_does_not_match() {
            bindEnvironment();
            bindApplication("^release-(.+)$");
            connector.running(NAMESPACE, workload("latest"));

            SyncRun run = collector.collect();

            assertThat(run.observationsAppended()).isZero();
            assertThat(run.unrecognized()).singleElement()
                    .extracting(UnrecognizedWorkload::reason)
                    .asString().contains("does not match the version pattern");
        }

        @Test
        void reports_a_digest_pinned_image_because_it_carries_no_version() {
            bindEnvironment();
            bindApplication(null);
            connector.running(NAMESPACE, RunningWorkload.digestPinned(
                    "customer-api/app", IMAGE, "sha256:abc123", DEPLOYED_AT));

            SyncRun run = collector.collect();

            assertThat(run.observationsAppended()).isZero();
            assertThat(run.unrecognized()).singleElement()
                    .extracting(UnrecognizedWorkload::reason)
                    .asString().contains("pinned to a digest");
        }

        @Test
        void names_the_image_so_the_user_can_write_a_binding_for_it() {
            bindEnvironment();
            connector.running(NAMESPACE, RunningWorkload.tagged(
                    "mystery/app", "registry.example/acme/mystery", "1.0", DEPLOYED_AT));

            var unrecognized = collector.collect().unrecognized().get(0);

            assertThat(unrecognized.imageReference()).isEqualTo("registry.example/acme/mystery:1.0");
            assertThat(unrecognized.scope()).isEqualTo(NAMESPACE);
            assertThat(unrecognized.name()).isEqualTo("mystery/app");
        }

        @Test
        void an_unrecognized_workload_does_not_stop_the_ones_it_can_attribute() {
            bindEnvironment();
            bindApplication(null);
            connector.running(NAMESPACE,
                    RunningWorkload.tagged("mystery/app", "acme/mystery", "1.0", DEPLOYED_AT),
                    workload("2026.08.1"));

            SyncRun run = collector.collect();

            assertThat(run.observationsAppended()).isEqualTo(1);
            assertThat(run.unrecognized()).hasSize(1);
            assertThat(run.workloadsRead()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("survives a failure without corrupting what it already knows")
    class Failure {

        @Test
        void records_a_failed_scope_and_appends_nothing_for_it() {
            bindEnvironment();
            bindApplication(null);
            connector.failsFor(NAMESPACE, "namespace not found");

            SyncRun run = collector.collect();

            assertThat(run.outcome()).isEqualTo(SyncRun.Outcome.FAILED);
            assertThat(run.failures()).singleElement().asString().contains("namespace not found");
            assertThat(observations.appended).isEmpty();
        }

        @Test
        void leaves_earlier_observations_valid() {
            bindEnvironment();
            bindApplication(null);
            connector.running(NAMESPACE, workload("2026.08.1"));
            collector.collect();

            connector.failsFor(NAMESPACE, "token expired");
            collector.collect();

            assertThat(observations.appended).hasSize(1);
        }

        @Test
        void reports_partial_success_when_one_scope_of_two_fails() {
            bindEnvironment();
            var production = EnvironmentId.newId();
            bindings.save(new EnvironmentBinding(production, CONNECTOR, CLUSTER, "customer-prod"));
            bindApplication(null);
            connector.running(NAMESPACE, workload("2026.08.1"));
            connector.failsFor("customer-prod", "forbidden");

            SyncRun run = collector.collect();

            assertThat(run.outcome()).isEqualTo(SyncRun.Outcome.PARTIALLY_SUCCEEDED);
            assertThat(run.observationsAppended()).isEqualTo(1);
            assertThat(run.failures()).hasSize(1);
        }

        @Test
        void succeeds_with_nothing_to_do_when_no_environment_is_bound() {
            SyncRun run = collector.collect();

            assertThat(run.outcome()).isEqualTo(SyncRun.Outcome.SUCCEEDED);
            assertThat(run.workloadsRead()).isZero();
        }
    }

    @Nested
    @DisplayName("handles credentials without leaking them")
    class Credentials {

        @Test
        void presents_the_stored_credential_for_the_target() {
            bindEnvironment();
            bindApplication(null);
            credentials.store(CONNECTOR, CLUSTER, "sha256~a-token".toCharArray());

            collector.collect();

            // Presence at call time, not afterwards: the Collector clears the
            // credential once the call returns, which the next test asserts.
            assertThat(connector.credentialPresentAtCall).containsExactly(true);
        }

        @Test
        void presents_no_credential_when_none_is_configured() {
            bindEnvironment();
            bindApplication(null);

            collector.collect();

            assertThat(connector.credentialPresentAtCall).containsExactly(false);
        }

        @Test
        void clears_the_credential_once_the_connector_is_done_with_it() {
            bindEnvironment();
            bindApplication(null);
            credentials.store(CONNECTOR, CLUSTER, "sha256~a-token".toCharArray());

            collector.collect();

            assertThat(connector.credentialPresentAtCall).containsExactly(true);
            assertThat(connector.credentialsSeen.get(0).isPresent()).isFalse();
        }

        @Test
        void leaves_the_stored_credential_intact_for_the_next_run() {
            bindEnvironment();
            bindApplication(null);
            credentials.store(CONNECTOR, CLUSTER, "sha256~a-token".toCharArray());

            collector.collect();
            collector.collect();

            // Clearing the copy handed to a Connector must not empty the store.
            assertThat(connector.credentialPresentAtCall).containsExactly(true, true);
        }

        @Test
        void clears_the_credential_even_when_the_scope_fails() {
            bindEnvironment();
            credentials.store(CONNECTOR, CLUSTER, "sha256~a-token".toCharArray());
            connector.failsFor(NAMESPACE, "forbidden");

            collector.collect();

            assertThat(connector.credentialsSeen.get(0).isPresent()).isFalse();
        }
    }

    @Nested
    @DisplayName("refuses to date a fact in the future")
    class Clamping {

        @Test
        void clamps_a_platform_timestamp_ahead_of_now_rather_than_burying_it() {
            // A cluster clock skewed ahead would otherwise produce an Observation
            // dated later than now, which an append-only store can never correct.
            bindEnvironment();
            bindApplication(null);
            connector.running(NAMESPACE, RunningWorkload.tagged(
                    "customer-api/app", IMAGE, "2026.08.1", NOW.plusSeconds(3600)));

            collector.collect();

            assertThat(observations.appended.get(0).observedAt()).isEqualTo(NOW);
        }
    }

    @Test
    @DisplayName("keeps Environments apart, so a shared image is observed in each")
    void observes_the_same_application_separately_in_each_environment() {
        bindEnvironment();
        var production = EnvironmentId.newId();
        bindings.save(new EnvironmentBinding(production, CONNECTOR, CLUSTER, "customer-prod"));
        bindApplication(null);
        connector.running(NAMESPACE, workload("2026.08.1"));
        connector.running("customer-prod", workload("2026.07.9"));

        SyncRun run = collector.collect();

        assertThat(run.observationsAppended()).isEqualTo(2);
        assertThat(observations.findAllInEnvironment(uat)).hasSize(1);
        assertThat(observations.findAllInEnvironment(production)).hasSize(1);
        assertThat(observations.findAllInEnvironment(uat).get(0).applicationVersionId())
                .isNotEqualTo(observations.findAllInEnvironment(production).get(0).applicationVersionId());
    }

    @Test
    @DisplayName("reports the Connector it normalizes for")
    void names_its_connector() {
        assertThat(collector.connectorId()).isEqualTo(CONNECTOR);
    }

    @Test
    @DisplayName("a superseded version is a new fact, and the old one survives")
    void keeps_the_superseded_observation() {
        bindEnvironment();
        bindApplication(null);
        connector.running(NAMESPACE, workload("2026.08.1"));
        collector.collect();
        connector.running(NAMESPACE, RunningWorkload.tagged(
                "customer-api/app", IMAGE, "2026.08.2", DEPLOYED_AT.plusSeconds(3600)));
        collector.collect();

        assertThat(observations.appended).hasSize(2);
        assertThat(observations.appended.stream().map(Observation::observedAt))
                .containsExactly(DEPLOYED_AT, DEPLOYED_AT.plusSeconds(3600));
    }
}
