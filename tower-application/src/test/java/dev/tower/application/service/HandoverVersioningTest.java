package dev.tower.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.tower.application.port.in.ReleasePackUseCases.CreateReleasePack;
import dev.tower.application.port.out.ApplicationVersionRepository;
import dev.tower.application.port.out.HandoverRevisionRepository;
import dev.tower.application.port.out.PromotionPathRepository;
import dev.tower.application.port.out.ReleasePackRepository;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.handover.Handover;
import dev.tower.domain.handover.HandoverRevision;
import dev.tower.domain.promotionpath.PromotionPath;
import dev.tower.domain.promotionpath.PromotionPathId;
import dev.tower.domain.releasepack.ReleasePack;
import dev.tower.domain.releasepack.ReleasePackId;

/**
 * Issue #8, IA-02, ADR-016.
 *
 * <p>The property under test is that editing a Handover never destroys what was
 * there. Handover holds the rollback procedure someone follows when a release is
 * going wrong, and until this existed the first question asked afterwards — what
 * did we actually hand over — had no answer.
 */
@DisplayName("Handover versioning")
class HandoverVersioningTest {

    private static final Instant T0 = Instant.parse("2026-08-03T09:00:00Z");

    private MutableClock clock;
    private InMemoryRevisions revisions;
    private ReleasePackService service;
    private ReleasePackId pack;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        revisions = new InMemoryRevisions();
        service = new ReleasePackService(
                new InMemoryPacks(), new NoVersions(), new NoPaths(), revisions, clock);
        pack = service.create(new CreateReleasePack("Release 2026.08", "")).id();
    }

    private static Handover handover(String instructions) {
        return new Handover(instructions, "", "", "", "", "");
    }

    @Nested
    @DisplayName("keeps what was there")
    class Appending {

        @Test
        void the_first_edit_becomes_revision_one() {
            service.updateHandover(pack, handover("Deploy customer-api first."));

            assertThat(service.handoverHistory(pack)).singleElement().satisfies(revision -> {
                assertThat(revision.revisionNumber()).isEqualTo(1);
                assertThat(revision.handover().deploymentInstructions())
                        .isEqualTo("Deploy customer-api first.");
                assertThat(revision.recordedAt()).isEqualTo(T0);
            });
        }

        @Test
        void an_edit_does_not_overwrite_the_previous_revision() {
            // The whole point. Before this, the first text was simply gone.
            service.updateHandover(pack, handover("Deploy customer-api first."));
            clock.advance(Duration.ofHours(2));
            service.updateHandover(pack, handover("Deploy orders-api first, we were wrong."));

            assertThat(service.handoverHistory(pack))
                    .extracting(r -> r.handover().deploymentInstructions())
                    .containsExactly("Deploy orders-api first, we were wrong.", "Deploy customer-api first.");
        }

        @Test
        void history_is_newest_first() {
            service.updateHandover(pack, handover("one"));
            clock.advance(Duration.ofMinutes(5));
            service.updateHandover(pack, handover("two"));
            clock.advance(Duration.ofMinutes(5));
            service.updateHandover(pack, handover("three"));

            assertThat(service.handoverHistory(pack))
                    .extracting(HandoverRevision::revisionNumber)
                    .containsExactly(3, 2, 1);
        }

        @Test
        void the_newest_revision_is_what_the_release_pack_holds() {
            // ADR-016 accepts that the current Handover lives in two places. This
            // is the invariant that acceptance rests on.
            ReleasePack updated = service.updateHandover(pack, handover("current text"));

            assertThat(service.handoverHistory(pack).get(0).handover())
                    .isEqualTo(updated.handover());
        }

        @Test
        void records_an_edit_that_clears_the_handover() {
            // Deleting the instructions is an edit like any other, and the
            // interesting one: without a revision, "there were never any" and
            // "someone removed them" would look identical.
            service.updateHandover(pack, handover("Deploy customer-api first."));
            clock.advance(Duration.ofMinutes(1));
            service.updateHandover(pack, Handover.empty());

            List<HandoverRevision> history = service.handoverHistory(pack);
            assertThat(history).hasSize(2);
            assertThat(history.get(0).isEmpty()).isTrue();
            assertThat(history.get(1).handover().deploymentInstructions())
                    .isEqualTo("Deploy customer-api first.");
        }

        @Test
        void records_a_repeated_edit_rather_than_suppressing_it() {
            // Deliberately unlike ADR-011's change comparison for Observations.
            // An Observation repeated is not a new fact about the world; a
            // Handover saved again is a person saying "this is still what I
            // mean", and the timestamp is the information.
            service.updateHandover(pack, handover("same text"));
            clock.advance(Duration.ofHours(1));
            service.updateHandover(pack, handover("same text"));

            assertThat(service.handoverHistory(pack)).hasSize(2);
        }
    }

    @Nested
    @DisplayName("offers no way to lose a revision")
    class Immutability {

        @Test
        void the_repository_port_has_no_update_or_delete() {
            // The enforcement is the absence, so it is asserted rather than
            // trusted: a port that offered a delete would leave the rule to
            // whoever remembered it.
            assertThat(HandoverRevisionRepository.class.getDeclaredMethods())
                    .extracting(java.lang.reflect.Method::getName)
                    .containsExactlyInAnyOrder(
                            "append", "findAllByReleasePack", "findByReleasePackAndNumber",
                            "highestRevisionNumber");
        }

        @Test
        void restoring_an_old_handover_appends_rather_than_rewrites() {
            service.updateHandover(pack, handover("original"));
            clock.advance(Duration.ofMinutes(5));
            service.updateHandover(pack, handover("mistake"));
            clock.advance(Duration.ofMinutes(5));

            // Putting the original back is just another edit.
            service.updateHandover(pack, handover("original"));

            assertThat(service.handoverHistory(pack))
                    .extracting(HandoverRevision::revisionNumber)
                    .containsExactly(3, 2, 1);
            assertThat(service.handoverHistory(pack).get(1).handover().deploymentInstructions())
                    .isEqualTo("mistake");
        }
    }

    @Test
    @DisplayName("refuses history for a Release Pack that does not exist")
    void an_unknown_pack_is_not_found() {
        // Rather than an empty list, which would read as "nothing was ever
        // handed over" for a pack that is not there at all.
        assertThatThrownBy(() -> service.handoverHistory(ReleasePackId.newId()))
                .isInstanceOf(NotFoundException.class);
    }

    // --- doubles -----------------------------------------------------------

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private static final class InMemoryRevisions implements HandoverRevisionRepository {
        private final List<HandoverRevision> stored = new ArrayList<>();

        @Override
        public HandoverRevision append(HandoverRevision revision) {
            stored.add(revision);
            return revision;
        }

        @Override
        public List<HandoverRevision> findAllByReleasePack(ReleasePackId releasePackId) {
            return stored.stream()
                    .filter(r -> r.releasePackId().equals(releasePackId))
                    .sorted(Comparator.comparingInt(HandoverRevision::revisionNumber).reversed())
                    .toList();
        }

        @Override
        public Optional<HandoverRevision> findByReleasePackAndNumber(ReleasePackId id, int number) {
            return stored.stream()
                    .filter(r -> r.releasePackId().equals(id) && r.revisionNumber() == number)
                    .findFirst();
        }

        @Override
        public int highestRevisionNumber(ReleasePackId releasePackId) {
            return stored.stream()
                    .filter(r -> r.releasePackId().equals(releasePackId))
                    .mapToInt(HandoverRevision::revisionNumber)
                    .max().orElse(0);
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

        private boolean existsByName(String name) {
            return stored.stream().anyMatch(p -> p.name().equalsIgnoreCase(name));
        }

        @Override
        public boolean existsByNameIgnoringCase(String name) {
            return existsByName(name);
        }

        @Override
        public List<ReleasePack> findAllReferencingPromotionPath(PromotionPathId pathId) {
            return List.of();
        }

        @Override
        public List<ReleasePack> findAllContaining(ApplicationVersionId versionId) {
            return List.of();
        }

        @Override
        public void deleteById(ReleasePackId id) {
            stored.removeIf(p -> p.id().equals(id));
        }
    }

    private static final class NoVersions implements ApplicationVersionRepository {
        @Override
        public ApplicationVersion save(ApplicationVersion version) {
            return version;
        }

        @Override
        public Optional<ApplicationVersion> findById(ApplicationVersionId id) {
            return Optional.empty();
        }

        @Override
        public List<ApplicationVersion> findAll() {
            return List.of();
        }

        @Override
        public List<ApplicationVersion> findAllByApplication(ApplicationId applicationId) {
            return List.of();
        }

        @Override
        public List<ApplicationVersion> findAllById(List<ApplicationVersionId> ids) {
            return List.of();
        }

        @Override
        public boolean existsByApplicationAndVersion(ApplicationId applicationId, String version) {
            return false;
        }

        @Override
        public Optional<ApplicationVersion> findByApplicationAndVersion(
                ApplicationId applicationId, String version) {
            return Optional.empty();
        }

        @Override
        public void deleteById(ApplicationVersionId id) {
        }
    }

    private static final class NoPaths implements PromotionPathRepository {
        @Override
        public PromotionPath save(PromotionPath path) {
            return path;
        }

        @Override
        public Optional<PromotionPath> findById(PromotionPathId id) {
            return Optional.empty();
        }

        @Override
        public List<PromotionPath> findAll() {
            return List.of();
        }



        @Override
        public List<PromotionPath> findAllReferencing(dev.tower.domain.environment.EnvironmentId id) {
            return List.of();
        }

        @Override
        public boolean existsByNameIgnoringCase(String name) {
            return false;
        }

        @Override
        public void deleteById(PromotionPathId id) {
        }
    }
}
