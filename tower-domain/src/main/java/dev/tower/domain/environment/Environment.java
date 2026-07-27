package dev.tower.domain.environment;

import dev.tower.domain.shared.DomainException;

/**
 * A deployment destination where Applications execute.
 *
 * <p>An Environment does not belong to a Promotion Path. ADR-005 makes the
 * relationship many-to-many: Promotion Paths reference Environments they do not
 * own, and several paths may converge on the same Environment. That is why this
 * type holds no reference back to any path.
 *
 * <p>An Environment owns only its identity, its name and its {@link Stage}. It
 * never owns Applications; the deployment state observed within it is derived
 * from Observations (Domain-Model.md).
 */
public record Environment(EnvironmentId id, String name, Stage stage) {

    public static final int NAME_MAX_LENGTH = 100;

    public Environment {
        DomainException.require(id != null, "Environment id is required.");
        DomainException.require(name != null && !name.isBlank(), "Environment name is required.");
        DomainException.require(name.length() <= NAME_MAX_LENGTH,
                "Environment name must be at most " + NAME_MAX_LENGTH + " characters.");
        DomainException.require(stage != null,
                "Environment stage is required. ADR-008 derives Release Pack state from it.");
        name = name.trim();
    }

    public static Environment create(String name, Stage stage) {
        return new Environment(EnvironmentId.newId(), name, stage);
    }

    public Environment rename(String newName) {
        return new Environment(id, newName, stage);
    }

    public Environment reclassify(Stage newStage) {
        return new Environment(id, name, newStage);
    }
}
