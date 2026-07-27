package dev.tower.domain.application;

import dev.tower.domain.shared.DomainException;

import java.util.UUID;

/** Stable identity of an Application Version. */
public record ApplicationVersionId(UUID value) {

    public ApplicationVersionId {
        DomainException.require(value != null, "Application Version id is required.");
    }

    public static ApplicationVersionId newId() {
        return new ApplicationVersionId(UUID.randomUUID());
    }

    public static ApplicationVersionId of(String value) {
        try {
            return new ApplicationVersionId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DomainException("Application Version id is not a valid identifier: " + value);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
