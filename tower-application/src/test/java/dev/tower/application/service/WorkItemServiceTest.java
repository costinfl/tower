package dev.tower.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.tower.application.binding.ApplicationBinding;
import dev.tower.application.binding.EnvironmentBinding;
import dev.tower.application.binding.IssueTrackerBinding;
import dev.tower.application.binding.RepositoryBinding;
import dev.tower.application.port.in.WorkItemUseCases.Resolution;
import dev.tower.application.port.in.WorkItemUseCases.State;
import dev.tower.application.port.out.ExternalBindingRepository;
import dev.tower.application.port.out.ReleasePackRepository;
import dev.tower.application.port.out.WorkItemCollector;
import dev.tower.application.sync.ConnectionTest;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.promotionpath.PromotionPathId;
import dev.tower.domain.releasepack.ReleasePack;
import dev.tower.domain.releasepack.ReleasePackId;
import dev.tower.domain.releasepack.WorkItemReference;

/**
 * ADR-018: resolving work items against the tracker, and storing none of it.
 *
 * <p>Most of what matters here is what Tower says when it cannot get an answer.
 * A tracker that is unconfigured, unreachable or does not know an identifier
 * produces three different statements, and collapsing any of them into "not
 * found" would be the same mistake as reporting an unobserved Environment as
 * empty.
 */
@DisplayName("Resolving work items against a tracker")
class WorkItemServiceTest {

    private InMemoryPacks packs;
    private InMemoryBindings bindings;
    private StubCollector tracker;
    private WorkItemService service;
    private ReleasePack pack;

    @BeforeEach
    void setUp() {
        packs = new InMemoryPacks();
        bindings = new InMemoryBindings();
        tracker = new StubCollector();
        service = new WorkItemService(packs, bindings, List.of(tracker));

        pack = packs.save(ReleasePack.create("Release 2026.08", "")
                .linkWorkItem(new WorkItemReference("PROJ-123", "Save basket"))
                .linkWorkItem(WorkItemReference.of("PROJ-140")));
    }

    private void bindTracker() {
        bindings.save(new IssueTrackerBinding("stub-tracker", "acme/retail"));
    }

    @Nested
    @DisplayName("says what it could not find out")
    class Honesty {

        @Test
        void with_no_tracker_configured_it_says_so_and_still_lists_the_references() {
            // A release does not become less true because no tracker is
            // configured, and a screen showing nothing would say the opposite.
            Resolution resolution = service.resolve(pack.id());

            assertThat(resolution.reachedTracker()).isFalse();
            assertThat(resolution.failure()).contains("No issue tracker is configured");
            assertThat(resolution.items()).hasSize(2)
                    .allSatisfy(item -> assertThat(item.state()).isEqualTo(State.UNRESOLVED));
            // What was accepted is still shown, because that is what documents print.
            assertThat(resolution.items().get(0).acceptedTitle()).isEqualTo("Save basket");
        }

        @Test
        void an_unreachable_tracker_leaves_the_references_intact_and_explains() {
            bindTracker();
            tracker.failWith("the credential was refused");

            Resolution resolution = service.resolve(pack.id());

            assertThat(resolution.reachedTracker()).isFalse();
            assertThat(resolution.failure()).contains("could not be read")
                    .contains("the credential was refused");
            assertThat(resolution.items()).hasSize(2)
                    .allSatisfy(item -> assertThat(item.state()).isEqualTo(State.UNRESOLVED));
        }

        @Test
        void an_identifier_the_tracker_does_not_know_is_not_found_rather_than_unresolved() {
            // The tracker answered. "It does not have this" is a fact; "Tower
            // could not ask" is not, and the two must not read the same.
            bindTracker();
            tracker.knows("PROJ-123", "Save basket", "In Progress", false);

            Resolution resolution = service.resolve(pack.id());

            assertThat(resolution.reachedTracker()).isTrue();
            assertThat(resolution.items()).extracting("identifier", "state")
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("PROJ-123", State.RESOLVED),
                            org.assertj.core.groups.Tuple.tuple("PROJ-140", State.NOT_FOUND));
        }

