package dev.tower.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import dev.tower.application.port.in.DashboardUseCases;
import dev.tower.application.port.in.ObservationUseCases;
import dev.tower.application.port.out.ApplicationVersionRepository;
import dev.tower.application.port.out.EnvironmentRepository;
import dev.tower.application.port.out.ExternalBindingRepository;
import dev.tower.application.port.out.PromotionPathRepository;
import dev.tower.application.port.out.ReleasePackRepository;
import dev.tower.domain.convergence.EnvironmentConvergence;
import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.observation.EnvironmentState;
import dev.tower.domain.observation.ReleasePackProgression;
import dev.tower.domain.promotionpath.PromotionPath;
import dev.tower.domain.promotionpath.PromotionPathVersion;
import dev.tower.domain.releasepack.ReleasePack;
import dev.tower.domain.releasepack.ReleasePackState;

/**
 * The operational dashboard (Milestone 5, issue #7).
 *
 * <p>Composes rather than derives. Every number here comes from a derivation
 * that already exists — Environment state, Release Pack state (ADR-008), pack
 * progression (ADR-017) — reached through {@link ObservationUseCases} rather
 * than recomputed locally. A dashboard that disagreed with the page it
 * summarises would be worse than no dashboard, and the only way to guarantee it
 * cannot is to make it ask the same question.
 *
 * <p>The cost is repetition: each pack's progression re-reads its Observations.
 * For the workload ADR-009 describes — one developer, one instance, local-first
 * — that is the right trade, and it is the same one ADR-017 already made.
 *
 * <p>Carries no framework annotation; Spring wiring lives in tower-api.
 */
public class DashboardService implements DashboardUseCases {

    private final EnvironmentRepository environments;
    private final ReleasePackRepository releasePacks;
    private final PromotionPathRepository promotionPaths;
    private final ApplicationVersionRepository applicationVersions;
    private final ExternalBindingRepository bindings;
    private final ObservationUseCases observations;

    public DashboardService(EnvironmentRepository environments,
                            ReleasePackRepository releasePacks,
                            PromotionPathRepository promotionPaths,
                            ApplicationVersionRepository applicationVersions,
                            ExternalBindingRepository bindings,
                            ObservationUseCases observations) {
        this.environments = Objects.requireNonNull(environments);
        this.releasePacks = Objects.requireNonNull(releasePacks);
        this.promotionPaths = Objects.requireNonNull(promotionPaths);
        this.applicationVersions = Objects.requireNonNull(applicationVersions);
        this.bindings = Objects.requireNonNull(bindings);
        this.observations = Objects.requireNonNull(observations);
    }

