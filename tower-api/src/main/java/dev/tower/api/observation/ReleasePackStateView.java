package dev.tower.api.observation;

import java.util.List;

import dev.tower.domain.releasepack.ReleasePackState;

/**
 * Where a Release Pack has been observed (ADR-008, FR-016, FR-034).
 *
 * <p>{@code state} is the {@link ReleasePackState} enum name - the highest Stage at which any of
 * the pack's contents has been observed. It is deliberately a separate field from, and never
 * merged with, the pack's own {@code archived} flag: archived is Intent, decided by a person,
 * while state is Observation, derived from what was seen (SM-02, ReleasePackState javadoc). A
 * pack can be both PRODUCTION and archived at once, or PLANNED and archived, and this view lets
 * both be true simultaneously rather than collapsing them into one lifecycle.
 */
public record ReleasePackStateView(String state, List<PackSightingView> sightings) {
}
