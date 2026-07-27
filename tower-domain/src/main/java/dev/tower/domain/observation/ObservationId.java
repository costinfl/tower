package dev.tower.domain.observation;

import dev.tower.domain.shared.DomainException;

import java.util.UUID;

/** Stable identity of an Observation. */
public record ObservationId(UUID value) {

    public ObservationId {
        DomainException.require(value != null, "Observation id is required.");
    }

    public static ObservationId newId() {
        return new ObservationId(UUID.randomUUID());
    }

    public static ObservationId of(String value) {
        try {
            return new ObservationId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DomainException("Observation id is not a valid identifier: " + value);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
