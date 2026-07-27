package dev.tower.domain.environment;

import dev.tower.domain.shared.DomainException;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of an Environment. */
public record EnvironmentId(UUID value) {

    public EnvironmentId {
        DomainException.require(value != null, "Environment id is required.");
    }

    public static EnvironmentId newId() {
        return new EnvironmentId(UUID.randomUUID());
    }

    public static EnvironmentId of(String value) {
        Objects.requireNonNull(value, "Environment id is required.");
        try {
            return new EnvironmentId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Environment id is not a valid identifier: " + value);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
