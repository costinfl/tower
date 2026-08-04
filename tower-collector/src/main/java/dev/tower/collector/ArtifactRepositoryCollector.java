package dev.tower.collector;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import dev.tower.application.port.out.ArtifactCollector;
import dev.tower.application.port.out.ConnectorCredentialsPort;
import dev.tower.application.sync.ConnectionTest;
import dev.tower.connector.api.ArtifactLocator;
import dev.tower.connector.api.ConnectorCredential;
import dev.tower.connector.api.ArtifactRepositoryConnector;

/**
 * Carries composed coordinates to a repository and the answers back (ADR-021).
 *
 * <p>The fifth Collector and the emptiest one, which is the design rather than an
 * oversight. The deployment and pipeline Collectors append Observations; the
 * source control Collector proposes versions; the work item Collector shows a
 * tracker's current wording beside an accepted one. This one has no side effect
 * at all — it asks whether some bytes are at an address and returns the answer.
 * FR-084 is explicit that nothing it learns is stored, and there is no table in
 * the schema for it to be stored in.
 *
 * <p>One instance per installed Artifact Repository Connector, wired by
 * {@link ArtifactRepositoryCollectors}, for the reason
 * {@link IssueTrackerCollectors} gives: which Connectors exist is not known until
 * runtime, and a single injected Connector would quietly become ambiguous the
 * moment a second one was installed.
 *
 * <p>Takes the repository's address per call rather than looking one up. A team
 * has one issue tracker; it commonly has an image registry and a chart repository
 * at different addresses, and which is meant is decided by the coordinate binding
 * the caller is confirming.
 */
public class ArtifactRepositoryCollector implements ArtifactCollector {

    /** Reported wherever a Connector would name a partition it read within. */
    private static final String WHOLE_REPOSITORY = "whole repository";

    private final ArtifactRepositoryConnector connector;
    private final ConnectorCredentialsPort credentials;

    public ArtifactRepositoryCollector(ArtifactRepositoryConnector connector,
                                       ConnectorCredentialsPort credentials) {
        this.connector = Objects.requireNonNull(connector);
        this.credentials = Objects.requireNonNull(credentials);
    }

    @Override
    public String connectorId() {
        return connector.connectorId();
    }

    @Override
    public List<ConfirmedArtifact> read(String system, List<String> coordinates) {
        if (coordinates.isEmpty()) {
            // An Application with no template bound is ordinary. Asking the
            // repository about nothing would spend a round trip to learn nothing,
            // and would fail loudly for a team who never bound one.
            return List.of();
        }

        ConnectorCredential credential = credentialFor(system);
        try {
            return connector.readArtifacts(new ArtifactLocator(system), coordinates, credential)
                    .stream()
                    .map(artifact -> new ConfirmedArtifact(artifact.coordinate(), artifact.digest(),
                            artifact.storedAt(), artifact.sizeBytes(), artifact.url()))
                    .toList();
        } finally {
            credential.clear();
        }
    }

    @Override
    public ConnectionTest checkConnection(String system) {
        ConnectorCredential credential;
        try {
            // Inside the try, because reading the stored credential can fail on
            // its own — a master key that no longer matches the file. FR-061 asks
            // what the state is, and "your credentials cannot be decrypted" is an
            // answer to that question rather than an accident on the way to it.
            credential = credentialFor(system);
        } catch (RuntimeException e) {
            return ConnectionTest.unreachable(
                    connector.connectorId(), system, WHOLE_REPOSITORY, describe(e));
        }
        // Recorded before the call, because the credential is cleared by it.
        boolean presented = credential.isPresent();
        try {
            connector.checkConnection(new ArtifactLocator(system), credential);
            return presented
                    ? ConnectionTest.reachable(connector.connectorId(), system, WHOLE_REPOSITORY)
                    : ConnectionTest.reachableAnonymously(
                            connector.connectorId(), system, WHOLE_REPOSITORY);
        } catch (RuntimeException e) {
            // Reported rather than thrown: FR-061 asks what the state is, and
            // "refused the credential" is an answer, not an accident.
            return ConnectionTest.unreachable(
                    connector.connectorId(), system, WHOLE_REPOSITORY, describe(e));
        } finally {
            credential.clear();
        }
    }

    /**
     * The credential for this repository, or none.
     *
     * <p>Absent is allowed for the reason it is allowed for a tracker: a public
     * repository reads without a token, and a team should not have to mint one to
     * confirm a chart they published openly. Keyed by Connector and repository
     * address (NFR-028), so one token serves every artifact in the same
     * repository.
     */
    private ConnectorCredential credentialFor(String system) {
        return credentials.secretFor(connector.connectorId(), system)
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
                ? "The repository could not be read, and gave no reason."
                : message;
    }
}
