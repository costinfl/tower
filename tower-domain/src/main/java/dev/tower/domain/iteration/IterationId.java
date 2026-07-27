package dev.tower.domain.iteration;

import dev.tower.domain.shared.DomainException;

import java.util.UUID;

/** Stable identity of a validation Iteration. */
public record IterationId(UUID value) {

    public IterationId {
        DomainException.require(value != null, "Iteration id is required.");
    }

    public static IterationId newId() {
        return new IterationId(UUID.randomUUID());
    }

    public static IterationId of(String value) {
        try {
            return new IterationId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DomainException("Iteration id is not a valid identifier: " + value);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
