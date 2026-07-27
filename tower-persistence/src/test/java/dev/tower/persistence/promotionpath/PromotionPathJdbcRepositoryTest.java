package dev.tower.persistence.promotionpath;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.environment.Stage;
import dev.tower.domain.promotionpath.PromotionPath;
import dev.tower.domain.promotionpath.PromotionPathId;
import dev.tower.persistence.environment.EnvironmentJdbcRepository;
import dev.tower.persistence.support.PersistenceTestSupport;

class PromotionPathJdbcRepositoryTest {

    @TempDir
    Path tempDir;

    private PromotionPathJdbcRepository pathRepository;
    private EnvironmentJdbcRepository environmentRepository;
    private Clock clock;

    private Environment dev1;
    private Environment sit1;
    private Environment uat;
    private Environment preProd;
    private Environment production;
    private Environment devHotfix;
    private Environment sit;

    @BeforeEach
    void setUp() throws Exception {
        JdbcClient jdbcClient = PersistenceTestSupport.migratedJdbcClient(tempDir);
        pathRepository = new PromotionPathJdbcRepository(jdbcClient);
        environmentRepository = new EnvironmentJdbcRepository(jdbcClient);
        clock = Clock.fixed(Instant.parse("2026-07-27T10:00:00Z"), ZoneOffset.UTC);

        dev1 = environmentRepository.save(Environment.create("Dev1", Stage.DEVELOPMENT));
        sit1 = environmentRepository.save(Environment.create("SIT1", Stage.VALIDATION));
        uat = environmentRepository.save(Environment.create("UAT", Stage.VALIDATION));
        preProd = environmentRepository.save(Environment.create("PreProd", Stage.PRE_PRODUCTION));
        production = environmentRepository.save(Environment.create("Production", Stage.PRODUCTION));
        devHotfix = environmentRepository.save(Environment.create("Dev-Hotfix", Stage.DEVELOPMENT));
        sit = environmentRepository.save(Environment.create("SIT", Stage.VALIDATION));
    }

    @Test
    void savesAndReloadsAPathWithItsFirstVersionInOrder() {
        List<EnvironmentId> sequence = List.of(dev1.id(), sit1.id(), uat.id(), preProd.id(), production.id());
        PromotionPath created = pathRepository.save(PromotionPath.create("Regular", sequence, clock.instant()));

        PromotionPath reloaded = pathRepository.findById(created.id()).orElseThrow();

        assertThat(reloaded.name()).isEqualTo("Regular");
        assertThat(reloaded.isArchived()).isFalse();
        assertThat(reloaded.versions()).hasSize(1);
        assertThat(reloaded.currentVersion().environments()).containsExactlyElementsOf(sequence);
    }

    @Test
    void findByIdReturnsEmptyForAnUnknownId() {
        assertThat(pathRepository.findById(PromotionPathId.newId())).isEmpty();
    }

    /**
     * ADR-005: an Environment may appear in many Promotion Paths. Two different paths both
     * reference UAT and Production; both must persist and reload correctly, and each must report
     * only its own environment sequence (convergence, not merging).
     */
    @Test
    void adr005TwoDifferentPathsCanShareTheSameEnvironmentsAndBothLoadCorrectly() {
        List<EnvironmentId> regularSequence =
                List.of(dev1.id(), sit1.id(), uat.id(), preProd.id(), production.id());
        List<EnvironmentId> hotfixSequence =
                List.of(devHotfix.id(), sit.id(), uat.id(), production.id());

        PromotionPath regular = pathRepository.save(PromotionPath.create("Regular", regularSequence, clock.instant()));
        PromotionPath hotfix = pathRepository.save(PromotionPath.create("Hotfix", hotfixSequence, clock.instant()));

        PromotionPath reloadedRegular = pathRepository.findById(regular.id()).orElseThrow();
        PromotionPath reloadedHotfix = pathRepository.findById(hotfix.id()).orElseThrow();

        assertThat(reloadedRegular.currentVersion().environments()).containsExactlyElementsOf(regularSequence);
        assertThat(reloadedHotfix.currentVersion().environments()).containsExactlyElementsOf(hotfixSequence);

        // Both paths reference UAT and Production (convergence); neither path was corrupted by
        // the other's reference to the same Environment rows.
        assertThat(reloadedRegular.referencesEnvironment(uat.id())).isTrue();
        assertThat(reloadedRegular.referencesEnvironment(production.id())).isTrue();
        assertThat(reloadedHotfix.referencesEnvironment(uat.id())).isTrue();
        assertThat(reloadedHotfix.referencesEnvironment(production.id())).isTrue();

        // findAllReferencing spans both paths for a shared Environment (ADR-005 returns a list,
        // not an optional, precisely because convergence is expected).
        List<PromotionPath> referencingUat = pathRepository.findAllReferencing(uat.id());
        assertThat(referencingUat).extracting(PromotionPath::id)
                .containsExactlyInAnyOrder(regular.id(), hotfix.id());

        // An Environment referenced by only one path is not reported for the other.
        assertThat(pathRepository.findAllReferencing(sit1.id()))
                .extracting(PromotionPath::id)
                .containsExactly(regular.id());
    }

