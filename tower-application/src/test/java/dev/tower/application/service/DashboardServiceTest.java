package dev.tower.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.tower.application.port.in.DashboardUseCases.Overview;
import dev.tower.application.port.out.ApplicationVersionRepository;
import dev.tower.application.port.out.EnvironmentRepository;
import dev.tower.application.port.out.ObservationRepository;
import dev.tower.application.port.out.PromotionPathRepository;
import dev.tower.application.port.out.ReleasePackRepository;
import dev.tower.domain.application.Application;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.convergence.EnvironmentConvergence;
import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.environment.Stage;
import dev.tower.domain.observation.Observation;
import dev.tower.domain.observation.ObservationId;
import dev.tower.domain.observation.ObservationSource;
import dev.tower.domain.promotionpath.PromotionPath;
import dev.tower.domain.promotionpath.PromotionPathId;
import dev.tower.domain.releasepack.ReleasePack;
import dev.tower.domain.releasepack.ReleasePackId;
import dev.tower.domain.releasepack.ReleasePackState;

/**
 * Milestone 5, issue #7: one view across every active release.
 *
 * <p>The dashboard derives nothing of its own — it asks the same use cases the
 * individual pages ask — so what is under test is which packs it counts as
 * heading for an Environment, and whether its counts describe what Tower was
 * told rather than what is running.
 */
@DisplayName("The operational dashboard")
class DashboardServiceTest {

    private static final Instant MON = Instant.parse("2026-08-03T09:00:00Z");
    private static final Instant TUE = Instant.parse("2026-08-04T09:00:00Z");

    private InMemoryEnvironments environments;
    private InMemoryPacks packs;
    private InMemoryPaths paths;
    private InMemoryObservations observations;
    private InMemoryVersions versions;
    private DashboardService dashboard;

    private Environment sit;
    private Environment uat;
    private Environment production;
    private Application customer;
    private ApplicationVersion customer250;
    private ApplicationVersion customer251;

    @BeforeEach
    void setUp() {
        environments = new InMemoryEnvironments();
        packs = new InMemoryPacks();
        paths = new InMemoryPaths();
        observations = new InMemoryObservations();
        versions = new InMemoryVersions();

        ObservationService observationService = new ObservationService(
                observations, environments, versions, packs,
                () -> ObservationSource.manual("costin"),
                Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC));
        dashboard = new DashboardService(environments, packs, paths, observationService);

        sit = environments.save(Environment.create("SIT", Stage.VALIDATION));
        uat = environments.save(Environment.create("UAT", Stage.PRE_PRODUCTION));
        production = environments.save(Environment.create("Production", Stage.PRODUCTION));

