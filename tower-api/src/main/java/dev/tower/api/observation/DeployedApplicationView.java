package dev.tower.api.observation;

import java.time.Instant;

import dev.tower.domain.application.Application;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.observation.EnvironmentState;

/**
 * One Application currently deployed in an Environment, resolved for display. The originating
 * {@code observationId} is carried through so the fact behind this row stays traceable (FR-013,
 * FR-031, NFR-011, NFR-013), alongside the timestamp and source FR-012 and FR-013 require.
 */
public record DeployedApplicationView(
        ApplicationRefView application, ApplicationVersionDetailView applicationVersion,
        Instant observedAt, SourceView source, String observationId) {

    public static DeployedApplicationView from(
            EnvironmentState.DeployedApplication deployed, Application application, ApplicationVersion version) {
        return new DeployedApplicationView(
                ApplicationRefView.from(application),
                ApplicationVersionDetailView.from(version),
                deployed.observedAt(),
                SourceView.from(deployed.source()),
                deployed.observationId().toString());
    }
}
