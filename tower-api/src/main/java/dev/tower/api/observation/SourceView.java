package dev.tower.api.observation;

import dev.tower.domain.observation.ObservationSource;

/**
 * API-layer representation of {@link ObservationSource}. FR-013, FR-022 and NFR-012 require every
 * displayed fact to be traceable to where it came from, so no Observation-derived view omits this.
 *
 * @param manual true when {@link ObservationSource#isManual()}, so the Viewer can flag a fact
 *               entered by a person without duplicating the {@code "manual"} string literal
 */
public record SourceView(String collector, String actor, String originInstance, boolean manual) {

    public static SourceView from(ObservationSource source) {
        return new SourceView(source.collector(), source.actor(), source.originInstance(), source.isManual());
    }
}
