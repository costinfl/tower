package dev.tower.domain.releasepack;

import dev.tower.domain.shared.DomainException;

import java.util.UUID;

/** Stable identity of a Release Pack. */
public record ReleasePackId(UUID value) {

    public ReleasePackId {
        DomainException.require(value != null, "Release Pack id is required.");
    }

    public static ReleasePackId newId() {
        return new ReleasePackId(UUID.randomUUID());
    }

    public static ReleasePackId of(String value) {
        try {
            return new ReleasePackId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DomainException("Release Pack id is not a valid identifier: " + value);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
