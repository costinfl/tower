package dev.tower.connector.api;

import java.util.List;

/**
 * Reads the branches and tags a repository holds.
 *
 * <p>One interface per Connector category, satisfying CM-02, and the sibling of
 * {@link DeploymentPlatformConnector}. ADR-014 keeps this one neutral in a
 * stronger sense than ADR-003 requires: it speaks git rather than any vendor's
 * API, so GitHub, GitLab, Bitbucket, a self-hosted server and a bare repository
 * on a file share are all the same implementation reading a different URL.
 *
 * <p>Every operation here is a read, and for this Connector that is structural
 * rather than a matter of naming discipline. Reference discovery — what
 * {@code git ls-remote} performs — has no write counterpart in the same exchange.
 * A vendor REST token could push, tag and open a pull request, and Tower's
 * read-only guarantee would rest on choosing not to call those endpoints; here
 * there is nothing to choose not to call.
 *
 * <p>Implementations obtain their credentials through the credentials port
 * (ADR-009, NFR-028). They never read a file and never learn where secrets live.
 * A public repository needs none, which is why the credential may be absent.
 */
public interface SourceControlConnector {

    /**
     * Identifies this Connector in provenance and in reports.
     *
     * <p>Stable across runs and versions, for the same reason
     * {@link DeploymentPlatformConnector#connectorId()} is: anything Tower has
     * stored keeps referring to it.
     */
    String connectorId();

    /**
     * Every branch and tag the repository currently holds.
     *
     * <p>Returns an empty list for a repository that exists and has no refs — a
     * freshly initialised one. A repository that cannot be read is a failure and
     * throws, because silently returning nothing would be indistinguishable from
     * an empty repository and would make Tower report that every branch had been
     * deleted.
     *
     * <p>The result is a set of candidates, not a set of Application Versions.
     * BR-01 makes an Application Version immutable, so what a caller does with a
     * ref for a version Tower already holds is to leave it alone.
     *
     * <p>The credential is supplied by the caller rather than fetched here, so a
     * Connector never learns where secrets live. It may be absent for a public
     * repository; see {@link ConnectorCredential}.
     *
     * @throws ConnectorException when the repository cannot be reached or refuses
     *                            the request
     */
    List<SourceRef> readRefs(RepositoryLocator locator, ConnectorCredential credential);

    /**
     * Confirms the repository is reachable and the credentials are accepted,
     * without modifying anything (FR-061, FR-036).
     *
     * <p>Separate from {@link #readRefs} so a configuration can be verified
     * before anything depends on it, and so a failure distinguishes "cannot
     * connect" from "connected and found nothing".
     *
     * @throws ConnectorException when the repository is unreachable or rejects
     *                            the credentials, carrying a message fit to show
     *                            a user
     */
    void checkConnection(RepositoryLocator locator, ConnectorCredential credential);
}
