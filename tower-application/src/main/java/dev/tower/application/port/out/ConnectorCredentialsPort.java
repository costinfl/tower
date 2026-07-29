package dev.tower.application.port.out;

import java.util.Optional;

/**
 * Outbound port for the secrets Connectors present to External Systems.
 *
 * <p>NFR-028 makes credentials an implementation concern, so the application
 * layer states only what it needs — store, retrieve, report, forget — and knows
 * nothing about encryption, files or key management. The implementation lives in
 * tower-config, which an architecture test makes the only module allowed to
 * touch credential material.
 *
 * <p>Nothing here names a Connector type. The application layer may not depend
 * on {@code dev.tower.connector..} (CM-03, ADR-003, enforced by
 * {@code domain_and_application_do_not_depend_on_connectors}), so a secret
 * crosses to a Connector by way of the Collector rather than directly.
 *
 * <p>A credential is identified by the Connector that uses it and the target it
 * authenticates against, which is the same target an External Binding names.
 * That avoids inventing a separate "connection" concept for the user to manage.
 *
 * <p>Secrets are {@code char[]} rather than {@code String} so a caller can clear
 * them after use. This is a modest measure — it shortens the window in which a
 * secret sits in the heap, and does not pretend to defeat anyone with a debugger
 * attached to the process.
 */
public interface ConnectorCredentialsPort {

    /** Stores a secret, replacing any secret already held for the same pair. */
    void store(String connectorId, String target, char[] secret);

    /**
     * The stored secret, for a Collector about to invoke a Connector.
     *
     * <p>The caller is expected to clear the array once the Connector has used
     * it. Empty when nothing is configured, which is a normal state rather than
     * an error: it means the user has bound an Environment but not yet supplied
     * a token.
     */
    Optional<char[]> secretFor(String connectorId, String target);

    /**
     * Whether a secret is configured, and when it was last written.
     *
     * <p>This is what the user interface is allowed to see. The plan's rule that
     * credentials are write-only through the API is why no method on this port
     * returns a stored value to a caller that only wants to render a screen.
     */
    CredentialStatus status(String connectorId, String target);

    /** Removes the stored secret. Silent when nothing was stored. */
    void forget(String connectorId, String target);
}
