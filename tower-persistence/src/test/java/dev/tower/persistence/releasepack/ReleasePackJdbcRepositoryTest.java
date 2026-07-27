package dev.tower.persistence.releasepack;

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

import dev.tower.domain.application.Application;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.Stage;
import dev.tower.domain.handover.Handover;
import dev.tower.domain.iteration.Iteration;
import dev.tower.domain.promotionpath.PromotionPath;
import dev.tower.domain.releasepack.ReleasePack;
import dev.tower.domain.releasepack.ReleasePackId;
import dev.tower.persistence.application.ApplicationJdbcRepository;
import dev.tower.persistence.application.ApplicationVersionJdbcRepository;
import dev.tower.persistence.environment.EnvironmentJdbcRepository;
import dev.tower.persistence.promotionpath.PromotionPathJdbcRepository;
import dev.tower.persistence.support.PersistenceTestSupport;

class ReleasePackJdbcRepositoryTest {

    @TempDir
    Path tempDir;

    private ReleasePackJdbcRepository releasePacks;
    private ApplicationJdbcRepository applications;
    private ApplicationVersionJdbcRepository applicationVersions;
    private EnvironmentJdbcRepository environments;
    private PromotionPathJdbcRepository promotionPaths;
    private Clock clock;

    private Application payments;
    private ApplicationVersion paymentsV1;

    @BeforeEach
    void setUp() throws Exception {
        JdbcClient jdbcClient = PersistenceTestSupport.migratedJdbcClient(tempDir);
        releasePacks = new ReleasePackJdbcRepository(jdbcClient);
        applications = new ApplicationJdbcRepository(jdbcClient);
        applicationVersions = new ApplicationVersionJdbcRepository(jdbcClient);
        environments = new EnvironmentJdbcRepository(jdbcClient);
        promotionPaths = new PromotionPathJdbcRepository(jdbcClient);
        clock = Clock.fixed(Instant.parse("2026-07-27T10:00:00Z"), ZoneOffset.UTC);

        payments = applications.save(Application.create("Payments", ""));
        paymentsV1 = applicationVersions.save(
                ApplicationVersion.create(payments.id(), "1.0.0", "main", null, null, null));
    }

    @Test
    void savesAndReloadsAnEmptyPack() {
        ReleasePack saved = releasePacks.save(ReleasePack.create("2026-08 Release", "Quarterly release"));

        ReleasePack reloaded = releasePacks.findById(saved.id()).orElseThrow();

        assertThat(reloaded.name()).isEqualTo("2026-08 Release");
        assertThat(reloaded.description()).isEqualTo("Quarterly release");
        assertThat(reloaded.isArchived()).isFalse();
        assertThat(reloaded.promotionPath()).isEmpty();
        assertThat(reloaded.contents()).isEmpty();
        assertThat(reloaded.handover().isEmpty()).isTrue();
        assertThat(reloaded.iterations()).isEmpty();
    }

    @Test
    void findByIdReturnsEmptyForAnUnknownId() {
        assertThat(releasePacks.findById(ReleasePackId.newId())).isEmpty();
    }

    @Test
    void addingAndRemovingApplicationVersionsPersists() {
        ReleasePack pack = releasePacks.save(ReleasePack.create("Release", ""));

        ReleasePack withContent = releasePacks.save(
                pack.addApplicationVersion(payments.id(), paymentsV1.id()));
        assertThat(releasePacks.findById(pack.id()).orElseThrow().contents()).hasSize(1);

        ReleasePack withoutContent = releasePacks.save(withContent.removeApplicationVersion(paymentsV1.id()));
        assertThat(releasePacks.findById(pack.id()).orElseThrow().contents()).isEmpty();
        assertThat(withoutContent.contents()).isEmpty();
    }

