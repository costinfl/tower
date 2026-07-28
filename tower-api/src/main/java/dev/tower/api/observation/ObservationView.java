package dev.tower.api.observation;

import java.time.Instant;

import dev.tower.domain.application.Application;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.environment.Environment;
import dev.tower.domain.observation.Observation;

/**
 * API-layer representation of an Observation (issues #21 to #24). Self-sufficient for the UI:
 * environment, application and applicationVersion carry resolved names rather than bare ids, and
 * source is always present, so the Viewer never needs a follow-up round trip to explain a fact
 * (FR-012, FR-013, NFR-013). Never serialise {@link Observation} directly.
 */
public record ObservationView(
        String id, EnvironmentRefView environment, ApplicationRefView application,
        ApplicationVersionRefView applicationVersion, Instant observedAt, SourceView source) {

    public static ObservationView from(Observation observation, Environment environment, Application application,
                                       ApplicationVersion applicationVersion) {
        return new ObservationView(
                observation.id().toString(),
                EnvironmentRefView.from(environment),
                ApplicationRefView.from(application),
                ApplicationVersionRefView.from(applicationVersion),
                observation.observedAt(),
                SourceView.from(observation.source()));
    }
}
