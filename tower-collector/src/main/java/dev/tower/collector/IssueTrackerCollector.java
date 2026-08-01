package dev.tower.collector;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dev.tower.application.binding.IssueTrackerBinding;
import dev.tower.application.port.out.ConnectorCredentialsPort;
import dev.tower.application.port.out.ExternalBindingRepository;
import dev.tower.application.port.out.WorkItemCollector;
import dev.tower.application.sync.ConnectionTest;
import dev.tower.connector.api.ConnectorCredential;
import dev.tower.connector.api.IssueLocator;
import dev.tower.connector.api.IssueTrackerConnector;

/**
 * Carries work item identifiers to a tracker and the answers back (ADR-018).
 *
 * <p>The third Collector, and the only one that observes nothing. The deployment
 * Collector appends Observations because what a platform is running is a fact
 * Tower saw; the source control Collector stores nothing but proposes versions a
 * user may register. This one neither observes nor proposes: it reads what a
 * tracker currently says about references a developer already wrote down, so
 * that a person can look at the two side by side. Nothing it returns is
 * persisted, which is precisely what keeps a generated document reproducible
 * (NFR-025) — the title a document prints is the one somebody accepted, not
 * whatever the tracker said this morning.
 *
 * <p>One instance per installed Issue Tracking Connector, wired by
 * {@link IssueTrackerCollectors}. It is not a {@code @Component}: which
 * Connectors exist is not known until runtime, and a single injected Connector
 * would quietly become ambiguous the moment a second tracker was installed.
 *
 * <p>Holds the binding lookup rather than being handed a locator, because the
 * application layer may not know that a tracker has a locator shape at all. What
 * {@code owner/repo} or a Jira site URL means is the Connector's business alone.
 */
public class IssueTrackerCollector implements WorkItemCollector {

    /** Reported wherever a Connector would name a partition it read within. */
    private static final String WHOLE_TRACKER = "whole tracker";

    private final IssueTrackerConnector connector;
    private final ExternalBindingRepository bindings;
    private final ConnectorCredentialsPort credentials;

    public IssueTrackerCollector(IssueTrackerConnector connector,
                                 ExternalBindingRepository bindings,
                                 ConnectorCredentialsPort credentials) {
        this.connector = Objects.requireNonNull(connector);
        this.bindings = Objects.requireNonNull(bindings);
        this.credentials = Objects.requireNonNull(credentials);
    }

    @Override
    public String connectorId() {
        return connector.connectorId();
    }

    @Override
    public List<CollectedWorkItem> read(List<String> identifiers) {
        if (identifiers.isEmpty()) {
            // A release that names no work items is ordinary. Asking the tracker
            // about nothing would spend a network round trip to learn nothing,
            // and would fail loudly for a team with no tracker bound who never
            // linked an item in the first place.
            return List.of();
        }

        IssueTrackerBinding binding = boundTracker();
        ConnectorCredential credential = credentialFor(binding.locator());
        try {
            return connector.readIssues(new IssueLocator(binding.locator()), identifiers, credential)
                    .stream()
                    .map(issue -> new CollectedWorkItem(issue.identifier(), issue.title(),
                            issue.status(), issue.closed(), issue.url()))
                    .toList();
        } finally {
            credential.clear();
        }
    }

    @Override
    public ConnectionTest checkConnection() {
        Optional<IssueTrackerBinding> bound = bindings.findIssueTrackerBinding(connector.connectorId());
        if (bound.isEmpty()) {
            // Not a failure to connect — there is nothing to connect to yet. Said
            // in those words so nobody goes looking for a network problem.
            return ConnectionTest.unreachable(connector.connectorId(), null, WHOLE_TRACKER,
                    "No tracker is bound to this Connector yet.");
        }

        String locator = bound.get().locator();
        ConnectorCredential credential = credentialFor(locator);
        // Recorded before the call, because the credential is cleared by it.
        boolean presented = credential.isPresent();
        try {
            connector.checkConnection(new IssueLocator(locator), credential);
            return presented
                    ? ConnectionTest.reachable(connector.connectorId(), locator, WHOLE_TRACKER)
                    : ConnectionTest.reachableAnonymously(
                            connector.connectorId(), locator, WHOLE_TRACKER);
        } catch (RuntimeException e) {
            // Reported rather than thrown: FR-061 asks what the state is, and
            // "refused the credential" is an answer, not an accident.
            return ConnectionTest.unreachable(connector.connectorId(), locator, WHOLE_TRACKER,
                    describe(e));
        } finally {
            credential.clear();
        }
    }

    private IssueTrackerBinding boundTracker() {
        return bindings.findIssueTrackerBinding(connector.connectorId())
                .orElseThrow(() -> new IllegalStateException(
                        "No tracker is bound to the " + connector.connectorId() + " Connector."));
    }

    /**
     * The credential for this tracker, or none.
     *
     * <p>Absent is normal, as it is for source control and unlike a cluster: a
     * public repository's issues read without a token, and a team should not have
     * to mint one to see their own open work.
     */
    private ConnectorCredential credentialFor(String locator) {
        return credentials.secretFor(connector.connectorId(), locator)
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
    private String describe(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isBlank()
                ? "The tracker could not be read, and gave no reason."
                : message;
    }
}