    /**
     * ADR-007: a Release Pack pins a specific Promotion Path *version*. Publishing a new version
     * of the same path afterwards must not change what an already-saved, already-reloaded pack
     * reports - it keeps describing version 1 even though the path itself has moved on to
     * version 2.
     */
    @Test
    void adr007APackPinnedToAPromotionPathVersionStillReportsThatVersionAfterANewVersionIsPublished() {
        Environment dev = environments.save(Environment.create("Dev1", Stage.DEVELOPMENT));
        Environment prod = environments.save(Environment.create("Production", Stage.PRODUCTION));
        PromotionPath path = promotionPaths.save(
                PromotionPath.create("Regular", List.of(dev.id(), prod.id()), clock.instant()));

        ReleasePack pack = releasePacks.save(ReleasePack.create("Release", ""));
        ReleasePack pinned = releasePacks.save(pack.assignPromotionPath(path.id(), 1));
        assertThat(pinned.promotionPath()).isPresent();
        assertThat(pinned.promotionPath().orElseThrow().versionNumber()).isEqualTo(1);

        // Publish version 2 of the path after the pack has already pinned version 1.
        Environment uat = environments.save(Environment.create("UAT", Stage.VALIDATION));
        promotionPaths.save(path.withNewVersion(List.of(dev.id(), uat.id(), prod.id()), clock.instant()));

        ReleasePack reloaded = releasePacks.findById(pack.id()).orElseThrow();

        assertThat(reloaded.promotionPath()).isPresent();
        assertThat(reloaded.promotionPath().orElseThrow().pathId()).isEqualTo(path.id());
        assertThat(reloaded.promotionPath().orElseThrow().versionNumber()).isEqualTo(1);
    }

    @Test
    void clearingThePromotionPathPersists() {
        Environment dev = environments.save(Environment.create("Dev1", Stage.DEVELOPMENT));
        PromotionPath path = promotionPaths.save(PromotionPath.create("Regular", List.of(dev.id()), clock.instant()));
        ReleasePack pack = releasePacks.save(
                releasePacks.save(ReleasePack.create("Release", "")).assignPromotionPath(path.id(), 1));

        releasePacks.save(pack.clearPromotionPath());

        assertThat(releasePacks.findById(pack.id()).orElseThrow().promotionPath()).isEmpty();
    }

    /**
     * FR-006 and gap G4: Handover and every Iteration a pack carries must survive a save/reload
     * round trip byte-for-byte, including a completed Iteration's completion timestamp.
     */
    @Test
    void handoverAndIterationsSurviveASaveReloadRoundTripIntact() {
        ReleasePack pack = releasePacks.save(ReleasePack.create("Release", ""));

        Handover handover = new Handover(
                "Deploy via the standard pipeline.",
                "kubectl apply -f release.yaml",
                "V42__add_column.sql",
                "kubectl rollout undo deployment/payments",
                "Smoke test the checkout flow.",
                "Notify #ops-channel before starting.");
        ReleasePack withHandover = releasePacks.save(pack.updateHandover(handover));

        Instant startedAt = Instant.parse("2026-07-27T09:00:00Z");
        Instant completedAt = Instant.parse("2026-07-27T11:30:00Z");
        ReleasePack withIteration = releasePacks.save(
                withHandover.startIteration("SIT Iteration 1", startedAt, "Initial pass"));
        Iteration started = withIteration.iterations().get(0);
        ReleasePack withCompletedIteration = releasePacks.save(
                withIteration.replaceIteration(started.complete(completedAt)));

        ReleasePack reloaded = releasePacks.findById(pack.id()).orElseThrow();

        assertThat(reloaded.handover()).isEqualTo(handover);

        assertThat(reloaded.iterations()).hasSize(1);
        Iteration reloadedIteration = reloaded.iterations().get(0);
        assertThat(reloadedIteration.id()).isEqualTo(started.id());
        assertThat(reloadedIteration.name()).isEqualTo("SIT Iteration 1");
        assertThat(reloadedIteration.startedAt()).isEqualTo(startedAt);
        assertThat(reloadedIteration.completedAt()).isEqualTo(completedAt);
        assertThat(reloadedIteration.notes()).isEqualTo("Initial pass");
        assertThat(reloadedIteration.isComplete()).isTrue();

        assertThat(withCompletedIteration.iterations().get(0).completedAt()).isEqualTo(completedAt);
    }

    @Test
    void reopeningAndRemovingAnIterationPersists() {
        ReleasePack pack = releasePacks.save(ReleasePack.create("Release", ""));
        ReleasePack started = releasePacks.save(
                pack.startIteration("UAT Iteration", Instant.parse("2026-07-27T09:00:00Z"), ""));
        Iteration iteration = started.iterations().get(0);

        ReleasePack completed = releasePacks.save(
                started.replaceIteration(iteration.complete(Instant.parse("2026-07-27T10:00:00Z"))));
        assertThat(releasePacks.findById(pack.id()).orElseThrow().iterations().get(0).isComplete()).isTrue();

        ReleasePack reopened = releasePacks.save(
                completed.replaceIteration(completed.iterations().get(0).reopen()));
        assertThat(releasePacks.findById(pack.id()).orElseThrow().iterations().get(0).isComplete()).isFalse();

        releasePacks.save(reopened.removeIteration(iteration.id()));
        assertThat(releasePacks.findById(pack.id()).orElseThrow().iterations()).isEmpty();
    }

