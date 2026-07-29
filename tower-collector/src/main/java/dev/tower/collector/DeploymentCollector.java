package dev.tower.collector;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.tower.application.binding.ApplicationBinding;
import dev.tower.application.binding.EnvironmentBinding;
import dev.tower.application.port.out.ApplicationVersionRepository;
import dev.tower.application.port.out.ConnectorCredentialsPort;
import dev.tower.application.port.out.DeploymentObservationCollector;
import dev.tower.application.port.out.ExternalBindingRepository;
import dev.tower.application.port.out.ObservationRepository;
import dev.tower.application.sync.SyncRun;
import dev.tower.application.sync.SyncRunId;
import dev.tower.application.sync.UnrecognizedWorkload;
import dev.tower.connector.api.ConnectorCredential;
import dev.tower.connector.api.ConnectorException;
import dev.tower.connector.api.DeploymentLocator;
import dev.tower.connector.api.DeploymentPlatformConnector;
import dev.tower.connector.api.RunningWorkload;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.observation.Observation;
import dev.tower.domain.observation.ObservationSource;

/**
 * Turns what a Deployment Platform reports into Observations (issue #49).
 *
 * <p>This is the component Connector-Model.md describes: it receives information
 * from a Connector, validates it, converts it into Observations, and preserves
 * timestamps and source references. ADR-011 additionally assigns it the change
 * comparison, on the grounds that suppressing an append inside the repository
 * would hide the decision in the persistence adapter where neither the
 * requirement nor the reasoning is visible.
 *
 * <p>The rule it enforces is ADR-011's: an Observation is appended only when the
 * version observed for an Environment and Application differs from the newest
 * one Tower already holds for that pair. Asking the same question twice and
 * receiving the same answer is not two facts.
 *
 * <p>Everything it cannot attribute is reported rather than guessed or dropped
 * (ADR-012, FR-060). Three things can go unattributed: an image no binding
 * names, a tag no version pattern recognises, and an image pinned to a digest so
 * that no tag exists at all.
 *
 * <p>A scope that fails does not fail the run. Connector-Model.md requires that
 * a Connector failure leave the Canonical Model intact, so the namespaces that
 * were read keep their Observations and the one that failed is recorded.
 */
@Component
public class DeploymentCollector implements DeploymentObservationCollector {

    private final DeploymentPlatformConnector connector;
    private final ExternalBindingRepository bindings;
    private final ObservationRepository observations;
    private final ApplicationVersionRepository versions;
    private final ConnectorCredentialsPort credentials;
    private final Clock clock;

