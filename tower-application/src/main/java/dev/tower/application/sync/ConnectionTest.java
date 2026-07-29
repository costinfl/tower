package dev.tower.application.sync;

/**
 * The result of checking whether a Connector can reach a target (FR-061).
 *
 * <p>Reports rather than throws, because "cannot connect" is the answer the user
 * asked for, not a failure of the asking. A screen showing the reason needs it
 * as a value.
 *
 * <p>Deliberately distinct from a {@link SyncRun}. A connection test reads
 * nothing into the Canonical Model and appends no Observation, so recording it
 * as a run would put an entry in the history that observed nothing.
 *
 * @param connectorId which Connector was asked
 * @param target      the endpoint it tried
 * @param scope       the partition within it
 * @param reachable   whether the platform answered and accepted the credential
 * @param message     what happened, in words fit to show a user
 */
public record ConnectionTest(
        String connectorId, String target, String scope, boolean reachable, String message) {

    public static ConnectionTest reachable(String connectorId, String target, String scope) {
        return new ConnectionTest(connectorId, target, scope, true,
                "Connected and the credential was accepted.");
    }

    public static ConnectionTest unreachable(
            String connectorId, String target, String scope, String message) {
        return new ConnectionTest(connectorId, target, scope, false, message);
    }
}
