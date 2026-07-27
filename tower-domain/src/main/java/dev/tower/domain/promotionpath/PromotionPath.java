package dev.tower.domain.promotionpath;

import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.shared.DomainConflictException;
import dev.tower.domain.shared.DomainException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A user-defined ordered sequence of Environments through which a Release Pack
 * progresses.
 *
 * <p>A Promotion Path is a stable identity carrying an ordered series of
 * immutable versions (ADR-007). Editing appends a version rather than changing
 * an existing one, so history stays truthful: a Release Pack that referenced
 * version 1 still reports the topology it actually followed after the team
 * publishes version 2.
 *
 * <p>Paths are static in the sense BR-03 intends — Release Packs move through
 * Promotion Paths, and Promotion Paths never move. That is a statement about
 * movement, not about editability, and ADR-007 replaces SM-01 accordingly.
 *
 * <p>Tower records Promotion Paths and never executes a promotion (ADR-001).
 */
public final class PromotionPath {

    public static final int NAME_MAX_LENGTH = 100;

    private final PromotionPathId id;
    private final String name;
    private final List<PromotionPathVersion> versions;
    private final boolean archived;

    private PromotionPath(PromotionPathId id, String name, List<PromotionPathVersion> versions, boolean archived) {
        DomainException.require(id != null, "Promotion Path id is required.");
        DomainException.require(name != null && !name.isBlank(), "Promotion Path name is required.");
        DomainException.require(name.length() <= NAME_MAX_LENGTH,
                "Promotion Path name must be at most " + NAME_MAX_LENGTH + " characters.");
        DomainException.require(versions != null && !versions.isEmpty(),
                "A Promotion Path must have at least one version.");
        this.id = id;
        this.name = name.trim();
        this.versions = List.copyOf(versions);
        this.archived = archived;
    }

    /** Creates a new Promotion Path with its first version. */
    public static PromotionPath create(String name, List<EnvironmentId> environments, Instant now) {
        PromotionPathVersion first = new PromotionPathVersion(1, environments, now);
        return new PromotionPath(PromotionPathId.newId(), name, List.of(first), false);
    }

    /** Reconstitutes a Promotion Path from storage. */
    public static PromotionPath reconstitute(PromotionPathId id, String name,
                                             List<PromotionPathVersion> versions, boolean archived) {
        List<PromotionPathVersion> ordered = new ArrayList<>(versions);
        ordered.sort((a, b) -> Integer.compare(a.number(), b.number()));
        for (int i = 0; i < ordered.size(); i++) {
            DomainException.require(ordered.get(i).number() == i + 1,
                    "Promotion Path versions must be numbered consecutively from 1.");
        }
        return new PromotionPath(id, name, ordered, archived);
    }

    /**
     * Publishes a new version with a different sequence of Environments.
     *
     * <p>Existing versions are untouched, so Release Packs referencing them keep
     * reporting the topology they followed (ADR-007, SM-06).
     */
    public PromotionPath withNewVersion(List<EnvironmentId> environments, Instant now) {
        DomainConflictException.requireNoConflict(!archived,
                "An archived Promotion Path cannot receive new versions.");
        PromotionPathVersion next = new PromotionPathVersion(versions.size() + 1, environments, now);
        List<PromotionPathVersion> updated = new ArrayList<>(versions);
        updated.add(next);
        return new PromotionPath(id, name, updated, false);
    }

    public PromotionPath rename(String newName) {
        DomainConflictException.requireNoConflict(!archived,
                "An archived Promotion Path cannot be renamed.");
        return new PromotionPath(id, newName, versions, false);
    }

    /**
     * Archives the path.
     *
     * <p>ADR-007 replaces deletion with archival whenever a Release Pack
     * references any version. An archived path stays readable for historical
     * purposes but accepts no new versions and cannot be assigned to new Release
     * Packs. Whether any pack references it is a question for the application
     * layer, which owns that lookup.
     */
    public PromotionPath archive() {
        return archived ? this : new PromotionPath(id, name, versions, true);
    }

    public PromotionPath restore() {
        return archived ? new PromotionPath(id, name, versions, false) : this;
    }

    /** The most recently published version, which new Release Packs receive. */
    public PromotionPathVersion currentVersion() {
        return versions.get(versions.size() - 1);
    }

    public Optional<PromotionPathVersion> version(int number) {
        return versions.stream().filter(v -> v.number() == number).findFirst();
    }

    /** True when any version references the Environment (ADR-005 convergence). */
    public boolean referencesEnvironment(EnvironmentId environment) {
        return versions.stream().anyMatch(v -> v.contains(environment));
    }

    public PromotionPathId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public List<PromotionPathVersion> versions() {
        return versions;
    }

    public boolean isArchived() {
        return archived;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PromotionPath other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "PromotionPath[" + id + ", name=" + name + ", versions=" + versions.size()
                + (archived ? ", archived" : "") + "]";
    }
}
