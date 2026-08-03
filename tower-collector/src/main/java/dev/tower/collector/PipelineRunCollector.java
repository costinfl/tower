package dev.tower.collector;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dev.tower.application.binding.PipelineJobBinding;
import dev.tower.application.port.out.ApplicationVersionRepository;
import dev.tower.application.port.out.ConnectorCredentialsPort;
import dev.tower.application.port.out.ExternalBindingRepository;
import dev.tower.application.port.out.ObservationRepository;
import dev.tower.application.port.out.PipelineRunObservationCollector;
import dev.tower.application.sync.ConnectionTest;
import dev.tower.application.sync.NotRecordedRun;
import dev.tower.application.sync.PipelineSyncReport;
import dev.tower.application.sync.PipelineSyncReportId;
import dev.tower.connector.api.CiCdConnector;
import dev.tower.connector.api.ConnectorCredential;
import dev.tower.connector.api.PipelineLocator;
import dev.tower.connector.api.PipelineRun;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.observation.Observation;
import dev.tower.domain.observation.ObservationSource;

/**
 * Turns successful deployment runs into Observations (ADR-020).
 *
 * <p>The fourth Collector, and the one that reads history rather than state. The
 * deployment Collector asks a platform what is running now; this asks a CI
 * system what it did, and the difference runs through everything below.
 *
 * <p>Runs are processed oldest first, and each is compared against the newest
 * Observation Tower holds for that Environment and Application <em>at or before
 * that run's own instant</em>. That is ADR-011's rule applied to a timeline
 * rather than to a poll, and the qualification is what makes it correct here: a
 * job that deployed 2.5.0, then 2.6.0, then 2.5.0 again recorded three changes,
 * and comparing every run against the single newest Observation would collapse
 * the third into nothing. Comparing against what was true at the time keeps the
 * sequence and still drops the repetitions, which is what ADR-011 asks for.
 *
 * <p>It also makes re-reading harmless (FR-081). On a second pass each run finds
 * the Observation it produced sitting immediately before it, naming the same
 * version, and appends nothing.
 *
 * <p>Two kinds of run are read and deliberately not recorded, and both are
 * reported rather than dropped: one that did not succeed, which is no evidence
 * that anything reached an Environment (FR-078), and one whose bound version
 * source held nothing or held something the pattern did not recognise (FR-080).
 * Guessing past either would produce an Observation with false provenance, which
 * is immutable once stored and therefore worse than none.
 *
 * <p>Not a {@code @Component}: which CI Connectors exist is not known until
 * runtime, and {@link PipelineRunCollectors} gives each installed one its own
 * instance for the reason {@link IssueTrackerCollectors} does the same.
 */
public class PipelineRunCollector implements PipelineRunObservationCollector {

    /**
     * The most runs read from one job in a single pass.
     *
     * <p>Bounds the first pass over a job with years of history. Later passes ask
     * only for runs since the newest Observation this Connector recorded, so this
     * limit is reached once and then stops mattering.
     */
    static final int RUNS_PER_JOB = 50;

    private final CiCdConnector connector;
    private final ExternalBindingRepository bindings;
    private final ObservationRepository observations;
    private final ApplicationVersionRepository versions;
    private final ConnectorCredentialsPort credentials;
    private final Clock clock;

