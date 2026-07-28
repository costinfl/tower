package dev.tower.api.observation;

import dev.tower.domain.application.Application;

/**
 * Resolved reference to an Application, embedded in Observation-derived views so the Viewer never
 * needs a follow-up round trip to show its name (FR-012, FR-013, NFR-013).
 */
public record ApplicationRefView(String id, String name) {

    public static ApplicationRefView from(Application application) {
        return new ApplicationRefView(application.id().toString(), application.name());
    }
}
