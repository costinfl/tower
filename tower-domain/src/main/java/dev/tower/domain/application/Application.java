package dev.tower.domain.application;

import dev.tower.domain.shared.DomainException;

/**
 * A deployable software component with its own development and version
 * lifecycle (Glossary.md).
 *
 * <p>An Application maintains identity across versions and is the logical owner
 * of its Application Versions. It may belong to several Release Packs over time
 * and may run in several Environments at once.
 */
public record Application(ApplicationId id, String name, String description) {

    public static final int NAME_MAX_LENGTH = 150;

    public Application {
        DomainException.require(id != null, "Application id is required.");
        DomainException.require(name != null && !name.isBlank(), "Application name is required.");
        DomainException.require(name.length() <= NAME_MAX_LENGTH,
                "Application name must be at most " + NAME_MAX_LENGTH + " characters.");
        name = name.trim();
        description = description == null ? "" : description.trim();
    }

    public static Application create(String name, String description) {
        return new Application(ApplicationId.newId(), name, description);
    }

    public Application update(String newName, String newDescription) {
        return new Application(id, newName, newDescription);
    }
}