    @Override
    public Overview overview() {
        // Archived packs are excluded throughout. Archiving is the team saying
        // it stopped working the release (ADR-008), and a release nobody is
        // working is not competing for anything.
        List<ReleasePack> active = releasePacks.findAll().stream()
                .filter(pack -> !pack.isArchived())
                .toList();
        long archived = releasePacks.findAll().stream().filter(ReleasePack::isArchived).count();

        Map<EnvironmentId, Environment> environmentsById = new LinkedHashMap<>();
        for (Environment environment : environments.findAll()) {
            environmentsById.put(environment.id(), environment);
        }

        Map<dev.tower.domain.promotionpath.PromotionPathId, PromotionPath> pathsById =
                new LinkedHashMap<>();
        for (PromotionPath path : promotionPaths.findAll()) {
            pathsById.put(path.id(), path);
        }

        // Derived once per pack and reused for every Environment it names,
        // rather than once per pack per Environment.
        Map<ReleasePack, ReleasePackProgression> progressions = new LinkedHashMap<>();
        for (ReleasePack pack : active) {
            progressions.put(pack, observations.progressionOf(pack.id()));
        }

        List<EnvironmentSummary> environmentSummaries = new ArrayList<>();
        int contested = 0;
        int neverObserved = 0;
        for (Environment environment : environmentsById.values()) {
            EnvironmentState state = observations.environmentState(environment.id());

            List<EnvironmentConvergence.PackProgression> converging = new ArrayList<>();
            for (Map.Entry<ReleasePack, ReleasePackProgression> entry : progressions.entrySet()) {
                if (namesEnvironment(entry.getKey(), environment.id(), pathsById)) {
                    converging.add(new EnvironmentConvergence.PackProgression(
                            entry.getKey().id(), entry.getKey().name(),
                            entry.getKey().contents().size(), entry.getValue()));
                }
            }

            EnvironmentConvergence convergence =
                    EnvironmentConvergence.from(environment.id(), converging);
            if (convergence.isContested()) {
                contested++;
            }
            if (!state.hasBeenObserved()) {
                neverObserved++;
            }

            environmentSummaries.add(new EnvironmentSummary(
                    environment.id(), environment.name(), environment.stage(),
                    state.hasBeenObserved(), state.lastObservedAt().orElse(null),
                    state.deployed().size(), convergence));
        }

        List<ReleasePackSummary> packSummaries = new ArrayList<>();
        int unobserved = 0;
        for (Map.Entry<ReleasePack, ReleasePackProgression> entry : progressions.entrySet()) {
            ReleasePack pack = entry.getKey();
            ReleasePackProgression progression = entry.getValue();
            if (!progression.hasBeenObserved()) {
                unobserved++;
            }
            packSummaries.add(summarise(pack, progression, environmentsById, pathsById));
        }

        // Environments in the order they were registered; packs by name, for the
        // same reason convergence is ordered by name — no arrangement here is a
        // recommendation.
        packSummaries.sort(Comparator.comparing(
                ReleasePackSummary::name, String.CASE_INSENSITIVE_ORDER));

        return new Overview(environmentSummaries, packSummaries,
                new Summary(active.size(), (int) archived, contested, unobserved, neverObserved),
                setupState());
    }

    /**
     * Where this Tower stands against the order things have to be defined in.
     *
     * <p>The order is the one the model forces rather than one somebody chose: a
     * Promotion Path is a sequence of Environments, so Environments come first; a
     * Release Pack holds Application Versions, so those come before it. Nothing
     * here is a preference, which is why it is safe to teach.
     *
     * <p>Read from the repositories directly rather than through the use cases
     * above, unlike every other number on this page. What is being asked is "does
     * any of these exist", and routing that through a derivation would make the
     * cheapest question on the dashboard the most expensive.
     */
    private SetupState setupState() {
        List<SetupStep> steps = List.of(
                new SetupStep("environments", "Define your Environments",
                        "The places software runs — Dev, SIT, UAT, Production. Everything else"
                                + " refers to them, so they come first.",
                        !environments.findAll().isEmpty(), false),
                new SetupStep("paths", "Arrange them into a Promotion Path",
                        "The route a release takes through those Environments. A release pins the"
                                + " version of the path it follows, so the path can change later"
                                + " without rewriting history.",
                        !promotionPaths.findAll().isEmpty(), false),
                new SetupStep("applications", "Register an Application and a version of it",
                        "What gets deployed. A version is immutable once registered, so Tower can"
                                + " point at it forever.",
                        !applicationVersions.findAll().isEmpty(), false),
                new SetupStep("releasePacks", "Create a Release Pack and put versions in it",
                        "What a team actually ships: the versions that travel together, and the"
                                + " handover that goes with them.",
                        releasePacks.findAll().stream().anyMatch(pack -> !pack.contents().isEmpty()),
                        false),
                // Optional, and stated as such rather than politely implied.
                // ADR-006 admits a person saying what is deployed as a real
                // Observation, so a Tower nobody has connected to anything is a
                // supported way to run rather than a half-finished one.
                new SetupStep("connectors", "Connect a system, so Tower reads deployments for you",
                        "Optional. Without this you record what is deployed yourself, and Tower"
                                + " treats what you say as a fact like any other. With it, Tower"
                                + " reads Kubernetes, git, a CI system or an artifact repository"
                                + " and never writes to any of them.",
                        anyBindingExists(), true));

        boolean complete = steps.stream().filter(step -> !step.optional()).allMatch(SetupStep::done);
        return new SetupState(steps, complete);
    }

