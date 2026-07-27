package dev.tower.domain.application;

import dev.tower.domain.shared.DomainException;

import java.util.UUID;

/** Stable identity of an Application. */
public record ApplicationId(UUID value) {

    public ApplicationId {
        DomainException.require(value != null, "Application id is required.");
    }

    public static ApplicationId newId() {
        return new ApplicationId(UUID.randomUUID());
    }

    public static ApplicationId of(String value) {
        try {
            return new ApplicationId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DomainException("Application id is not a valid identifier: " + value);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
