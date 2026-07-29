package dev.tower.application.sync;

import java.util.UUID;

import dev.tower.application.service.InvalidRequestException;

/**
 * Stable identity of a {@link SyncRun}.
 *
 * <p>Mirrors the domain id types in shape but deliberately not in package. A
 * Sync Run is operational telemetry rather than a business fact (ADR-011), so
 * its identity belongs to the application layer — and being here it cannot leak
 * into the Domain Model, which may depend only on the Java standard library.
 */
public record SyncRunId(UUID value) {

    public SyncRunId {
        if (value == null) {
            throw new InvalidRequestException("Sync Run id is required.");
        }
    }

    public static SyncRunId newId() {
        return new SyncRunId(UUID.randomUUID());
    }

    public static SyncRunId of(String value) {
        try {
            return new SyncRunId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidRequestException("Sync Run id is not a valid identifier: " + value);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
