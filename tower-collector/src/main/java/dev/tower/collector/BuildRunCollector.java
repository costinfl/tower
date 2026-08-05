package dev.tower.collector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import dev.tower.application.binding.BuildJobBinding;
import dev.tower.application.discovery.DiscoveredVersion;
import dev.tower.application.discovery.VersionDiscovery;
import dev.tower.application.port.out.ApplicationVersionRepository;
import dev.tower.application.port.out.BuildVersionCollector;
import dev.tower.application.port.out.ConnectorCredentialsPort;
import dev.tower.application.port.out.ExternalBindingRepository;
import dev.tower.application.sync.ConnectionTest;
import dev.tower.connector.api.CiCdConnector;
import dev.tower.connector.api.ConnectorCredential;
import dev.tower.connector.api.PipelineLocator;
import dev.tower.connector.api.PipelineRun;
import dev.tower.domain.application.ApplicationId;

/**
 * Turns successful build runs into candidate Application Versions (ADR-020).
 *
 * <p>The fifth Collector, reading the same CI system as {@link PipelineRunCollector}
 * and answering a different question. That one asks "what reached an
 * Environment" and appends Observations; this asks "what exists to be released"
 * and proposes candidates. ADR-020 exists because conflating the two records a
 * build as though it had deployed something.
 *
 * <p>Writes nothing, which is the whole of what makes it safe. A candidate is a
 * proposal a person accepts or ignores; BR-01 makes an Application Version
 * immutable, so one created here without anybody asking would be immutable too.
 * The practical consequence is that a wrong version pattern on a build job is
 * corrected rather than outlived — the opposite of the same mistake on a
 * deployment job, where ADR-012's warning bites and the Observations survive.
 *
 * <p>Only successful runs propose anything. A failed or still-running build has
 * not produced a version, and offering one would put a candidate on a screen for
 * something that does not exist. Unlike its sibling, this does not report the
 * runs it passed over: a candidate list is a list of things a person might
 * register, and filling it with builds that failed would bury the ones they can
 * actually act on. The deployment side reports them because there a missing run
 * means a missing fact; here it means one fewer suggestion.
 *
 * <p>Not a {@code @Component}: which CI Connectors exist is not known until
 * runtime, and {@link BuildRunCollectors} gives each installed one its own
 * instance for the reason {@link IssueTrackerCollectors} does the same.
 */
public class BuildRunCollector implements BuildVersionCollector {

    /**
     * The most runs read from one build job in a single pass.
     *
     * <p>Smaller than the deployment side's, and for a different reason. There,
     * a deep first pass builds a timeline that is then never re-read. Here every
     * pass reads the same window, because a candidate list is about what is worth
     * registering now — and nobody scrolls two hundred build numbers looking for
     * a version they meant to register last year.
     */
    static final int RUNS_PER_JOB = 25;

    private final CiCdConnector connector;
    private final ExternalBindingRepository bindings;
    private final ApplicationVersionRepository versions;
    private final ConnectorCredentialsPort credentials;

    public BuildRunCollector(CiCdConnector connector,
                             ExternalBindingRepository bindings,
                             ApplicationVersionRepository versions,
                             ConnectorCredentialsPort credentials) {
        this.connector = Objects.requireNonNull(connector);
        this.bindings = Objects.requireNonNull(bindings);
        this.versions = Objects.requireNonNull(versions);
        this.credentials = Objects.requireNonNull(credentials);
    }

    @Override
    public String connectorId() {
        return connector.connectorId();
    }

    @Override
    public VersionDiscovery discover(ApplicationId applicationId) {
        List<BuildJobBinding> jobs = bindings.findBuildJobBindings(applicationId).stream()
                .filter(binding -> binding.connectorId().equals(connector.connectorId()))
                .toList();

        if (jobs.isEmpty()) {
            return VersionDiscovery.failed(applicationId, null,
                    "No build job is bound to this Application.");
        }

        // Keyed by version so two jobs producing the same one propose it once.
        // First wins, and the jobs are read in the order the binding store
        // returns them, which is stable — a candidate that changed which job it
        // cited between two reads would look like a different thing.
        Map<String, DiscoveredVersion> byVersion = new LinkedHashMap<>();
        List<String> unmatched = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (BuildJobBinding job : jobs) {
            readJob(applicationId, job, byVersion, unmatched, failures);
        }

        // Every job failing is a failure; some failing is a partial answer, and
        // the candidates that were read are still worth showing. Saying "could
        // not read" over a list that has entries in it would be false.
        if (!failures.isEmpty() && byVersion.isEmpty()) {
            return VersionDiscovery.failed(applicationId, jobs.get(0).system(),
                    String.join(" ", failures));
        }

        return VersionDiscovery.found(applicationId, jobs.get(0).system(),
                List.copyOf(byVersion.values()), List.copyOf(unmatched));
    }

