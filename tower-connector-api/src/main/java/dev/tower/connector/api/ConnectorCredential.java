package dev.tower.connector.api;

import java.util.Arrays;

/**
 * The secret a Connector presents to an External System.
 *
 * <p>Exists so that a Connector never reaches for a credential itself. The
 * application layer owns the credentials port but may not name a Connector type
 * (CM-03, ADR-003, enforced by
 * {@code domain_and_application_do_not_depend_on_connectors}), so the Collector
 * reads the secret through that port and hands it over as this.
 *
 * <p>That keeps a Connector stateless, unaware of where secrets live, and
 * testable without a credential store — which is the arrangement
 * Implementation-Plan.md describes when it says connector modules never touch
 * storage.
 *
 * <p>Holds {@code char[]} rather than {@code String} so the caller can clear it
 * after the call. {@link #toString} never renders it.
 */
public final class ConnectorCredential {

    private final char[] token;
    private boolean cleared;

    private ConnectorCredential(char[] token) {
        this.token = token;
    }

    /**
     * A bearer token, as an OpenShift or Kubernetes login command yields.
     *
     * <p>The array is copied, so clearing the caller's copy does not empty this
     * one mid-request.
     */
    public static ConnectorCredential bearerToken(char[] token) {
        if (token == null || token.length == 0) {
            throw new ConnectorException("A bearer token credential must carry a token.");
        }
        return new ConnectorCredential(Arrays.copyOf(token, token.length));
    }

    /**
     * No credential at all, for a target that needs none.
     *
     * <p>Distinct from a missing credential the user forgot to supply: this says
     * the Connector should present nothing, and lets a Connector that requires
     * authentication fail with a message naming that rather than a null.
     */
    public static ConnectorCredential none() {
        return new ConnectorCredential(new char[0]);
    }

    public boolean isPresent() {
        return !cleared && token.length > 0;
    }

    /**
     * The token, copied. Callers should clear what they receive.
     *
     * @throws ConnectorException once {@link #clear()} has been called. Returning
     *         the zero-filled array instead would let a Connector present a row
     *         of NUL bytes as a bearer token and report an authentication failure
     *         rather than the programming error that caused it.
     */
    public char[] token() {
        if (cleared) {
            throw new ConnectorException("This credential has been cleared and can no longer be used.");
        }
        return Arrays.copyOf(token, token.length);
    }

    /** Clears the held token. The credential is unusable afterwards. */
    public void clear() {
        Arrays.fill(token, '\0');
        cleared = true;
    }

    @Override
    public String toString() {
        return "ConnectorCredential[" + (isPresent() ? "present" : "absent") + "]";
    }
}