    public PipelineRunCollector(CiCdConnector connector,
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
    public PipelineSyncReport collect() {
        Instant startedAt = clock.instant();
        String connectorId = connector.connectorId();
        List<PipelineJobBinding> jobs = bindings.findAllPipelineJobBindings(connectorId);

        var tally = new Tally();
        for (PipelineJobBinding job : jobs) {
            collectJob(job, tally);
        }

        return new PipelineSyncReport(PipelineSyncReportId.newId(), connectorId, startedAt, clock.instant(),
                jobs.size(), tally.runsRead, tally.observationsAppended,
                tally.notRecorded, tally.failures);
    }

    @Override
    public ConnectionTest checkConnection(String system, String job) {
        String connectorId = connector.connectorId();
        ConnectorCredential credential;
        try {
            credential = credentialFor(connectorId, system);
        } catch (RuntimeException e) {
            // Reading the stored credential can fail on its own, and a screen
            // asking "can you reach this?" should say so rather than answer with
            // a server error that hides the reason.
            return ConnectionTest.unreachable(connectorId, system, job, describe(e));
        }
        try {
            connector.checkConnection(new PipelineLocator(system, job), credential);
            return credential.isPresent()
                    ? ConnectionTest.reachable(connectorId, system, job)
                    : ConnectionTest.reachableAnonymously(connectorId, system, job);
        } catch (RuntimeException e) {
            return ConnectionTest.unreachable(connectorId, system, job, describe(e));
        } finally {
            credential.clear();
        }
    }

    private void collectJob(PipelineJobBinding binding, Tally tally) {
        ConnectorCredential credential;
        try {
            credential = credentialFor(binding.connectorId(), binding.system());
        } catch (RuntimeException e) {
            tally.failures.add(describe(e));
            return;
        }

        List<PipelineRun> runs;
        try {
            runs = connector.readRuns(new PipelineLocator(binding.system(), binding.job()),
                    lastSeen(binding), RUNS_PER_JOB, credential);
        } catch (RuntimeException e) {
            // Recorded, not thrown: the jobs already read keep their Observations
            // (Connector-Model.md, Failure Handling). The message is taken as-is,
            // because a ConnectorException already names the job it failed on.
            tally.failures.add(describe(e));
            return;
        } finally {
            credential.clear();
        }

        // Oldest first, whatever order the system returned them in. Appending out
        // of order would compare a run against Observations that did not exist
        // when it happened.
        runs.stream()
                .sorted(Comparator.comparing(PipelineRun::startedAt))
                .forEach(run -> {
                    tally.runsRead++;
                    consider(binding, run, tally);
                });
    }

    private void consider(PipelineJobBinding binding, PipelineRun run, Tally tally) {
        if (!run.succeeded()) {
            tally.notRecorded.add(NotRecordedRun.didNotSucceed(
                    binding.job(), run.runId(), run.outcome()));
            return;
        }

        String where = versionSourceDescription(binding);
        String text = versionText(binding, run);
        if (text == null || text.isBlank()) {
            tally.notRecorded.add(NotRecordedRun.noVersionFound(
                    binding.job(), run.runId(), run.outcome(), where,
                    String.join(", ", run.values().keySet().stream().sorted().toList())));
            return;
        }

        Optional<String> version = binding.resolveVersion(text);
        if (version.isEmpty()) {
            tally.notRecorded.add(NotRecordedRun.patternDidNotMatch(
                    binding.job(), run.runId(), run.outcome(), text, binding.versionPattern()));
            return;
        }

        ApplicationVersion applicationVersion = resolveVersion(binding, version.get(), run);
        if (appendIfChangedAt(binding, applicationVersion, observedAt(run))) {
            tally.observationsAppended++;
        }
    }

    /**
     * The text the binding says holds the version.
     *
     * <p>Nothing is read from a console log, and there is nowhere here that could
     * be: ADR-020 refuses it, because it would make Tower's correctness depend on
     * log formatting and a wrong parse produces Observations that are permanent.
     */
    private String versionText(PipelineJobBinding binding, PipelineRun run) {
        return switch (binding.versionSource()) {
            // A parameter or an environment variable — the same thing to a CI
            // system, which surfaces its parameters as variables to the run. The
            // Connector merges both into the run's named values, so this reads
            // one map rather than needing to know which it was.
            case PARAMETER -> run.value(binding.versionKey()).orElse(null);
            case RUN_NAME -> run.displayName();
            case JOB_PATH -> binding.job();
        };
    }

    private String versionSourceDescription(PipelineJobBinding binding) {
        return switch (binding.versionSource()) {
            case PARAMETER -> "a value named " + binding.versionKey();
            case RUN_NAME -> "its name";
            case JOB_PATH -> "its job path";
        };
    }

    /**
     * ADR-011's rule, asked of the moment the run happened rather than of now.
     *
     * <p>Comparing against the newest Observation overall would be wrong in both
     * directions here. A run older than something Tower already knows would look
     * like a change and be appended twice on every pass; a re-deployment of an
     * earlier version would look like no change and be lost.
     */
    private boolean appendIfChangedAt(PipelineJobBinding binding, ApplicationVersion version,
                                      Instant observedAt) {
        Optional<Observation> before = observations.findAllInEnvironment(binding.environmentId()).stream()
                .filter(observation -> observation.applicationId().equals(version.applicationId()))
                .filter(observation -> !observation.observedAt().isAfter(observedAt))
                .max(Comparator.comparing(Observation::observedAt));

        if (before.isPresent() && before.get().applicationVersionId().equals(version.id())) {
            return false;
        }

        observations.append(Observation.record(
                binding.environmentId(), version.applicationId(), version.id(),
                observedAt, ObservationSource.collector(connector.connectorId())));
        return true;
    }

    /**
     * Where to resume from, so a routine pass does not re-read history.
     *
     * <p>The newest Observation this Connector recorded for this pair. Restricted
     * to this Connector's own: a later reading from a Deployment Platform would
     * otherwise move the mark forward and hide every run in between.
     */
    private Instant lastSeen(PipelineJobBinding binding) {
        ObservationSource mine = ObservationSource.collector(connector.connectorId());
        return observations.findAllInEnvironment(binding.environmentId()).stream()
                .filter(observation -> observation.applicationId().equals(binding.applicationId()))
                .filter(observation -> observation.source().collector().equals(mine.collector()))
                .map(Observation::observedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    /**
     * The run's own instant (FR-077), unless it is in the future.
     *
     * <p>Clamped rather than discarded, for the reason the deployment Collector
     * clamps a workload's: the fact is real and only its timestamp is suspect,
     * and an append-only store could never correct a date in the future — only
     * bury it.
     */
    private Instant observedAt(PipelineRun run) {
        Instant now = clock.instant();
        return run.startedAt() == null || run.startedAt().isAfter(now) ? now : run.startedAt();
    }

    /**
     * Reuses the Application Version already recorded, or records the new one.
     *
     * <p>The run identifier becomes the build identifier, which is the one field
     * a pipeline can fill that source control cannot: it says which run produced
     * or deployed this version, and Information-Architecture.md lists build
     * identifiers among what Tower observes.
     */
    private ApplicationVersion resolveVersion(PipelineJobBinding binding, String version,
                                              PipelineRun run) {
        return versions.findByApplicationAndVersion(binding.applicationId(), version)
                .orElseGet(() -> versions.save(ApplicationVersion.create(
                        binding.applicationId(), version, null, null, null, run.runId())));
    }

    private ConnectorCredential credentialFor(String connectorId, String system) {
        return credentials.secretFor(connectorId, system)
                .map(secret -> {
                    try {
                        return ConnectorCredential.bearerToken(secret);
                    } finally {
                        Arrays.fill(secret, '\0');
                    }
                })
                .orElseGet(ConnectorCredential::none);
    }

    /** The reason in words, never the exception's type (NFR-028). */
    private static String describe(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isBlank()
                ? "The job could not be read, and gave no reason."
                : message;
    }

    /** Running totals for one pass, kept out of the flow above. */
    private static final class Tally {
        private int runsRead;
        private int observationsAppended;
        private final List<NotRecordedRun> notRecorded = new ArrayList<>();
        private final List<String> failures = new ArrayList<>();
    }
}