        @Test
        void a_release_that_does_not_exist_says_so() {
            assertThatThrownBy(() -> service.resolve(ReleasePackId.newId()))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("shows drift without correcting it")
    class Divergence {

        @Test
        void a_rewritten_summary_is_reported_as_diverged() {
            bindTracker();
            tracker.knows("PROJ-123", "Save basket and remember it", "In Progress", false);

            Resolution resolution = service.resolve(pack.id());

            assertThat(resolution.items().get(0)).satisfies(item -> {
                assertThat(item.state()).isEqualTo(State.DIVERGED);
                assertThat(item.hasDiverged()).isTrue();
                // Both are shown. Tower does not choose for the reader, and it
                // certainly does not overwrite what a document already printed.
                assertThat(item.acceptedTitle()).isEqualTo("Save basket");
                assertThat(item.trackerTitle()).isEqualTo("Save basket and remember it");
            });
        }

        @Test
        void resolving_never_changes_what_tower_holds() {
            // The property the whole decision rests on: NFR-025 survives because
            // reading the tracker cannot alter a document's inputs.
            bindTracker();
            tracker.knows("PROJ-123", "Something else entirely", "Done", true);

            service.resolve(pack.id());

            assertThat(packs.findById(pack.id()).orElseThrow().workItems())
                    .extracting(WorkItemReference::title)
                    .containsExactly("Save basket", "");
        }

        @Test
        void a_reference_with_no_accepted_title_has_nothing_to_diverge_from() {
            bindTracker();
            tracker.knows("PROJ-140", "Address validation", "To Do", false);

            Resolution resolution = service.resolve(pack.id());

            assertThat(resolution.items().get(1)).satisfies(item -> {
                assertThat(item.state()).isEqualTo(State.RESOLVED);
                assertThat(item.acceptedTitle()).isEmpty();
                assertThat(item.trackerTitle()).isEqualTo("Address validation");
            });
        }

        @Test
        void matches_the_tracker_case_insensitively() {
            bindTracker();
            tracker.knows("proj-123", "Save basket", "In Progress", false);

            assertThat(service.resolve(pack.id()).items().get(0).state()).isEqualTo(State.RESOLVED);
        }

        @Test
        void carries_the_trackers_own_word_for_status_rather_than_a_tower_one() {
            // ADR-018: normalising status would mean Tower deciding which of a
            // team's states counts as finished.
            bindTracker();
            tracker.knows("PROJ-123", "Save basket", "Awaiting business sign-off", false);

            assertThat(service.resolve(pack.id()).items().get(0).status())
                    .isEqualTo("Awaiting business sign-off");
        }
    }

    @Nested
    @DisplayName("connection test")
    class Connection {

        @Test
        void reports_that_no_tracker_is_bound() {
            var report = service.testConnection("stub-tracker");

            assertThat(report.reachable()).isFalse();
            assertThat(report.message()).contains("No tracker is bound");
        }

        @Test
        void reports_an_unknown_connector_rather_than_failing() {
            bindings.save(new IssueTrackerBinding("nonexistent", "somewhere"));

            var report = service.testConnection("nonexistent");

            assertThat(report.reachable()).isFalse();
            assertThat(report.message()).contains("No Connector named 'nonexistent' is installed");
        }

        @Test
        void reports_the_locator_it_would_read() {
            bindTracker();

            var report = service.testConnection("stub-tracker");

            assertThat(report.locator()).isEqualTo("acme/retail");
            assertThat(report.reachable()).isTrue();
        }
    }

    // --- fakes ---------------------------------------------------------------

    private static final class StubCollector implements WorkItemCollector {
        private final List<CollectedWorkItem> known = new ArrayList<>();
        private String failure;

        void knows(String identifier, String title, String status, boolean closed) {
            known.add(new CollectedWorkItem(identifier, title, status, closed,
                    "https://tracker.example/" + identifier));
        }

        void failWith(String message) {
            this.failure = message;
        }

        @Override
        public String connectorId() {
            return "stub-tracker";
        }

        @Override
        public List<CollectedWorkItem> read(List<String> identifiers) {
            if (failure != null) {
                throw new IllegalStateException(failure);
            }
            return List.copyOf(known);
        }

        @Override
        public ConnectionTest checkConnection() {
            return failure == null
                    ? ConnectionTest.reachable("stub-tracker", "acme/retail", "whole tracker")
                    : ConnectionTest.unreachable("stub-tracker", "acme/retail", "whole tracker", failure);
        }
    }

    private static final class InMemoryPacks implements ReleasePackRepository {
        private final List<ReleasePack> stored = new ArrayList<>();

        @Override
        public ReleasePack save(ReleasePack pack) {
            stored.removeIf(p -> p.id().equals(pack.id()));
            stored.add(pack);
            return pack;
        }

        @Override
        public Optional<ReleasePack> findById(ReleasePackId id) {
            return stored.stream().filter(p -> p.id().equals(id)).findFirst();
        }

        @Override
        public List<ReleasePack> findAll() {
            return List.copyOf(stored);
        }

        @Override
        public boolean existsByNameIgnoringCase(String name) {
            return false;
        }

        @Override
        public List<ReleasePack> findAllContaining(ApplicationVersionId versionId) {
            return List.of();
        }

        @Override
        public List<ReleasePack> findAllReferencingPromotionPath(PromotionPathId pathId) {
            return List.of();
        }

        @Override
        public void deleteById(ReleasePackId id) {
            stored.removeIf(p -> p.id().equals(id));
        }
    }

    private static final class InMemoryBindings implements ExternalBindingRepository {
        private final List<IssueTrackerBinding> trackers = new ArrayList<>();

        @Override
        public IssueTrackerBinding save(IssueTrackerBinding binding) {
            trackers.removeIf(b -> b.connectorId().equals(binding.connectorId()));
            trackers.add(binding);
            return binding;
        }

        @Override
        public Optional<IssueTrackerBinding> findIssueTrackerBinding(String connectorId) {
            return trackers.stream().filter(b -> b.connectorId().equals(connectorId)).findFirst();
        }

        @Override
        public List<IssueTrackerBinding> findAllIssueTrackerBindings() {
            return List.copyOf(trackers);
        }

        @Override
        public void deleteIssueTrackerBinding(String connectorId) {
            trackers.removeIf(b -> b.connectorId().equals(connectorId));
        }

        // Pipeline job bindings (ADR-020). Kept in a map keyed the way the table
        // is, so the fake enforces the same "one job per Environment, Application
        // and Connector" rule the schema does.
        private final java.util.Map<String, dev.tower.application.binding.PipelineJobBinding>
                pipelineJobBindings = new java.util.HashMap<>();

        @Override
        public dev.tower.application.binding.PipelineJobBinding save(
                dev.tower.application.binding.PipelineJobBinding binding) {
            pipelineJobBindings.put(pipelineKey(
                    binding.environmentId(), binding.applicationId(), binding.connectorId()), binding);
            return binding;
        }

        @Override
        public java.util.Optional<dev.tower.application.binding.PipelineJobBinding>
                findPipelineJobBinding(EnvironmentId environmentId, ApplicationId applicationId,
                                       String connectorId) {
            return java.util.Optional.ofNullable(
                    pipelineJobBindings.get(pipelineKey(environmentId, applicationId, connectorId)));
        }

        @Override
        public java.util.List<dev.tower.application.binding.PipelineJobBinding>
                findAllPipelineJobBindings(String connectorId) {
            return pipelineJobBindings.values().stream()
                    .filter(b -> b.connectorId().equals(connectorId)).toList();
        }

        @Override
        public java.util.List<dev.tower.application.binding.PipelineJobBinding>
                findAllPipelineJobBindings() {
            return java.util.List.copyOf(pipelineJobBindings.values());
        }

        @Override
        public void deletePipelineJobBinding(EnvironmentId environmentId, ApplicationId applicationId,
                                             String connectorId) {
            pipelineJobBindings.remove(pipelineKey(environmentId, applicationId, connectorId));
        }

        private static String pipelineKey(EnvironmentId environmentId, ApplicationId applicationId,
                                          String connectorId) {
            return environmentId + "|" + applicationId + "|" + connectorId;
        }

        @Override
        public EnvironmentBinding save(EnvironmentBinding binding) {
            return binding;
        }

        @Override
        public Optional<EnvironmentBinding> findEnvironmentBinding(EnvironmentId id, String connectorId) {
            return Optional.empty();
        }

        @Override
        public List<EnvironmentBinding> findAllEnvironmentBindings(String connectorId) {
            return List.of();
        }

        @Override
        public List<EnvironmentBinding> findAllEnvironmentBindings() {
            return List.of();
        }

        @Override
        public void deleteEnvironmentBinding(EnvironmentId id, String connectorId) {
        }

        @Override
        public ApplicationBinding save(ApplicationBinding binding) {
            return binding;
        }

        @Override
        public Optional<ApplicationBinding> findApplicationBinding(ApplicationId id, String connectorId) {
            return Optional.empty();
        }

        @Override
        public List<ApplicationBinding> findAllApplicationBindings(String connectorId) {
            return List.of();
        }

        @Override
        public List<ApplicationBinding> findAllApplicationBindings() {
            return List.of();
        }

        @Override
        public void deleteApplicationBinding(ApplicationId id, String connectorId) {
        }

        @Override
        public RepositoryBinding save(RepositoryBinding binding) {
            return binding;
        }

        @Override
        public Optional<RepositoryBinding> findRepositoryBinding(ApplicationId id, String connectorId) {
            return Optional.empty();
        }

        @Override
        public List<RepositoryBinding> findAllRepositoryBindings(String connectorId) {
            return List.of();
        }

        @Override
        public List<RepositoryBinding> findAllRepositoryBindings() {
            return List.of();
        }

        @Override
        public void deleteRepositoryBinding(ApplicationId id, String connectorId) {
        }
    }
}
