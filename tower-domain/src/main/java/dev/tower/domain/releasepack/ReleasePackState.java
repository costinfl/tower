package dev.tower.domain.releasepack;

import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.environment.Stage;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Where a Release Pack has been observed, derived from Observations (ADR-008).
 *
 * <p>SM-02: state is derived and never manually assigned. Every value here
 * therefore answers a question about what was <em>seen</em>. Whether a team has
 * stopped working the release is a different question, answered by the pack's
 * archived flag, which is Intent rather than Observation — the two are
 * deliberately not members of the same enum.
 *
 * <p>State is derived from the {@link Stage} of the Environment an Application
 * Version was observed in, not from the Environment's name and not from its
 * position in a Promotion Path. Names are an organizational convention that
 * changes; position is not comparable across paths of different lengths.
 */
public enum ReleasePackState {

    /** No Application Version in the pack has been observed anywhere (SM-05). */
    PLANNED,

    DEVELOPMENT,
    VALIDATION,
    PRE_PRODUCTION,
    PRODUCTION;

    /** The state corresponding to having been observed at a given Stage. */
    public static ReleasePackState of(Stage stage) {
        return switch (stage) {
            case DEVELOPMENT -> DEVELOPMENT;
            case VALIDATION -> VALIDATION;
            case PRE_PRODUCTION -> PRE_PRODUCTION;
            case PRODUCTION -> PRODUCTION;
        };
    }

    /**
     * Derives the state of a Release Pack from where its contents were seen.
     *
     * <p>The rule is the highest Stage at which <em>any</em> Application Version
     * belonging to the pack has been observed. Because it is driven by Stage
     * rather than by path topology, a Promotion Path containing no
     * pre-production Environment progresses straight from Validation to
     * Production — which is what makes the Hotfix path in Scenarios.md legal
     * without any special handling. That case was impossible under the original
     * State Model, and fixing it is why ADR-008 exists.
     *
     * <p>Sightings of versions the pack does not contain are ignored, so a
     * caller may pass everything observed in an Environment without filtering
     * it down first.
     *
     * @param contents  Application Versions belonging to the pack
     * @param sightings where those versions have been observed
     */
    public static ReleasePackState derive(Set<ApplicationVersionId> contents, Collection<Sighting> sightings) {
        if (contents == null || contents.isEmpty() || sightings == null) {
            return PLANNED;
        }
        Optional<Stage> highest = sightings.stream()
                .filter(sighting -> sighting != null && contents.contains(sighting.applicationVersionId()))
                .map(Sighting::stage)
                .reduce(Stage::max);

        return highest.map(ReleasePackState::of).orElse(PLANNED);
    }

    /** One Application Version observed at an Environment of a given Stage. */
    public record Sighting(ApplicationVersionId applicationVersionId, Stage stage) {
    }
}
