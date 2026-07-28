package dev.tower.portability;

import dev.tower.application.port.out.ApplicationRepository;
import dev.tower.application.port.out.ApplicationVersionRepository;
import dev.tower.application.port.out.EnvironmentRepository;
import dev.tower.application.port.out.ObservationRepository;
import dev.tower.application.port.out.PromotionPathRepository;
import dev.tower.application.port.out.ReleasePackRepository;
import dev.tower.domain.application.Application;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.observation.Observation;
import dev.tower.domain.observation.ObservationId;
import dev.tower.domain.promotionpath.PromotionPath;
import dev.tower.domain.promotionpath.PromotionPathId;
import dev.tower.domain.releasepack.ReleasePack;
import dev.tower.domain.releasepack.ReleasePackId;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory repositories for portability tests.
 *
 * <p>Export and import are about identity and provenance, not storage, so they
 * are tested against plain maps. A database here would slow the tests without
 * exercising anything the persistence module's own tests do not already cover.
 */
final class InMemoryRepositories {

    private final Map<EnvironmentId, Environment> environments = new LinkedHashMap<>();
    private final Map<ApplicationId, Application> applications = new LinkedHashMap<>();
    private final Map<ApplicationVersionId, ApplicationVersion> versions = new LinkedHashMap<>();
    private final Map<PromotionPathId, PromotionPath> paths = new LinkedHashMap<>();
    private final Map<ReleasePackId, ReleasePack> packs = new LinkedHashMap<>();
    private final Map<ObservationId, Observation> observations = new LinkedHashMap<>();

    private final EnvironmentRepository environmentRepository = new EnvironmentRepository() {
        public Environment save(Environment e) { environments.put(e.id(), e); return e; }
        public Optional<Environment> findById(EnvironmentId id) { return Optional.ofNullable(environments.get(id)); }
        public List<Environment> findAll() { return List.copyOf(environments.values()); }
        public List<Environment> findAllById(List<EnvironmentId> ids) {
            return ids.stream().map(environments::get).filter(java.util.Objects::nonNull).toList();
        }
        public boolean existsByNameIgnoringCase(String name) {
            return environments.values().stream().anyMatch(e -> e.name().equalsIgnoreCase(name));
        }
        public void deleteById(EnvironmentId id) { environments.remove(id); }
    };

    private final ApplicationRepository applicationRepository = new ApplicationRepository() {
        public Application save(Application a) { applications.put(a.id(), a); return a; }
        public Optional<Application> findById(ApplicationId id) { return Optional.ofNullable(applications.get(id)); }
        public List<Application> findAll() { return List.copyOf(applications.values()); }
        public boolean existsByNameIgnoringCase(String name) {
            return applications.values().stream().anyMatch(a -> a.name().equalsIgnoreCase(name));
        }
        public void deleteById(ApplicationId id) { applications.remove(id); }
    };

    private final ApplicationVersionRepository versionRepository = new ApplicationVersionRepository() {
        public ApplicationVersion save(ApplicationVersion v) { versions.put(v.id(), v); return v; }
        public Optional<ApplicationVersion> findById(ApplicationVersionId id) {
            return Optional.ofNullable(versions.get(id));
        }
        public List<ApplicationVersion> findAll() { return List.copyOf(versions.values()); }
        public List<ApplicationVersion> findAllByApplication(ApplicationId applicationId) {
            return versions.values().stream().filter(v -> v.applicationId().equals(applicationId)).toList();
        }
        public List<ApplicationVersion> findAllById(List<ApplicationVersionId> ids) {
            return ids.stream().map(versions::get).filter(java.util.Objects::nonNull).toList();
        }
        public boolean existsByApplicationAndVersion(ApplicationId applicationId, String version) {
            return versions.values().stream()
                    .anyMatch(v -> v.applicationId().equals(applicationId) && v.version().equals(version));
        }
        public void deleteById(ApplicationVersionId id) { versions.remove(id); }
    };

    private final PromotionPathRepository pathRepository = new PromotionPathRepository() {
        public PromotionPath save(PromotionPath p) { paths.put(p.id(), p); return p; }
        public Optional<PromotionPath> findById(PromotionPathId id) { return Optional.ofNullable(paths.get(id)); }
        public List<PromotionPath> findAll() { return List.copyOf(paths.values()); }
        public boolean existsByNameIgnoringCase(String name) {
            return paths.values().stream().anyMatch(p -> p.name().equalsIgnoreCase(name));
        }
        public List<PromotionPath> findAllReferencing(EnvironmentId environment) {
            return paths.values().stream().filter(p -> p.referencesEnvironment(environment)).toList();
        }
        public void deleteById(PromotionPathId id) { paths.remove(id); }
    };

    private final ReleasePackRepository packRepository = new ReleasePackRepository() {
        public ReleasePack save(ReleasePack p) { packs.put(p.id(), p); return p; }
        public Optional<ReleasePack> findById(ReleasePackId id) { return Optional.ofNullable(packs.get(id)); }
        public List<ReleasePack> findAll() { return List.copyOf(packs.values()); }
        public boolean existsByNameIgnoringCase(String name) {
            return packs.values().stream().anyMatch(p -> p.name().equalsIgnoreCase(name));
        }
        public List<ReleasePack> findAllContaining(ApplicationVersionId versionId) {
            return packs.values().stream().filter(p -> p.contains(versionId)).toList();
        }
        public List<ReleasePack> findAllReferencingPromotionPath(PromotionPathId pathId) {
            return packs.values().stream()
                    .filter(p -> p.promotionPath().map(a -> a.pathId().equals(pathId)).orElse(false))
                    .toList();
        }
        public void deleteById(ReleasePackId id) { packs.remove(id); }
    };

    private final ObservationRepository observationRepository = new ObservationRepository() {
        public Observation append(Observation o) { observations.put(o.id(), o); return o; }
        public Optional<Observation> findById(ObservationId id) { return Optional.ofNullable(observations.get(id)); }
        public List<Observation> findAllInEnvironment(EnvironmentId environmentId) {
            return observations.values().stream()
                    .filter(o -> o.environmentId().equals(environmentId)).toList();
        }
        public List<Observation> findAllOfVersions(Collection<ApplicationVersionId> versionIds) {
            List<Observation> found = new ArrayList<>();
            observations.values().stream()
                    .filter(o -> versionIds.contains(o.applicationVersionId()))
                    .forEach(found::add);
            return found;
        }
        public List<Observation> findAll() { return List.copyOf(observations.values()); }
    };

    EnvironmentRepository environments() { return environmentRepository; }

    ObservationRepository observations() { return observationRepository; }

    ImportService importService() {
        return new ImportService(environmentRepository, applicationRepository, versionRepository,
                pathRepository, packRepository, observationRepository);
    }

    ExportService exportService() {
        return new ExportService(environmentRepository, applicationRepository, versionRepository,
                pathRepository, packRepository, observationRepository,
                Clock.fixed(Instant.parse("2026-07-22T12:00:00Z"), ZoneOffset.UTC));
    }

}
