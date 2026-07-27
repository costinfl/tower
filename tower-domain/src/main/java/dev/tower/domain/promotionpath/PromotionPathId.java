package dev.tower.domain.promotionpath;

import dev.tower.domain.shared.DomainException;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable identity of a Promotion Path.
 *
 * <p>ADR-007 separates identity from version: this id survives every edit, while
 * each edit publishes a new {@link PromotionPathVersion}.
 */
public record PromotionPathId(UUID value) {

    public PromotionPathId {
        DomainException.require(value != null, "Promotion Path id is required.");
    }

    public static PromotionPathId newId() {
        return new PromotionPathId(UUID.randomUUID());
    }

    public static PromotionPathId of(String value) {
        Objects.requireNonNull(value, "Promotion Path id is required.");
        try {
            return new PromotionPathId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Promotion Path id is not a valid identifier: " + value);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
