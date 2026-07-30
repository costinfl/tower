package dev.tower.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.tower.application.port.out.ApplicationVersionRepository;
import dev.tower.application.port.out.EnvironmentRepository;
import dev.tower.application.port.out.ObservationRepository;
import dev.tower.application.port.out.ReleasePackRepository;
import dev.tower.domain.application.Application;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.environment.Stage;
import dev.tower.domain.observation.Observation;
import dev.tower.domain.observation.ObservationId;
import dev.tower.domain.observation.ObservationSource;
import dev.tower.domain.promotionpath.PromotionPathId;
import dev.tower.domain.releasepack.ReleasePack;
import dev.tower.domain.releasepack.ReleasePackId;

/**
 * Milestone 4 (ADR-017) at the service level: the derivations themselves are
 * covered by the domain tests, so what is under test here is the wiring the
 * domain cannot see — which Observations the service hands to the fold, and
 * what it does with facts it can no longer resolve.
 */
@DisplayName("Reading history through the Observation service")
class ObservationHistoryTest {

    private static final Instant MARCH = Instant.parse("2026-03-15T10:00:00Z");
    private static final Instant APRIL = Instant.parse("2026-04-20T10:00:00Z");
    private static final Instant MAY = Instant.parse("2026-05-05T10:00:00Z");

    private InMemoryObservations observations;
    private InMemoryEnvironments environments;
    private InMemoryVersions versions;
    private InMemoryPacks packs;
    private ObservationService service;

    private Environment sit;
    private Environment uat;
    private Application customer;
    private Application orders;
    private ApplicationVersion customer240;
    private ApplicationVersion customer250;
    private ApplicationVersion orders190;

    @BeforeEach
    void setUp() {
        observations = new InMemoryObservations();
        environments = new InMemoryEnvironments();
        versions = new InMemoryVersions();
        packs = new InMemoryPacks();
        service = new ObservationService(observations, environments, versions, packs,
                () -> ObservationSource.manual("costin"),
                Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC));

        sit = environments.save(Environment.create("SIT", Stage.VALIDATION));
        uat = environments.save(Environment.create("UAT", Stage.PRE_PRODUCTION));
        customer = Application.create("Customer API", "");
        orders = Application.create("Orders API", "");
        customer240 = versions.save(
                ApplicationVersion.create(customer.id(), "2.4.0", null, null, null, null));
        customer250 = versions.save(
                ApplicationVersion.create(customer.id(), "2.5.0", null, null, null, null));
        orders190 = versions.save(
                ApplicationVersion.create(orders.id(), "1.9.0", null, null, null, null));
    }

    private void seen(Environment environment, ApplicationVersion version, Instant at) {
        observations.append(Observation.record(environment.id(), version.applicationId(), version.id(),
                at, ObservationSource.manual("costin")));
    }

    private ReleasePack packOf(ApplicationVersion... contents) {
        ReleasePack pack = ReleasePack.create("Release 2026.08", "");
        for (ApplicationVersion version : contents) {
            pack = pack.addApplicationVersion(version.applicationId(), version.id());
        }
        return packs.save(pack);
    }

    @Test
    void reads_state_at_an_instant_from_the_same_stream_as_current_state() {
        seen(sit, customer240, MARCH);
        seen(sit, customer250, MAY);

        assertThat(service.environmentStateAt(sit.id(), APRIL).deploymentOf(customer.id()))
                .get().extracting("applicationVersionId").isEqualTo(customer240.id());
        assertThat(service.environmentState(sit.id()).deploymentOf(customer.id()))
                .get().extracting("applicationVersionId").isEqualTo(customer250.id());
    }

    @Test
    void compares_two_environments_at_one_instant() {
        seen(sit, customer250, MARCH);
        seen(uat, customer240, MARCH);

        assertThat(service.compareStates(sit.id(), MAY, uat.id(), MAY).differences())
                .singleElement()
                .satisfies(difference -> {
                    assertThat(difference.leftVersion()).isEqualTo(customer250.id());
                    assertThat(difference.rightVersion()).isEqualTo(customer240.id());
                });
    }

    @Test
    void asking_about_an_environment_that_does_not_exist_says_so() {
        EnvironmentId unknown = EnvironmentId.newId();

        assertThatThrownBy(() -> service.environmentStateAt(unknown, MARCH))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void progression_reports_when_each_of_the_pack_reached_an_environment() {
        ReleasePack pack = packOf(customer250, orders190);
        seen(sit, customer250, MARCH);
        seen(sit, orders190, MAY);

        assertThat(service.progressionOf(pack.id()).in(sit.id())).get().satisfies(arrival -> {
            assertThat(arrival.firstObservedAt()).isEqualTo(MARCH);
            assertThat(arrival.completeAt()).isEqualTo(MAY);
        });
    }

    @Test
    void progression_ignores_observations_of_versions_the_pack_does_not_contain() {
        // The service asks the repository for the pack's versions; this proves it
        // does not simply hand the whole stream to the fold.
        ReleasePack pack = packOf(customer250);
        seen(sit, orders190, MARCH);

        assertThat(service.progressionOf(pack.id()).hasBeenObserved()).isFalse();
    }

    @Test
    void progression_drops_an_observation_whose_environment_has_been_deleted() {
        // An arrival Tower cannot name is worse than one it does not show: the
        // reader would see a row identified only by an id that resolves to
        // nothing.
        ReleasePack pack = packOf(customer250);
        seen(sit, customer250, MARCH);
        seen(uat, customer250, MAY);
        environments.deleteById(uat.id());

        assertThat(service.progressionOf(pack.id()).arrivals())
                .extracting("environmentId")
                .containsExactly(sit.id());
    }

    @Test
    void an_empty_pack_has_no_progression_rather_than_arriving_everywhere() {
        ReleasePack pack = packOf();
        seen(sit, customer250, MARCH);

        assertThat(service.progressionOf(pack.id()).hasBeenObserved()).isFalse();
    }

    @Test
    void progression_of_a_pack_that_does_not_exist_says_so() {
        assertThatThrownBy(() -> service.progressionOf(ReleasePackId.newId()))
                .isInstanceOf(NotFoundException.class);
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
            return stored.stream()
                    .filter(o -> versionIds.contains(o.applicationVersionId()))
                    .toList();
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
}
