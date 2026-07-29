package dev.tower.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.tower.application.port.out.DeploymentObservationCollector;
import dev.tower.application.port.out.SyncRunRepository;
import dev.tower.application.sync.SyncRun;
import dev.tower.application.sync.SyncRunId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Synchronization use cases")
class SynchronizationServiceTest {

    private static final Instant T0 = Instant.parse("2026-07-29T10:00:00Z");

    private InMemorySyncRuns runs;

    @BeforeEach
    void setUp() {
        runs = new InMemorySyncRuns();
    }

    private static SyncRun aRun(String connectorId, Instant startedAt, SyncRun.Outcome outcome) {
        return new SyncRun(SyncRunId.newId(), connectorId, startedAt, startedAt.plusSeconds(1),
                outcome, 1, 0, List.of(), List.of());
    }

    @Nested
    @DisplayName("synchronize now")
    class SynchronizeNow {

        @Test
        void records_the_run_the_collector_produced() {
            var run = aRun("kubernetes", T0, SyncRun.Outcome.SUCCEEDED);
            var service = new SynchronizationService(List.of(new FixedCollector("kubernetes", run)), runs);

            List<SyncRun> completed = service.synchronizeNow();

            assertThat(completed).containsExactly(run);
            assertThat(runs.stored).containsExactly(run);
        }

        @Test
        void records_a_failed_run_too_because_a_failure_is_worth_knowing_about() {
            var failed = aRun("kubernetes", T0, SyncRun.Outcome.FAILED);
            var service = new SynchronizationService(List.of(new FixedCollector("kubernetes", failed)), runs);

            service.synchronizeNow();

            assertThat(runs.stored).singleElement()
                    .extracting(SyncRun::outcome).isEqualTo(SyncRun.Outcome.FAILED);
        }

        @Test
        void runs_every_collector_so_adding_one_needs_no_change_here() {
            var service = new SynchronizationService(List.of(
                    new FixedCollector("kubernetes", aRun("kubernetes", T0, SyncRun.Outcome.SUCCEEDED)),
                    new FixedCollector("git", aRun("git", T0, SyncRun.Outcome.SUCCEEDED))), runs);

            assertThat(service.synchronizeNow()).hasSize(2);
            assertThat(runs.stored).extracting(SyncRun::connectorId)
                    .containsExactly("kubernetes", "git");
        }

        @Test
        void does_nothing_and_says_so_when_no_collector_is_configured() {
            var service = new SynchronizationService(List.of(), runs);

            assertThat(service.synchronizeNow()).isEmpty();
            assertThat(runs.stored).isEmpty();
        }
    }

    @Nested
    @DisplayName("history")
    class History {

        @Test
        void applies_a_default_limit_rather_than_reading_an_unbounded_table() {
            var service = new SynchronizationService(List.of(), runs);

            service.history(0);

            assertThat(runs.lastLimitRequested)
                    .isEqualTo(SynchronizationService.DEFAULT_HISTORY_LIMIT);
        }

        @Test
        void honours_an_explicit_limit() {
            var service = new SynchronizationService(List.of(), runs);

            service.history(5);

            assertThat(runs.lastLimitRequested).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("last confirmation — the other half of ADR-011")
    class LastConfirmation {

        @Test
        void reports_the_newest_wholly_successful_run() {
            runs.append(aRun("kubernetes", T0, SyncRun.Outcome.SUCCEEDED));
            var service = new SynchronizationService(List.of(), runs);

            assertThat(service.lastConfirmation("kubernetes")).isPresent();
        }

        @Test
        void is_empty_when_nothing_has_ever_wholly_succeeded() {
            runs.append(aRun("kubernetes", T0, SyncRun.Outcome.PARTIALLY_SUCCEEDED));
            var service = new SynchronizationService(List.of(), runs);

            // The interface must be able to say "Tower has never confirmed this"
            // rather than imply an Observation is current.
            assertThat(service.lastConfirmation("kubernetes")).isEmpty();
        }

        @Test
        void requires_a_connector_to_ask_about() {
            var service = new SynchronizationService(List.of(), runs);

            assertThatThrownBy(() -> service.lastConfirmation(" "))
                    .isInstanceOf(InvalidRequestException.class);
        }
    }

    /** A Collector that has already decided what its run produced. */
    private record FixedCollector(String connectorId, SyncRun run)
            implements DeploymentObservationCollector {

        @Override
        public SyncRun collect() {
            return run;
        }
    }

    private static final class InMemorySyncRuns implements SyncRunRepository {

        private final List<SyncRun> stored = new ArrayList<>();
        private int lastLimitRequested;

        @Override
        public SyncRun append(SyncRun run) {
            stored.add(run);
            return run;
        }

        @Override
        public List<SyncRun> findRecent(int limit) {
            lastLimitRequested = limit;
            return stored.stream()
                    .sorted(Comparator.comparing(SyncRun::startedAt).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<SyncRun> findLatest(String connectorId) {
            return stored.stream()
                    .filter(r -> r.connectorId().equals(connectorId))
                    .max(Comparator.comparing(SyncRun::startedAt));
        }

        @Override
        public Optional<SyncRun> findLatestSuccessful(String connectorId) {
            return stored.stream()
                    .filter(r -> r.connectorId().equals(connectorId) && r.confirmsLiveness())
                    .max(Comparator.comparing(SyncRun::startedAt));
        }
    }
}