    @Test
    void archivingAndRestoringPersists() {
        ReleasePack pack = releasePacks.save(ReleasePack.create("Release", ""));

        releasePacks.save(releasePacks.findById(pack.id()).orElseThrow().archive());
        assertThat(releasePacks.findById(pack.id()).orElseThrow().isArchived()).isTrue();

        releasePacks.save(releasePacks.findById(pack.id()).orElseThrow().restore());
        assertThat(releasePacks.findById(pack.id()).orElseThrow().isArchived()).isFalse();
    }

    @Test
    void existsByNameIgnoringCaseIsCaseInsensitive() {
        releasePacks.save(ReleasePack.create("Release", ""));

        assertThat(releasePacks.existsByNameIgnoringCase("release")).isTrue();
        assertThat(releasePacks.existsByNameIgnoringCase("RELEASE")).isTrue();
        assertThat(releasePacks.existsByNameIgnoringCase("Other")).isFalse();
    }

    @Test
    void findAllOrdersByName() {
        releasePacks.save(ReleasePack.create("Zephyr Release", ""));
        releasePacks.save(ReleasePack.create("Alpha Release", ""));

        assertThat(releasePacks.findAll()).extracting(ReleasePack::name)
                .containsExactly("Alpha Release", "Zephyr Release");
    }

    @Test
    void findAllContainingReturnsOnlyPacksHoldingTheGivenVersion() {
        ApplicationVersion otherVersion = applicationVersions.save(
                ApplicationVersion.create(payments.id(), "2.0.0", null, null, null, null));
        ReleasePack withV1 = releasePacks.save(
                releasePacks.save(ReleasePack.create("Release A", "")).addApplicationVersion(payments.id(), paymentsV1.id()));
        releasePacks.save(
                releasePacks.save(ReleasePack.create("Release B", "")).addApplicationVersion(payments.id(), otherVersion.id()));

        List<ReleasePack> found = releasePacks.findAllContaining(paymentsV1.id());

        assertThat(found).extracting(ReleasePack::id).containsExactly(withV1.id());
    }

    @Test
    void findAllReferencingPromotionPathReturnsOnlyPacksCurrentlyPinnedToThatPath() {
        Environment dev = environments.save(Environment.create("Dev1", Stage.DEVELOPMENT));
        PromotionPath path = promotionPaths.save(PromotionPath.create("Regular", List.of(dev.id()), clock.instant()));
        PromotionPath otherPath = promotionPaths.save(PromotionPath.create("Hotfix", List.of(dev.id()), clock.instant()));

        ReleasePack pinned = releasePacks.save(
                releasePacks.save(ReleasePack.create("Pinned Release", "")).assignPromotionPath(path.id(), 1));
        releasePacks.save(
                releasePacks.save(ReleasePack.create("Other Release", "")).assignPromotionPath(otherPath.id(), 1));
        releasePacks.save(ReleasePack.create("Unassigned Release", ""));

        List<ReleasePack> found = releasePacks.findAllReferencingPromotionPath(path.id());

        assertThat(found).extracting(ReleasePack::id).containsExactly(pinned.id());
    }

    @Test
    void deleteByIdRemovesThePackAndItsContentsHandoverAndIterations() {
        ReleasePack pack = releasePacks.save(
                releasePacks.save(ReleasePack.create("Release", "")).addApplicationVersion(payments.id(), paymentsV1.id()));
        releasePacks.save(pack.updateHandover(new Handover("a", "b", "c", "d", "e", "f")));
        releasePacks.save(releasePacks.findById(pack.id()).orElseThrow()
                .startIteration("Iteration", Instant.parse("2026-07-27T09:00:00Z"), ""));

        releasePacks.deleteById(pack.id());

        assertThat(releasePacks.findById(pack.id())).isEmpty();
        // The Application and its Version must not have been cascaded away (release_pack_version
        // only owns the join row, never the Application or Application Version it references).
        assertThat(applications.findById(payments.id())).isPresent();
        assertThat(applicationVersions.findById(paymentsV1.id())).isPresent();
    }
}