        customer = Application.create("Customer API", "");
        customer250 = versions.save(
                ApplicationVersion.create(customer.id(), "2.5.0", null, null, null, null));
        customer251 = versions.save(
                ApplicationVersion.create(customer.id(), "2.5.1", null, null, null, null));
    }

    private PromotionPath pathThrough(String name, Environment... route) {
        return paths.save(PromotionPath.create(
                name, List.of(route).stream().map(Environment::id).toList(), MON));
    }

    private ReleasePack packOn(String name, PromotionPath path, ApplicationVersion... contents) {
        ReleasePack pack = ReleasePack.create(name, "");
        if (path != null) {
            pack = pack.assignPromotionPath(path.id(), path.currentVersion().number());
        }
        for (ApplicationVersion version : contents) {
            pack = pack.addApplicationVersion(version.applicationId(), version.id());
        }
        return packs.save(pack);
    }

    private void seen(Environment environment, ApplicationVersion version, Instant at) {
        observations.append(Observation.record(environment.id(), version.applicationId(),
                version.id(), at, ObservationSource.manual("costin")));
    }

    /** Convenience: the Environment row for a given name. */
    private static dev.tower.application.port.in.DashboardUseCases.EnvironmentSummary rowFor(
            Overview overview, String name) {
        return overview.environments().stream()
                .filter(e -> e.name().equals(name)).findFirst().orElseThrow();
    }

    @Nested
    @DisplayName("shows which releases are heading for one Environment")
    class Convergence {

        @Test
        void counts_a_pack_as_heading_for_every_environment_its_pinned_path_names() {
            PromotionPath regular = pathThrough("Regular", sit, uat, production);
            packOn("Release A", regular, customer250);

            Overview overview = dashboard.overview();

            assertThat(rowFor(overview, "UAT").convergence().packs())
                    .extracting(EnvironmentConvergence.ConvergingPack::name)
                    .containsExactly("Release A");
            assertThat(rowFor(overview, "SIT").convergence().packs()).hasSize(1);
        }

        @Test
        void a_pack_is_not_heading_for_an_environment_its_path_does_not_name() {
            // The Hotfix path skips UAT entirely.
            PromotionPath hotfix = pathThrough("Hotfix", sit, production);
            packOn("Hotfix pack", hotfix, customer251);

            assertThat(rowFor(dashboard.overview(), "UAT").convergence().packs()).isEmpty();
        }

        @Test
        void reports_contention_when_two_releases_head_for_the_same_environment() {
            // The question the dashboard exists to answer.
            PromotionPath regular = pathThrough("Regular", sit, uat, production);
            packOn("Release A", regular, customer250);
            packOn("Release B", regular, customer251);

            Overview overview = dashboard.overview();

            assertThat(rowFor(overview, "UAT").convergence().isContested()).isTrue();
            assertThat(overview.summary().contestedEnvironments()).isEqualTo(3);
        }

        @Test
        void an_archived_release_is_not_competing_for_anything() {
            // Archiving is the team saying it stopped working the release
            // (ADR-008). It is Intent, and it is the one thing that removes a
            // pack from this view.
            PromotionPath regular = pathThrough("Regular", sit, uat, production);
            packOn("Release A", regular, customer250);
            packs.save(packOn("Abandoned", regular, customer251).archive());

            Overview overview = dashboard.overview();

            assertThat(rowFor(overview, "UAT").convergence().packs())
                    .extracting(EnvironmentConvergence.ConvergingPack::name)
                    .containsExactly("Release A");
            assertThat(overview.summary().activeReleasePacks()).isEqualTo(1);
            assertThat(overview.summary().archivedReleasePacks()).isEqualTo(1);
        }

        @Test
        void a_pack_with_no_promotion_path_heads_for_nowhere_rather_than_everywhere() {
            packOn("Unassigned", null, customer250);

            Overview overview = dashboard.overview();

            assertThat(overview.environments())
                    .allSatisfy(row -> assertThat(row.convergence().packs()).isEmpty());
            // It is still an active release and still listed as one.
            assertThat(overview.releasePacks()).extracting("name").containsExactly("Unassigned");
        }

        @Test
        void follows_the_pinned_path_version_rather_than_the_current_one() {
            // ADR-007. The pack was assigned version 1, which went through UAT;
            // version 2 drops UAT. The pack is still heading for UAT.
            PromotionPath regular = pathThrough("Regular", sit, uat, production);
            packOn("Release A", regular, customer250);
            paths.save(regular.withNewVersion(List.of(sit.id(), production.id()), TUE));

            assertThat(rowFor(dashboard.overview(), "UAT").convergence().packs()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("counts what it was told")
    class Counts {

        @Test
        void a_release_nobody_has_reported_is_counted_as_silence_not_as_failure() {
            PromotionPath regular = pathThrough("Regular", sit, uat, production);
            packOn("Release A", regular, customer250);

            assertThat(dashboard.overview().summary().packsNotObservedAnywhere()).isEqualTo(1);
        }

        @Test
        void an_environment_nobody_has_reported_on_is_named_as_such() {
            assertThat(dashboard.overview().summary().environmentsNeverObserved()).isEqualTo(3);

            seen(uat, customer250, MON);

            Overview overview = dashboard.overview();
            assertThat(overview.summary().environmentsNeverObserved()).isEqualTo(2);
            assertThat(rowFor(overview, "UAT").hasBeenObserved()).isTrue();
            assertThat(rowFor(overview, "UAT").deployedCount()).isEqualTo(1);
            assertThat(rowFor(overview, "UAT").lastObservedAt()).isEqualTo(MON);
        }

        @Test
        void reports_the_furthest_environment_a_release_reached_by_stage() {
            PromotionPath regular = pathThrough("Regular", sit, uat, production);
            ReleasePack pack = packOn("Release A", regular, customer250);
            seen(sit, customer250, MON);
            seen(production, customer250, TUE);

            assertThat(dashboard.overview().releasePacks()).singleElement().satisfies(summary -> {
                assertThat(summary.releasePackId()).isEqualTo(pack.id());
                assertThat(summary.furthestEnvironments()).containsExactly("Production");
                assertThat(summary.environmentsReached()).isEqualTo(2);
                assertThat(summary.state()).isEqualTo(ReleasePackState.PRODUCTION);
                assertThat(summary.promotionPath()).isEqualTo("Regular");
            });
        }

        @Test
        void names_every_environment_at_the_highest_stage_rather_than_picking_one() {
            // ADR-008 derives state from Stage, and Environments share Stages. A
            // release fully in one Validation Environment and partly in another
            // has got equally far in both, and choosing between them would
            // answer a question the data does not answer.
            Environment sit2 = environments.save(Environment.create("SIT2", Stage.VALIDATION));
            Application orders = Application.create("Orders API", "");
            ApplicationVersion orders190 = versions.save(
                    ApplicationVersion.create(orders.id(), "1.9.0", null, null, null, null));
            PromotionPath wide = pathThrough("Wide", sit, sit2, production);
            packOn("Release A", wide, customer250, orders190);
            seen(sit, customer250, MON);
            seen(sit2, orders190, TUE);

            assertThat(dashboard.overview().releasePacks()).singleElement()
                    .extracting("furthestEnvironments")
                    .isEqualTo(List.of("SIT", "SIT2"));
        }

        @Test
        void a_release_observed_nowhere_names_no_furthest_environment() {
            PromotionPath regular = pathThrough("Regular", sit, uat, production);
            packOn("Release A", regular, customer250);

            assertThat(dashboard.overview().releasePacks()).singleElement().satisfies(summary -> {
                assertThat(summary.furthestEnvironments()).isEmpty();
                assertThat(summary.lastObservedAt()).isNull();
                assertThat(summary.environmentsReached()).isZero();
            });
        }

        @Test
        void lists_releases_by_name_rather_than_by_how_far_along_they_are() {
            PromotionPath regular = pathThrough("Regular", sit, uat, production);
            packOn("Zebra", regular, customer250);
            packOn("Alpha", regular, customer251);
            seen(production, customer250, MON);

            assertThat(dashboard.overview().releasePacks())
                    .extracting("name").containsExactly("Alpha", "Zebra");
        }
    }

    // --- fakes ---------------------------------------------------------------

    private static final class InMemoryObservations implements ObservationRepository {
        private final List<Observation> stored = new ArrayList<>();

        @Override
        public Observation append(Observation observation) {
            stored.add(observation);
            return observation;
        }

        @Override
        public Optional<Observation> findById(ObservationId id) {
            return stored.stream().filter(o -> o.id().equals(id)).findFirst();
        }

        @Override
        public List<Observation> findAllInEnvironment(EnvironmentId environmentId) {
            return stored.stream().filter(o -> o.environmentId().equals(environmentId)).toList();
        }

        @Override
        public List<Observation> findAllOfVersions(Collection<ApplicationVersionId> versionIds) {
            return stored.stream().filter(o -> versionIds.contains(o.applicationVersionId())).toList();
        }

        @Override
        public List<Observation> findAll() {
            return List.copyOf(stored);
        }
    }

    private static final class InMemoryEnvironments implements EnvironmentRepository {
        private final List<Environment> stored = new ArrayList<>();

        @Override
        public Environment save(Environment environment) {
            stored.removeIf(e -> e.id().equals(environment.id()));
            stored.add(environment);
            return environment;
        }

        @Override
        public Optional<Environment> findById(EnvironmentId id) {
            return stored.stream().filter(e -> e.id().equals(id)).findFirst();
        }

        @Override
        public List<Environment> findAll() {
            return List.copyOf(stored);
        }

        @Override
        public List<Environment> findAllById(List<EnvironmentId> ids) {
            return stored.stream().filter(e -> ids.contains(e.id())).toList();
        }

        @Override
        public boolean existsByNameIgnoringCase(String name) {
            return stored.stream().anyMatch(e -> e.name().equalsIgnoreCase(name));
        }

        @Override
        public void deleteById(EnvironmentId id) {
            stored.removeIf(e -> e.id().equals(id));
        }
    }

    private static final class InMemoryVersions implements ApplicationVersionRepository {
        private final List<ApplicationVersion> stored = new ArrayList<>();

        @Override
        public ApplicationVersion save(ApplicationVersion version) {
            stored.removeIf(v -> v.id().equals(version.id()));
            stored.add(version);
            return version;
        }

        @Override
        public Optional<ApplicationVersion> findById(ApplicationVersionId id) {
            return stored.stream().filter(v -> v.id().equals(id)).findFirst();
        }

        @Override
        public List<ApplicationVersion> findAll() {
            return List.copyOf(stored);
        }

        @Override
        public List<ApplicationVersion> findAllByApplication(ApplicationId applicationId) {
            return stored.stream().filter(v -> v.applicationId().equals(applicationId)).toList();
        }

        @Override
        public List<ApplicationVersion> findAllById(List<ApplicationVersionId> ids) {
            return stored.stream().filter(v -> ids.contains(v.id())).toList();
        }

        @Override
        public boolean existsByApplicationAndVersion(ApplicationId applicationId, String version) {
            return findByApplicationAndVersion(applicationId, version).isPresent();
        }

        @Override
        public Optional<ApplicationVersion> findByApplicationAndVersion(
                ApplicationId applicationId, String version) {
            return stored.stream()
                    .filter(v -> v.applicationId().equals(applicationId) && v.version().equals(version))
                    .findFirst();
        }

        @Override
        public void deleteById(ApplicationVersionId id) {
            stored.removeIf(v -> v.id().equals(id));
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
            return stored.stream().anyMatch(p -> p.name().equalsIgnoreCase(name));
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

    private static final class InMemoryPaths implements PromotionPathRepository {
        private final List<PromotionPath> stored = new ArrayList<>();

        @Override
        public PromotionPath save(PromotionPath path) {
            stored.removeIf(p -> p.id().equals(path.id()));
            stored.add(path);
            return path;
        }

        @Override
        public Optional<PromotionPath> findById(PromotionPathId id) {
            return stored.stream().filter(p -> p.id().equals(id)).findFirst();
        }

        @Override
        public List<PromotionPath> findAll() {
            return List.copyOf(stored);
        }

        @Override
        public boolean existsByNameIgnoringCase(String name) {
            return stored.stream().anyMatch(p -> p.name().equalsIgnoreCase(name));
        }

        @Override
        public List<PromotionPath> findAllReferencing(EnvironmentId environment) {
            return stored.stream().filter(p -> p.referencesEnvironment(environment)).toList();
        }

        @Override
        public void deleteById(PromotionPathId id) {
            stored.removeIf(p -> p.id().equals(id));
        }
    }
}
