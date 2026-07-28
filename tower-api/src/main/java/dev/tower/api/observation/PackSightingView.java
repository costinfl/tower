package dev.tower.api.observation;

import java.time.Instant;

import dev.tower.application.port.in.ObservationUseCases.PackSighting;
import dev.tower.domain.application.Application;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.environment.Environment;

/**
 * One Environment in which a Release Pack's content was seen (ADR-008, FR-016, FR-034), resolved
 * for display.
 *
 * <p>{@link PackSighting} carries only an Environment id/name pair and an Application Version id,
 * not the originating Observation - a Release Pack's contents may be observed many times over in
 * one Environment, and the state derivation this backs (ReleasePackState.derive) only needs the
 * latest fact per Stage, not a full provenance trail. Observation history remains available in
 * full via GET /api/environments/{id}/observations for whoever needs the source of a specific
 * fact.
 */
public record PackSightingView(
        EnvironmentRefView environment, ApplicationRefView application,
        ApplicationVersionRefView applicationVersion, Instant observedAt) {

    public static PackSightingView from(PackSighting sighting, Environment environment, Application application,
                                        ApplicationVersion applicationVersion) {
        return new PackSightingView(
                EnvironmentRefView.from(environment),
                ApplicationRefView.from(application),
                ApplicationVersionRefView.from(applicationVersion),
                sighting.observedAt());
    }
}
