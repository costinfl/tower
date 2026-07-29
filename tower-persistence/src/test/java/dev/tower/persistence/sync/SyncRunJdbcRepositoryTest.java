package dev.tower.persistence.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.tower.application.sync.SyncRun;
import dev.tower.application.sync.SyncRunId;
import dev.tower.application.sync.UnrecognizedWorkload;
import dev.tower.persistence.support.PersistenceTestSupport;

@DisplayName("The Sync Run repository")
class SyncRunJdbcRepositoryTest {

    private static final String CONNECTOR = "kubernetes";
    private static final Instant T0 = Instant.parse("2026-07-29T10:00:00Z");

    @TempDir
    Path tempDir;

    private SyncRunJdbcRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        JdbcClient jdbcClient = PersistenceTestSupport.migratedJdbcClient(tempDir);
        repository = new SyncRunJdbcRepository(jdbcClient);
    }

    private static SyncRun run(Instant startedAt, SyncRun.Outcome outcome, int appended) {
        return new SyncRun(SyncRunId.newId(), CONNECTOR, startedAt, startedAt.plusSeconds(2),
                outcome, 3, appended, List.of(), List.of());
    }

    @Nested
    @DisplayName("stores a run and everything it saw")
    class Storing {

        @Test
        void reloads_a_run_with_its_counts_intact() {
            var stored = repository.append(run(T0, SyncRun.Outcome.SUCCEEDED, 1));

            var reloaded = repository.findLatest(CONNECTOR).orElseThrow();

            assertThat(reloaded.id()).isEqualTo(stored.id());
            assertThat(reloaded.workloadsRead()).isEqualTo(3);
            assertThat(reloaded.observationsAppended()).isEqualTo(1);
            assertThat(reloaded.outcome()).isEqualTo(SyncRun.Outcome.SUCCEEDED);
        }

        @Test
        void preserves_the_timestamps_that_make_liveness_answerable() {
            repository.append(run(T0, SyncRun.Outcome.SUCCEEDED, 0));

            var reloaded = repository.findLatest(CONNECTOR).orElseThrow();

            assertThat(reloaded.startedAt()).isEqualTo(T0);
            assertThat(reloaded.finishedAt()).isEqualTo(T0.plusSeconds(2));
        }

        @Test
        void reloads_unrecognized_workloads_in_the_order_they_were_seen() {
            repository.append(new SyncRun(SyncRunId.newId(), CONNECTOR, T0, T0.plusSeconds(1),
                    SyncRun.Outcome.SUCCEEDED, 2, 0,
                    List.of(UnrecognizedWorkload.noBinding("ns", "first/app", "acme/first:1.0"),
                            UnrecognizedWorkload.digestPinned("ns", "second/app", "acme/second@sha256:abc")),
                    List.of()));

            var reloaded = repository.findLatest(CONNECTOR).orElseThrow();

            assertThat(reloaded.unrecognized()).extracting(UnrecognizedWorkload::name)
                    .containsExactly("first/app", "second/app");
            assertThat(reloaded.unrecognized().get(0).reason()).contains("No Application is bound");
            assertThat(reloaded.unrecognized().get(1).reason()).contains("pinned to a digest");
        }

        @Test
        void reloads_failures_in_order() {
            repository.append(new SyncRun(SyncRunId.newId(), CONNECTOR, T0, T0.plusSeconds(1),
                    SyncRun.Outcome.PARTIALLY_SUCCEEDED, 1, 0, List.of(),
                    List.of("cluster/ns-a: forbidden", "cluster/ns-b: not found")));

            var reloaded = repository.findLatest(CONNECTOR).orElseThrow();

            assertThat(reloaded.failures())
                    .containsExactly("cluster/ns-a: forbidden", "cluster/ns-b: not found");
        }

        @Test
        void keeps_the_children_of_two_runs_apart() {
            // The defect a join-based mapping invites: one run's unrecognized
            // workloads appearing under another.
            repository.append(new SyncRun(SyncRunId.newId(), CONNECTOR, T0, T0.plusSeconds(1),
                    SyncRun.Outcome.SUCCEEDED, 1, 0,
                    List.of(UnrecognizedWorkload.noBinding("ns", "first/app", "acme/first:1.0")),
                    List.of("failure-a")));
            repository.append(new SyncRun(SyncRunId.newId(), CONNECTOR, T0.plusSeconds(60),
                    T0.plusSeconds(61), SyncRun.Outcome.SUCCEEDED, 1, 0,
                    List.of(UnrecognizedWorkload.noBinding("ns", "second/app", "acme/second:1.0")),
                    List.of("failure-b")));

            List<SyncRun> recent = repository.findRecent(10);

            assertThat(recent).hasSize(2);
            assertThat(recent.get(0).unrecognized()).singleElement()
                    .extracting(UnrecognizedWorkload::name).isEqualTo("second/app");
            assertThat(recent.get(0).failures()).containsExactly("failure-b");
            assertThat(recent.get(1).unrecognized()).singleElement()
                    .extracting(UnrecognizedWorkload::name).isEqualTo("first/app");
            assertThat(recent.get(1).failures()).containsExactly("failure-a");
        }
    }

    @Nested
    @DisplayName("reads history newest first")
    class History {

        @Test
        void orders_by_when_the_run_started() {
            repository.append(run(T0, SyncRun.Outcome.SUCCEEDED, 0));
            repository.append(run(T0.plusSeconds(120), SyncRun.Outcome.SUCCEEDED, 0));
            repository.append(run(T0.plusSeconds(60), SyncRun.Outcome.SUCCEEDED, 0));

            List<SyncRun> recent = repository.findRecent(10);

            assertThat(recent).extracting(SyncRun::startedAt)
                    .containsExactly(T0.plusSeconds(120), T0.plusSeconds(60), T0);
        }

        @Test
        void honours_the_limit_because_history_grows_without_bound() {
            for (int i = 0; i < 5; i++) {
                repository.append(run(T0.plusSeconds(i * 60L), SyncRun.Outcome.SUCCEEDED, 0));
            }

            assertThat(repository.findRecent(2)).hasSize(2);
        }

        @Test
        void returns_nothing_for_a_non_positive_limit_rather_than_everything() {
            repository.append(run(T0, SyncRun.Outcome.SUCCEEDED, 0));

            assertThat(repository.findRecent(0)).isEmpty();
            assertThat(repository.findRecent(-1)).isEmpty();
        }

        @Test
        void is_empty_before_anything_has_run() {
            assertThat(repository.findRecent(10)).isEmpty();
            assertThat(repository.findLatest(CONNECTOR)).isEmpty();
        }
    }

    @Nested
    @DisplayName("answers the liveness question only when a run earned it")
    class Liveness {

        @Test
        void finds_the_newest_wholly_successful_run() {
            repository.append(run(T0, SyncRun.Outcome.SUCCEEDED, 0));
            repository.append(run(T0.plusSeconds(60), SyncRun.Outcome.SUCCEEDED, 0));

            assertThat(repository.findLatestSuccessful(CONNECTOR))
                    .hasValueSatisfying(r -> assertThat(r.startedAt()).isEqualTo(T0.plusSeconds(60)));
        }

        @Test
        void ignores_a_newer_partial_run_because_it_says_nothing_about_the_scopes_it_missed() {
            repository.append(run(T0, SyncRun.Outcome.SUCCEEDED, 0));
            repository.append(run(T0.plusSeconds(60), SyncRun.Outcome.PARTIALLY_SUCCEEDED, 0));

            assertThat(repository.findLatestSuccessful(CONNECTOR))
                    .hasValueSatisfying(r -> assertThat(r.startedAt()).isEqualTo(T0));
        }

        @Test
        void ignores_a_failed_run() {
            repository.append(run(T0, SyncRun.Outcome.SUCCEEDED, 0));
            repository.append(run(T0.plusSeconds(60), SyncRun.Outcome.FAILED, 0));

            assertThat(repository.findLatestSuccessful(CONNECTOR))
                    .hasValueSatisfying(r -> assertThat(r.startedAt()).isEqualTo(T0));
        }

        @Test
        void is_empty_when_no_run_has_ever_wholly_succeeded() {
            repository.append(run(T0, SyncRun.Outcome.FAILED, 0));

            assertThat(repository.findLatestSuccessful(CONNECTOR)).isEmpty();
        }

        @Test
        void keeps_connectors_apart() {
            repository.append(run(T0, SyncRun.Outcome.SUCCEEDED, 0));
            repository.append(new SyncRun(SyncRunId.newId(), "git", T0.plusSeconds(60),
                    T0.plusSeconds(61), SyncRun.Outcome.SUCCEEDED, 0, 0, List.of(), List.of()));

            assertThat(repository.findLatest(CONNECTOR))
                    .hasValueSatisfying(r -> assertThat(r.connectorId()).isEqualTo(CONNECTOR));
            assertThat(repository.findLatest("git"))
                    .hasValueSatisfying(r -> assertThat(r.connectorId()).isEqualTo("git"));
        }
    }
}