    /**
     * Whether anything at all has been bound.
     *
     * <p>Any one binding counts. Asking for a particular kind would be Tower
     * having an opinion about which Connector a team ought to use, and it has
     * none.
     */
    private boolean anyBindingExists() {
        return !bindings.findAllEnvironmentBindings().isEmpty()
                || !bindings.findAllApplicationBindings().isEmpty()
                || !bindings.findAllRepositoryBindings().isEmpty()
                || !bindings.findAllIssueTrackerBindings().isEmpty()
                || !bindings.findAllPipelineJobBindings().isEmpty()
                || !bindings.findAllBuildJobBindings().isEmpty()
                || !bindings.findAllArtifactCoordinateBindings().isEmpty();
    }

    /**
     * Whether a pack's <em>pinned</em> Promotion Path version names an
     * Environment (ADR-007).
     *
     * <p>The pinned version, not the path's current one: a pack that was
     * assigned version 1 is still heading for the Environments version 1 named,
     * even after the team published a version 2 that drops one of them.
     */
    private boolean namesEnvironment(
            ReleasePack pack, EnvironmentId environmentId,
            Map<dev.tower.domain.promotionpath.PromotionPathId, PromotionPath> pathsById) {

        return pinnedVersion(pack, pathsById)
                .map(version -> version.contains(environmentId))
                .orElse(false);
    }

    private Optional<PromotionPathVersion> pinnedVersion(
            ReleasePack pack,
            Map<dev.tower.domain.promotionpath.PromotionPathId, PromotionPath> pathsById) {

        return pack.promotionPath().flatMap(assignment -> Optional
                .ofNullable(pathsById.get(assignment.pathId()))
                .flatMap(path -> path.version(assignment.versionNumber())));
    }

    private ReleasePackSummary summarise(
            ReleasePack pack, ReleasePackProgression progression,
            Map<EnvironmentId, Environment> environmentsById,
            Map<dev.tower.domain.promotionpath.PromotionPathId, PromotionPath> pathsById) {

        String pathName = pack.promotionPath()
                .map(assignment -> pathsById.get(assignment.pathId()))
                .map(PromotionPath::name)
                .orElse(null);

        // Every Environment at the highest Stage the pack was seen in — all of
        // them, not one.
        //
        // ADR-008 derives state from Stage, and several Environments commonly
        // share a Stage: a release fully in UAT and partly in SIT1, both
        // Validation, has got equally far in both. Picking one would answer a
        // question the data does not answer, and the pick would look like a
        // judgement about which Environment counts.
        int highest = -1;
        List<String> furthest = new ArrayList<>();
        Instant lastObserved = null;
        for (ReleasePackProgression.EnvironmentArrival arrival : progression.arrivals()) {
            Environment environment = environmentsById.get(arrival.environmentId());
            if (environment == null) {
                continue;
            }
            if (environment.stage().ordinal() > highest) {
                highest = environment.stage().ordinal();
                furthest.clear();
            }
            if (environment.stage().ordinal() == highest) {
                furthest.add(environment.name());
            }
            Instant seen = arrival.completeAt() != null ? arrival.completeAt() : arrival.firstObservedAt();
            if (lastObserved == null || seen.isAfter(lastObserved)) {
                lastObserved = seen;
            }
        }
        furthest.sort(String.CASE_INSENSITIVE_ORDER);

        ReleasePackState state = observations.stateOf(pack.id());

        return new ReleasePackSummary(
                pack.id(), pack.name(), state, pathName, pack.contents().size(),
                progression.arrivals().size(), List.copyOf(furthest), lastObserved);
    }
}