    public DeploymentCollector(DeploymentPlatformConnector connector,
                               ExternalBindingRepository bindings,
                               ObservationRepository observations,
                               ApplicationVersionRepository versions,
                               ConnectorCredentialsPort credentials,
                               Clock clock) {
        this.connector = Objects.requireNonNull(connector);
        this.bindings = Objects.requireNonNull(bindings);
        this.observations = Objects.requireNonNull(observations);
        this.versions = Objects.requireNonNull(versions);
        this.credentials = Objects.requireNonNull(credentials);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public String connectorId() {
        return connector.connectorId();
    }

    @Override
    public SyncRun collect() {
        Instant startedAt = clock.instant();
        String connectorId = connector.connectorId();

        List<EnvironmentBinding> environmentBindings = bindings.findAllEnvironmentBindings(connectorId);
        Map<String, ApplicationBinding> byImage = bindings.findAllApplicationBindings(connectorId).stream()
                .collect(Collectors.toMap(ApplicationBinding::image, Function.identity(), (first, second) -> first));

        var run = new RunTally();
        for (EnvironmentBinding binding : environmentBindings) {
            collectScope(binding, byImage, run);
        }

        return new SyncRun(SyncRunId.newId(), connectorId, startedAt, clock.instant(),
                outcomeOf(environmentBindings.size(), run.failures.size()),
                run.workloadsRead, run.observationsAppended, run.unrecognized, run.failures);
    }

    private void collectScope(EnvironmentBinding binding, Map<String, ApplicationBinding> byImage, RunTally run) {
        var locator = new DeploymentLocator(binding.target(), binding.scope());
        ConnectorCredential credential = credentialFor(binding.connectorId(), binding.target());
        try {
            for (RunningWorkload workload : connector.readWorkloads(locator, credential)) {
                run.workloadsRead++;
                attribute(binding, workload, byImage, run);
            }
        } catch (ConnectorException e) {
            // Recorded, not thrown. The scopes already read keep their
            // Observations (Connector-Model.md, Failure Handling).
            //
            // The message is taken as-is: a ConnectorException already names the
            // locator it failed on, and prefixing it again produced text that
            // said the cluster and namespace twice in one line.
            run.failures.add(e.getMessage());
        } finally {
            credential.clear();
        }
    }

    private void attribute(EnvironmentBinding binding, RunningWorkload workload,
                           Map<String, ApplicationBinding> byImage, RunTally run) {
        String scope = binding.scope();

        if (!workload.hasVersionableTag()) {
            run.unrecognized.add(UnrecognizedWorkload.digestPinned(
                    scope, workload.name(), workload.imageReference()));
            return;
        }

        ApplicationBinding applicationBinding = byImage.get(workload.image());
        if (applicationBinding == null) {
            run.unrecognized.add(UnrecognizedWorkload.noBinding(
                    scope, workload.name(), workload.imageReference()));
            return;
        }

        Optional<String> version = applicationBinding.resolveVersion(workload.imageTag());
        if (version.isEmpty()) {
            run.unrecognized.add(UnrecognizedWorkload.tagNotMatched(
                    scope, workload.name(), workload.imageReference(),
                    applicationBinding.versionPattern()));
            return;
        }

        ApplicationVersion applicationVersion = resolveVersion(applicationBinding, version.get(), workload);
        if (appendIfChanged(binding.environmentId(), applicationVersion, workload)) {
            run.observationsAppended++;
        }
    }

    /**
     * ADR-011's rule, and the whole point of this class.
     *
     * <p>Compares against the newest Observation Tower holds for this Environment
     * and Application. Comparing per Application rather than per Application
     * Version is what makes a change detectable at all: the question is "what is
     * deployed here now", and a different version is a different answer.
     */
    private boolean appendIfChanged(EnvironmentId environmentId, ApplicationVersion version,
                                    RunningWorkload workload) {
        Optional<Observation> newest = observations.findAllInEnvironment(environmentId).stream()
                .filter(observation -> observation.applicationId().equals(version.applicationId()))
                .max(Comparator.comparing(Observation::observedAt));

        if (newest.isPresent() && newest.get().applicationVersionId().equals(version.id())) {
            return false;
        }

        observations.append(Observation.record(
                environmentId, version.applicationId(), version.id(),
                observedAt(workload), ObservationSource.collector(connector.connectorId())));
        return true;
    }

    /**
     * The platform's own timestamp (FR-021), unless it is in the future.
     *
     * <p>A clock skewed ahead on the cluster would otherwise let a fact be dated
     * later than now, and an append-only store could never correct it — only bury
     * it. ObservationService refuses a future manual Observation for the same
     * reason; here the fact is real and only its timestamp is suspect, so it is
     * clamped rather than discarded.
     */
    private Instant observedAt(RunningWorkload workload) {
        Instant now = clock.instant();
        return workload.observedAt().isAfter(now) ? now : workload.observedAt();
    }

    /**
     * Reuses the Application Version already recorded, or records the new one.
     *
     * <p>Creating a Version of an Application the user has bound is not the
     * guessing ADR-012 forbids: the Application is known, and a version of it
     * appearing in an Environment is exactly the fact Tower exists to observe.
     * Scenario 7 depends on this — drift is an Application Version nobody put in
     * a Release Pack, and Tower must report it without judging it.
     */
    private ApplicationVersion resolveVersion(ApplicationBinding binding, String version, RunningWorkload workload) {
        return versions.findByApplicationAndVersion(binding.applicationId(), version)
                .orElseGet(() -> versions.save(ApplicationVersion.create(
                        binding.applicationId(), version, null, workload.imageTag(), null, null)));
    }

    private ConnectorCredential credentialFor(String connectorId, String target) {
        return credentials.secretFor(connectorId, target)
                .map(secret -> {
                    try {
                        return ConnectorCredential.bearerToken(secret);
                    } finally {
                        Arrays.fill(secret, '\0');
                    }
                })
                .orElseGet(ConnectorCredential::none);
    }

    private static SyncRun.Outcome outcomeOf(int scopes, int failures) {
        if (failures == 0) {
            return SyncRun.Outcome.SUCCEEDED;
        }
        return failures == scopes ? SyncRun.Outcome.FAILED : SyncRun.Outcome.PARTIALLY_SUCCEEDED;
    }

    /** Mutable tally for one run, kept out of {@link SyncRun}, which is immutable. */
    private static final class RunTally {
        private int workloadsRead;
        private int observationsAppended;
        private final List<UnrecognizedWorkload> unrecognized = new ArrayList<>();
        private final List<String> failures = new ArrayList<>();
    }
}
