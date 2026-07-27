package dev.tower.api.application;

import dev.tower.domain.application.Application;

/** API-layer representation of an Application. Never serialise {@link Application} directly. */
public record ApplicationResponse(String id, String name, String description) {

    public static ApplicationResponse from(Application application) {
        return new ApplicationResponse(application.id().toString(), application.name(), application.description());
    }
}
