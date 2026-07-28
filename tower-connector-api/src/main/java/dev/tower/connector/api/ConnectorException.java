package dev.tower.connector.api;

/**
 * Signals that a Connector could not retrieve what it was asked for.
 *
 * <p>Connector-Model.md, Failure Handling: a Connector failure shall not
 * invalidate the Canonical Model. This exception is therefore a report, not a
 * corruption — the caller records the failed run (ADR-011) and leaves every
 * Observation already held exactly as it was.
 *
 * <p>Unchecked, matching {@code DomainException}. The Collector catches it at
 * one place, where it builds the Sync Run, and nowhere else needs to know.
 */
public class ConnectorException extends RuntimeException {

    public ConnectorException(String message) {
        super(message);
    }

    public ConnectorException(String message, Throwable cause) {
        super(message, cause);
    }
}
