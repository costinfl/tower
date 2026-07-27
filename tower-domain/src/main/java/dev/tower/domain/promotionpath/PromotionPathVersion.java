package dev.tower.domain.promotionpath;

import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.shared.DomainException;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One immutable version of a Promotion Path: the ordered sequence of
 * Environments in effect at a point in time.
 *
 * <p>ADR-007 makes Promotion Paths editable by versioning them rather than
 * mutating them. A Release Pack references a specific version, so a pack that
 * has already progressed continues to report the topology it actually followed
 * even after the team evolves the path. That is why this type is immutable and
 * why nothing here offers a setter.
 *
 * <p>ADR-005: the version holds Environment <em>references</em>. It does not own
 * the Environments, and the same Environment may appear in any number of other
 * Promotion Paths.
 */
public record PromotionPathVersion(int number, List<EnvironmentId> environments, Instant createdAt) {

    public PromotionPathVersion {
        DomainException.require(number >= 1, "Promotion Path version number starts at 1.");
        DomainException.require(environments != null && !environments.isEmpty(),
                "A Promotion Path version must contain at least one Environment.");
        DomainException.require(createdAt != null, "Promotion Path version requires a creation timestamp.");

        // An Environment appearing twice in one path would make "the position of
        // a Release Pack within its path" ambiguous, so it is rejected. Note this
        // constrains one version only — reuse across different paths is expected
        // and is exactly what ADR-005 permits.
        Set<EnvironmentId> seen = new HashSet<>();
        for (EnvironmentId environment : environments) {
            DomainException.require(environment != null, "A Promotion Path may not reference a null Environment.");
            DomainException.require(seen.add(environment),
                    "Environment " + environment + " appears more than once in this Promotion Path.");
        }

        environments = List.copyOf(environments);
    }

    /** The first Environment in the sequence. */
    public EnvironmentId first() {
        return environments.get(0);
    }

    /** The final Environment in the sequence. */
    public EnvironmentId last() {
        return environments.get(environments.size() - 1);
    }

    public int length() {
        return environments.size();
    }

    public boolean contains(EnvironmentId environment) {
        return environments.contains(environment);
    }

    /**
     * Zero-based position of an Environment, or -1 when absent.
     *
     * <p>Position describes progress through <em>this</em> path only. ADR-008
     * rejected position as the basis for Release Pack state precisely because it
     * is not comparable across paths of different lengths.
     */
    public int positionOf(EnvironmentId environment) {
        return environments.indexOf(environment);
    }
}
