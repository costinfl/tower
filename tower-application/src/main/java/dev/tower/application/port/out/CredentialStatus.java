package dev.tower.application.port.out;

import java.time.Instant;

/**
 * What may be said about a stored credential without disclosing it.
 *
 * <p>Implementation-Plan.md / Credential Handling: once saved, the user
 * interface shows that a credential is configured and the stored value is never
 * returned. This record is the shape of that statement, and it deliberately has
 * no field that could carry the secret — not even a masked fragment, because a
 * fragment of a bearer token is still a disclosure and helps an observer confirm
 * a guess.
 *
 * @param connectorId the Connector the credential belongs to
 * @param target      the endpoint it authenticates against
 * @param configured  whether a secret is stored for that pair
 * @param updatedAt   when it was last written, or null when nothing is stored
 */
public record CredentialStatus(String connectorId, String target, boolean configured, Instant updatedAt) {

    public static CredentialStatus absent(String connectorId, String target) {
        return new CredentialStatus(connectorId, target, false, null);
    }

    public static CredentialStatus configuredAt(String connectorId, String target, Instant updatedAt) {
        return new CredentialStatus(connectorId, target, true, updatedAt);
    }
}
