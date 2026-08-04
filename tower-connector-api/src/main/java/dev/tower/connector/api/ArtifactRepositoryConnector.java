package dev.tower.connector.api;

import java.util.List;

/**
 * Confirms that artifacts are where an Application Version says they should be
 * (Connector-Model.md, ADR-021).
 *
 * <p>The second Connector category that produces no Observations, and it
 * produces nothing stored either. An artifact has no Environment, so there is no
 * Observation to make; nobody stated it, so it is not Intent. ADR-021 records
 * that it is therefore confirmed on demand and shown beside what Tower holds —
 * the arrangement ADR-018 established for a work item's current title.
 *
 * <p>Read-only, like every Connector (ADR-001). There is no operation here that
 * uploads, promotes, copies, tags or deletes anything, and there never will be.
 *
 * <p>Deliberately asks about coordinates rather than searching for them. A
 * repository's capable search is usually a POST — Artifactory's AQL is — and
 * ADR-001's structural form forbids a Connector from calling one. Composing the
 * coordinate from a version Tower already holds makes a direct question
 * possible, and ADR-021 records that the team's tagging convention is what makes
 * that composition possible in the first place.
 */
public interface ArtifactRepositoryConnector {

    /** Stable identifier for this Connector, e.g. "artifactory". */
    String connectorId();

    /**
     * The artifacts among these coordinates that the repository holds.
     *
     * <p>A coordinate the repository does not have is <em>omitted</em> rather
     * than reported as an error, exactly as an unknown work item identifier is
     * (ADR-018). An artifact that is not there yet is an ordinary situation — a
     * build that has not run, a chart not yet published, a template with a typo
     * in it — and the caller tells that apart from an unreachable repository by
     * whether this returned or threw.
     *
     * <p>Order is not guaranteed; callers match on
     * {@link StoredArtifact#coordinate()}.
     *
     * @throws ConnectorException when the repository could not be read at all
     */
    List<StoredArtifact> readArtifacts(ArtifactLocator locator, List<String> coordinates,
                                       ConnectorCredential credential);

    /**
     * Confirms the repository answers and the credential is accepted, without
     * modifying anything (FR-061).
     *
     * @throws ConnectorException when it does not
     */
    void checkConnection(ArtifactLocator locator, ConnectorCredential credential);
}
