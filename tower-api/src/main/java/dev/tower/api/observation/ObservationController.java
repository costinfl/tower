package dev.tower.api.observation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
import dev.tower.domain.observation.StateComparison;
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

    /**
     * Current state, or the state at a past instant when {@code at} is given
     * (Milestone 4, ADR-017).
     *
     * <p>One endpoint rather than two, because a Snapshot is not a different
     * kind of thing from current state — it is the same derivation with an upper
     * bound, and current state is the case where the bound is now.
     */
    @GetMapping("/api/environments/{id}/state")
    public EnvironmentStateView state(
            @PathVariable("id") String id,
            @RequestParam(name = "at", required = false) Instant at) {
        EnvironmentId environmentId = EnvironmentId.of(id);
        Environment environment = environmentUseCases.get(environmentId);
        EnvironmentState state = observationUseCases.environmentStateAt(environmentId, at);

        Function<ApplicationId, Application> applications = applicationResolver();
        Function<ApplicationVersionId, ApplicationVersion> versions = versionResolver();
        List<DeployedApplicationView> deployed = state.deployed().stream()
                .map(d -> DeployedApplicationView.from(
                        d, applications.apply(d.applicationId()), versions.apply(d.applicationVersionId())))
                .toList();

        return EnvironmentStateView.from(environment, state, deployed);
    }

    /**
     * The difference between two derived states (Milestone 4, ADR-017).
     *
     * <p>Both sides are an Environment and an instant, either of which may be
     * omitted, so one endpoint answers "how did UAT change between Monday and
     * Friday" and "how does UAT differ from Production now". Nothing is stored:
     * both sides are folded from the Observation stream on request.
     */
    @GetMapping("/api/environments/{id}/state/comparison")
    public StateComparisonView compare(
            @PathVariable("id") String id,
            @RequestParam(name = "at", required = false) Instant at,
            @RequestParam(name = "against", required = false) String against,
            @RequestParam(name = "againstAt", required = false) Instant againstAt) {

        EnvironmentId left = EnvironmentId.of(id);
        // Comparing an Environment with itself at two instants is the common
        // case, so the other side defaults to the same Environment.
        EnvironmentId right = against == null || against.isBlank() ? left : EnvironmentId.of(against);

        environmentUseCases.get(left);
        if (!right.equals(left)) {
            environmentUseCases.get(right);
        }

        StateComparison comparison = observationUseCases.compareStates(left, at, right, againstAt);

        Function<ApplicationId, Application> applications = applicationResolver();
        Function<ApplicationVersionId, ApplicationVersion> versions = versionResolver();

        return new StateComparisonView(
                comparison.isIdentical(),
                comparison.differences().stream()
                        .map(d -> DifferenceView.from(d, applications, versions)).toList(),
                comparison.unchanged().stream()
                        .map(u -> UnchangedView.from(u, applications, versions)).toList());
    }

    /**
     * @param identical whether the two sides agree, stated rather than left to be
     *                  inferred from an empty list — an empty differences list and
     *                  a failed comparison would otherwise look the same
     */
    public record StateComparisonView(
            boolean identical, List<DifferenceView> differences, List<UnchangedView> unchanged) {
    }

    /**
     * @param kind CHANGED, ARRIVED or GONE — named rather than left for a client
     *             to work out from which version is null, because that inference
     *             is exactly where "not deployed" gets confused with "never observed"
     */
    public record DifferenceView(
            String applicationId, String applicationName, String kind,
            String leftVersion, String rightVersion,
            String leftObservationId, String rightObservationId) {

        static DifferenceView from(StateComparison.Difference difference,
                                   Function<ApplicationId, Application> applications,
                                   Function<ApplicationVersionId, ApplicationVersion> versions) {
            Application application = applications.apply(difference.applicationId());
            return new DifferenceView(
                    difference.applicationId().toString(),
                    application == null ? null : application.name(),
                    difference.changed() ? "CHANGED" : difference.onlyOnRight() ? "ARRIVED" : "GONE",
                    versionLabel(difference.leftVersion(), versions),
                    versionLabel(difference.rightVersion(), versions),
                    difference.leftObservationId() == null ? null : difference.leftObservationId().toString(),
                    difference.rightObservationId() == null ? null : difference.rightObservationId().toString());
        }
    }

    public record UnchangedView(
            String applicationId, String applicationName, String version,
            String leftObservationId, String rightObservationId) {

        static UnchangedView from(StateComparison.Unchanged unchanged,
                                  Function<ApplicationId, Application> applications,
                                  Function<ApplicationVersionId, ApplicationVersion> versions) {
            Application application = applications.apply(unchanged.applicationId());
            return new UnchangedView(
                    unchanged.applicationId().toString(),
                    application == null ? null : application.name(),
                    versionLabel(unchanged.applicationVersionId(), versions),
                    unchanged.leftObservationId().toString(),
                    unchanged.rightObservationId().toString());
        }
    }

    private static String versionLabel(
            ApplicationVersionId id, Function<ApplicationVersionId, ApplicationVersion> versions) {
        if (id == null) {
            return null;
        }
        ApplicationVersion version = versions.apply(id);
        return version == null ? id.toString() : version.version();
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