    private void readJob(ApplicationId applicationId, BuildJobBinding binding,
                         Map<String, DiscoveredVersion> byVersion, List<String> unmatched,
                         List<String> failures) {

        ConnectorCredential credential;
        try {
            credential = credentialFor(binding.connectorId(), binding.system());
        } catch (RuntimeException e) {
            failures.add(describe(e));
            return;
        }

        List<PipelineRun> runs;
        try {
            // No lower bound on time: a candidate list is about what is worth
            // registering now, so every pass reads the same recent window rather
            // than only what is new since Tower last looked. Nothing is stored,
            // so there is no "last looked" to read from anyway.
            runs = connector.readRuns(new PipelineLocator(binding.system(), binding.job()),
                    null, RUNS_PER_JOB, credential);
        } catch (RuntimeException e) {
            failures.add(describe(e));
            return;
        } finally {
            credential.clear();
        }

        // Newest first, so the most recently built version leads the list. The
        // opposite of the deployment side, which must process oldest first to
        // build a timeline — here there is no timeline, only a list somebody
        // reads from the top.
        runs.stream()
                .sorted(Comparator.comparing(PipelineRun::startedAt).reversed())
                .forEach(run -> consider(applicationId, binding, run, byVersion, unmatched));
    }

    private void consider(ApplicationId applicationId, BuildJobBinding binding, PipelineRun run,
                          Map<String, DiscoveredVersion> byVersion, List<String> unmatched) {

        // A build that failed or is still going has produced no version, and a
        // candidate for one would be a suggestion to register something that
        // does not exist.
        if (!run.succeeded()) {
            return;
        }

        String text = versionText(binding, run);
        if (text == null || text.isBlank()) {
            unmatched.add(origin(binding, run) + " carried no version in " + where(binding));
            return;
        }

        Optional<String> version = binding.resolveVersion(text);
        if (version.isEmpty()) {
            // Reported rather than dropped, exactly as an unrecognised git ref
            // is: a user whose pattern is wrong needs to see what it failed on.
            unmatched.add(origin(binding, run) + ": \"" + text + "\"");
            return;
        }

        byVersion.putIfAbsent(version.get(), DiscoveredVersion.fromBuild(
                version.get(),
                connector.connectorId(),
                origin(binding, run),
                run.runId(),
                versions.findByApplicationAndVersion(applicationId, version.get()).isPresent()));
    }

    /**
     * Where a run recorded the version, read the same way the deployment side
     * reads it.
     *
     * <p>Shared meanings rather than shared code: the two Collectors take
     * different binding types, and a common helper would have to take one of
     * them or a third shape invented to hold both.
     */
    private String versionText(BuildJobBinding binding, PipelineRun run) {
        return switch (binding.versionSource()) {
            case PARAMETER -> run.value(binding.versionKey()).orElse(null);
            case RUN_NAME -> run.displayName();
            case JOB_PATH -> binding.job();
        };
    }

    private String where(BuildJobBinding binding) {
        return switch (binding.versionSource()) {
            case PARAMETER -> "the parameter " + binding.versionKey();
            case RUN_NAME -> "its run name";
            case JOB_PATH -> "its job path";
        };
    }

    /** The job and run, as the CI system names them. */
    private String origin(BuildJobBinding binding, PipelineRun run) {
        return binding.job() + " #" + run.runId();
    }

    @Override
    public ConnectionTest checkConnection(String system, String job) {
        String connectorId = connector.connectorId();
        ConnectorCredential credential;
        try {
            credential = credentialFor(connectorId, system);
        } catch (RuntimeException e) {
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
}