    /**
     * ADR-007: publishing a new version never touches an earlier one. Reload after publishing
     * version 2 and confirm version 1's environment order is byte-for-byte what it was created
     * with, and that version 2's own order is exactly what was published - not merely the same
     * set of Environments in some order (a reordered load is a silent corruption).
     */
    @Test
    void adr007PublishingASecondVersionLeavesTheFirstVersionUnchangedAndBothOrdersExact() {
        List<EnvironmentId> versionOneSequence = List.of(dev1.id(), sit1.id(), uat.id(), production.id());
        PromotionPath created = pathRepository.save(
                PromotionPath.create("Regular", versionOneSequence, Instant.parse("2026-07-27T09:00:00Z")));

        // Reorders SIT1 and UAT and adds PreProd - a distinct sequence, not just a superset, so
        // that a naive "same set, any order" bug would be caught.
        List<EnvironmentId> versionTwoSequence =
                List.of(dev1.id(), uat.id(), sit1.id(), preProd.id(), production.id());
        PromotionPath updated = pathRepository.save(
                created.withNewVersion(versionTwoSequence, Instant.parse("2026-07-27T11:00:00Z")));
        assertThat(updated.versions()).hasSize(2);

        PromotionPath reloaded = pathRepository.findById(created.id()).orElseThrow();

        assertThat(reloaded.versions()).hasSize(2);

        assertThat(reloaded.version(1)).isPresent();
        assertThat(reloaded.version(1).get().environments()).containsExactlyElementsOf(versionOneSequence);
        assertThat(reloaded.version(1).get().createdAt()).isEqualTo(Instant.parse("2026-07-27T09:00:00Z"));

        assertThat(reloaded.version(2)).isPresent();
        assertThat(reloaded.version(2).get().environments()).containsExactlyElementsOf(versionTwoSequence);
        assertThat(reloaded.version(2).get().createdAt()).isEqualTo(Instant.parse("2026-07-27T11:00:00Z"));

        // currentVersion() reports the newest, and it is version 2's *exact* sequence.
        assertThat(reloaded.currentVersion().number()).isEqualTo(2);
        assertThat(reloaded.currentVersion().environments()).containsExactlyElementsOf(versionTwoSequence);
    }

    @Test
    void renamingArchivingAndRestoringPersist() {
        PromotionPath created = pathRepository.save(
                PromotionPath.create("Regular", List.of(dev1.id(), production.id()), clock.instant()));

        pathRepository.save(created.rename("Regular Path"));
        assertThat(pathRepository.findById(created.id()).orElseThrow().name()).isEqualTo("Regular Path");

        PromotionPath archived = pathRepository.save(
                pathRepository.findById(created.id()).orElseThrow().archive());
        assertThat(archived.isArchived()).isTrue();
        assertThat(pathRepository.findById(created.id()).orElseThrow().isArchived()).isTrue();

        PromotionPath restored = pathRepository.save(
                pathRepository.findById(created.id()).orElseThrow().restore());
        assertThat(restored.isArchived()).isFalse();
        assertThat(pathRepository.findById(created.id()).orElseThrow().isArchived()).isFalse();
    }

    @Test
    void existsByNameIgnoringCaseIsCaseInsensitive() {
        pathRepository.save(PromotionPath.create("Regular", List.of(dev1.id(), production.id()), clock.instant()));

        assertThat(pathRepository.existsByNameIgnoringCase("regular")).isTrue();
        assertThat(pathRepository.existsByNameIgnoringCase("REGULAR")).isTrue();
        assertThat(pathRepository.existsByNameIgnoringCase("Hotfix")).isFalse();
    }

    @Test
    void findAllOrdersByNameAndLoadsEachPathWithItsVersionsInOrder() {
        List<EnvironmentId> regularSequence = List.of(dev1.id(), sit1.id(), uat.id(), production.id());
        List<EnvironmentId> hotfixSequence = List.of(devHotfix.id(), sit.id(), uat.id(), production.id());
        pathRepository.save(PromotionPath.create("Regular", regularSequence, clock.instant()));
        pathRepository.save(PromotionPath.create("Hotfix", hotfixSequence, clock.instant()));

        List<PromotionPath> all = pathRepository.findAll();

        assertThat(all).extracting(PromotionPath::name).containsExactly("Hotfix", "Regular");
        PromotionPath hotfix = all.stream().filter(p -> p.name().equals("Hotfix")).findFirst().orElseThrow();
        assertThat(hotfix.currentVersion().environments()).containsExactlyElementsOf(hotfixSequence);
    }

    @Test
    void deleteByIdRemovesAnUnreferencedSingleVersionPathAndItsJoinRows() {
        PromotionPath created = pathRepository.save(
                PromotionPath.create("Regular", List.of(dev1.id(), production.id()), clock.instant()));

        pathRepository.deleteById(created.id());

        assertThat(pathRepository.findById(created.id())).isEmpty();
        // The cascade must not have removed the Environments themselves (ADR-005: a Promotion
        // Path never owns the Environments it references).
        assertThat(environmentRepository.findById(dev1.id())).isPresent();
        assertThat(environmentRepository.findById(production.id())).isPresent();
    }
}
