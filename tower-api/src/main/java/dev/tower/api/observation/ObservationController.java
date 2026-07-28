package dev.tower.api.observation;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.tower.application.port.in.ApplicationUseCases;
import dev.tower.application.port.in.EnvironmentUseCases;
import dev.tower.application.port.in.ObservationUseCases;
import dev.tower.application.port.in.ObservationUseCases.PackSighting;
import dev.tower.application.port.in.ObservationUseCases.RecordManualObservation;
import dev.tower.domain.application.Application;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.observation.EnvironmentState;
import dev.tower.domain.observation.Observation;
import dev.tower.domain.releasepack.ReleasePackId;

/**
 * REST API for recording and reading Observations (issues #21 to #24, #23, #25).
 *
 * <p>Endpoints span three resources - Observations themselves, Environment state and Release Pack
 * state - because all three are read directly off the same append-only fact log
 * (ADR-002, ObservationUseCases). Grouping them here keeps the derivation and the view resolution
 * that FR-012, FR-013 and NFR-013 require (timestamp and source on every displayed value) in one
 * place rather than duplicated across ReleasePackController and EnvironmentController.
 */
@RestController
public class ObservationController {

    private final ObservationUseCases observationUseCases;
    private final EnvironmentUseCases environmentUseCases;
    private final ApplicationUseCases applicationUseCases;

    public ObservationController(ObservationUseCases observationUseCases, EnvironmentUseCases environmentUseCases,
                                 ApplicationUseCases applicationUseCases) {
        this.observationUseCases = observationUseCases;
        this.environmentUseCases = environmentUseCases;
        this.applicationUseCases = applicationUseCases;
    }

    @PostMapping("/api/observations")
    @ResponseStatus(HttpStatus.CREATED)
    public ObservationView record(@Valid @RequestBody ObservationRequest request) {
        Observation observation = observationUseCases.recordManual(new RecordManualObservation(
                EnvironmentId.of(request.environmentId()), ApplicationVersionId.of(request.applicationVersionId()),
                request.observedAt()));
        return toView(observation);
    }

    @GetMapping("/api/environments/{id}/state")
    public EnvironmentStateView state(@PathVariable("id") String id) {
        EnvironmentId environmentId = EnvironmentId.of(id);
        Environment environment = environmentUseCases.get(environmentId);
        EnvironmentState state = observationUseCases.environmentState(environmentId);

        Function<ApplicationId, Application> applications = applicationResolver();
        Function<ApplicationVersionId, ApplicationVersion> versions = versionResolver();
        List<DeployedApplicationView> deployed = state.deployed().stream()
                .map(d -> DeployedApplicationView.from(
                        d, applications.apply(d.applicationId()), versions.apply(d.applicationVersionId())))
                .toList();

        return EnvironmentStateView.from(environment, state, deployed);
    }

    @GetMapping("/api/environments/{id}/observations")
    public List<ObservationView> history(@PathVariable("id") String id) {
        // ObservationUseCases.historyOf already returns newest first.
        return toViews(observationUseCases.historyOf(EnvironmentId.of(id)));
    }

    @GetMapping("/api/release-packs/{id}/state")
    public ReleasePackStateView packState(@PathVariable("id") String id) {
        ReleasePackId packId = ReleasePackId.of(id);
        String state = observationUseCases.stateOf(packId).name();
        List<PackSighting> sightings = observationUseCases.sightingsOf(packId);

        Function<EnvironmentId, Environment> environments = environmentResolver();
        Function<ApplicationVersionId, ApplicationVersion> versions = versionResolver();
        Function<ApplicationId, Application> applications = applicationResolver();
        List<PackSightingView> sightingViews = sightings.stream()
                .map(sighting -> {
                    ApplicationVersion version = versions.apply(sighting.applicationVersionId());
                    return PackSightingView.from(sighting, environments.apply(sighting.environmentId()),
                            applications.apply(version.applicationId()), version);
                })
                .toList();

        return new ReleasePackStateView(state, sightingViews);
    }

    private ObservationView toView(Observation observation) {
        Environment environment = environmentUseCases.get(observation.environmentId());
        Application application = applicationUseCases.get(observation.applicationId());
        ApplicationVersion version = applicationUseCases.getVersion(observation.applicationVersionId());
        return ObservationView.from(observation, environment, application, version);
    }

    private List<ObservationView> toViews(List<Observation> observations) {
        // Resolvers are built once per request rather than once per Observation, since a single
        // Environment's history typically draws from a small pool of Applications and Versions.
        Function<EnvironmentId, Environment> environments = environmentResolver();
        Function<ApplicationId, Application> applications = applicationResolver();
        Function<ApplicationVersionId, ApplicationVersion> versions = versionResolver();
        return observations.stream()
                .map(o -> ObservationView.from(o, environments.apply(o.environmentId()),
                        applications.apply(o.applicationId()), versions.apply(o.applicationVersionId())))
                .toList();
    }

    private Function<EnvironmentId, Environment> environmentResolver() {
        Map<EnvironmentId, Environment> byId = environmentUseCases.list().stream()
                .collect(Collectors.toMap(Environment::id, Function.identity()));
        return byId::get;
    }

    private Function<ApplicationId, Application> applicationResolver() {
        Map<ApplicationId, Application> byId = applicationUseCases.list().stream()
                .collect(Collectors.toMap(Application::id, Function.identity()));
        return byId::get;
    }

    private Function<ApplicationVersionId, ApplicationVersion> versionResolver() {
        Map<ApplicationVersionId, ApplicationVersion> byId = applicationUseCases.listVersions().stream()
                .collect(Collectors.toMap(ApplicationVersion::id, Function.identity()));
        return byId::get;
    }
}
