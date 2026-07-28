package dev.tower.api.observation;

import dev.tower.domain.application.ApplicationVersion;

/**
 * Resolved reference to an Application Version, embedded in {@link ObservationView} so the Viewer
 * never needs a follow-up round trip to show which version was observed (FR-012, FR-013,
 * NFR-013). {@link EnvironmentStateView} uses the fuller {@link ApplicationVersionDetailView}
 * instead, since deployment detail (branch, tag, commit, build) matters there.
 */
public record ApplicationVersionRefView(String id, String version) {

    public static ApplicationVersionRefView from(ApplicationVersion version) {
        return new ApplicationVersionRefView(version.id().toString(), version.version());
    }
}
