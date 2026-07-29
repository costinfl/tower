package dev.tower.connector.api;

import java.util.List;

/**
 * Reads what is running on a Deployment Platform.
 *
 * <p>One interface per Connector category, satisfying CM-02. ADR-003 keeps the
 * category vendor-neutral: Kubernetes, OpenShift and ECS are implementations of
 * this interface, and the Domain Model never learns which one is installed.
 *
 * <p>Every operation here is a read. ADR-001, CM-01 and FR-036 forbid Tower from
 * modifying any External System, and FR-035 forbids it from deploying anything.
 * That is enforced two ways in this codebase: no method on this interface offers
 * a write, and the architecture tests fail the build if a class under
 * {@code dev.tower.connector} so much as calls a method named for mutation.
 *
 * <p>Neither check proves an implementation is read-only — only the target
 * system's own audit log can — so the Milestone 2 exit criteria require reading
 * it. These rules make the obvious mistake impossible, not the determined one.
 *
 * <p>Implementations obtain their credentials through the credentials port
 * (ADR-009, NFR-028). They never read a file and never learn where secrets live.
 */
public interface DeploymentPlatformConnector {

    /**
     * Identifies this Connector in Observation provenance and Sync Run records.
     *
     * <p>Becomes the {@code collector} of {@link
     * dev.tower.domain.observation.ObservationSource}, so it is what the user
     * sees when asking where a fact came from (FR-022, NFR-012). Stable across
     * runs and versions, because stored Observations keep referring to it.
     */
    String connectorId();

    /**
     * Everything running at the given locator, as the platform reports it now.
     *
     * <p>Returns an empty list when the scope exists but holds nothing. A scope
     * that cannot be read is a failure and throws, because silently returning
     * nothing would be indistinguishable from an empty Environment and would
     * make Tower report that a deployment had disappeared.
     *
     * <p>The credential is supplied by the caller rather than fetched here, so a
     * Connector never learns where secrets live. See {@link ConnectorCredential}.
     *
     * @throws ConnectorException when the platform cannot be reached or refuses
     *                            the request
     */
    List<RunningWorkload> readWorkloads(DeploymentLocator locator, ConnectorCredential credential);

    /**
     * Confirms the platform is reachable and the credentials are accepted,
     * without modifying anything (FR-061, FR-036).
     *
     * <p>Separate from {@link #readWorkloads} so the user can verify a
     * configuration before any Observation depends on it, and so a failure
     * distinguishes "cannot connect" from "connected and found nothing".
     *
     * @throws ConnectorException when the platform is unreachable or rejects the
     *                            credentials, carrying a message fit to show a user
     */
    void checkConnection(DeploymentLocator locator, ConnectorCredential credential);
}
