package dev.tower.persistence.handover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.tower.domain.handover.Handover;
import dev.tower.domain.handover.HandoverRevision;
import dev.tower.domain.releasepack.ReleasePack;
import dev.tower.domain.releasepack.ReleasePackId;
import dev.tower.persistence.releasepack.ReleasePackJdbcRepository;
import dev.tower.persistence.support.PersistenceTestSupport;

@DisplayName("The Handover revision repository")
class HandoverRevisionJdbcRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-08-03T09:00:00Z");

    @TempDir
    Path tempDir;

    private JdbcClient jdbcClient;
    private HandoverRevisionJdbcRepository repository;
    private ReleasePackId pack;

    @BeforeEach
    void setUp() throws Exception {
        jdbcClient = PersistenceTestSupport.migratedJdbcClient(tempDir);
        repository = new HandoverRevisionJdbcRepository(jdbcClient);

        // A real Release Pack, because the foreign key is part of what is tested.
        var packs = new ReleasePackJdbcRepository(jdbcClient);
        pack = packs.save(ReleasePack.create("Release 2026.08", "")).id();
    }

    private HandoverRevision revision(int number, String instructions, Instant at) {
        return HandoverRevision.record(
                pack, number, new Handover(instructions, "", "", "", "", ""), at);
    }

    @Nested
    @DisplayName("keeps every version")
    class Appending {

        @Test
        void reloads_a_revision_with_all_six_fields_intact() {
            var handover = new Handover("deploy", "shell", "migrations", "rollback", "validation", "ops");
            repository.append(HandoverRevision.record(pack, 1, handover, T0));

            assertThat(repository.findByReleasePackAndNumber(pack, 1)).isPresent()
                    .get().satisfies(reloaded -> {
                        assertThat(reloaded.handover()).isEqualTo(handover);
                        assertThat(reloaded.recordedAt()).isEqualTo(T0);
                    });
        }

        @Test
        void returns_history_newest_first() {
            repository.append(revision(1, "one", T0));
            repository.append(revision(2, "two", T0.plusSeconds(60)));
            repository.append(revision(3, "three", T0.plusSeconds(120)));

            assertThat(repository.findAllByReleasePack(pack))
                    .extracting(HandoverRevision::revisionNumber)
                    .containsExactly(3, 2, 1);
        }

        @Test
        void reports_zero_for_a_pack_whose_handover_was_never_edited() {
            // So the first revision becomes 1 without a special case.
            assertThat(repository.highestRevisionNumber(pack)).isZero();
        }

        @Test
        void reports_the_highest_number_rather_than_the_count() {
            repository.append(revision(1, "one", T0));
            repository.append(revision(2, "two", T0.plusSeconds(60)));

            assertThat(repository.highestRevisionNumber(pack)).isEqualTo(2);
        }

        @Test
        void the_database_refuses_two_revisions_with_the_same_number() {
            // The number is how a revision is referred to, so a duplicate would
            // make "revision 2" ambiguous.
            repository.append(revision(1, "one", T0));

            assertThatThrownBy(() -> repository.append(revision(1, "another one", T0)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("does not let a revision be lost")
    class Immutability {

        @Test
        void deleting_the_release_pack_takes_its_revisions_with_it() {
            // The protection here is that an edit never loses history. Deleting
            // the whole release is a different act and already guarded on its
            // own terms; blocking it here would have made every Release Pack
            // whose Handover was ever touched permanently undeletable.
            repository.append(revision(1, "deploy customer-api first", T0));

            jdbcClient.sql("DELETE FROM release_pack WHERE id = :id")
                    .param("id", pack.value())
                    .update();

            assertThat(repository.findAllByReleasePack(pack)).isEmpty();
        }

        @Test
        void a_revision_survives_being_read_back_unchanged() {
            repository.append(revision(1, "original", T0));
            repository.append(revision(2, "replacement", T0.plusSeconds(60)));

            assertThat(repository.findByReleasePackAndNumber(pack, 1))
                    .get()
                    .satisfies(first -> assertThat(first.handover().deploymentInstructions())
                            .isEqualTo("original"));
        }
    }
}
