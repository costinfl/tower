package dev.tower.application.port.out;

import java.time.Instant;
import java.util.List;

import dev.tower.application.sync.ConnectionTest;

/**
 * Confirms artifacts against a repository, on the application layer's terms
 * (ADR-021, FR-082).
 *
 * <p>The second Collector that observes nothing, after {@link WorkItemCollector},
 * and the first that also proposes nothing. The deployment and pipeline
 * Collectors append Observations; the source control Collector proposes versions
 * a user may register; the work item Collector shows a tracker's current wording
 * beside an accepted one. This one only answers whether some bytes are where a
 * version says they should be. Nothing it returns is persisted (FR-084).
 *
 * <p>Names no Connector type, and cannot: the application layer may not depend on
 * {@code dev.tower.connector..} (CM-03, ADR-003). An interface carrying
 * {@code StoredArtifact} would put a vendor-facing shape into the layer that has
 * to stay vendor-neutral.
 *
 * <p>Takes the repository's address per call rather than looking one up, unlike
 * {@link WorkItemCollector}. A team has one issue tracker; it may well have an
 * image repository and a chart repository at different addresses, and which one
 * is meant is decided by the coordinate binding the caller is confirming.
 */
public interface ArtifactCollector {

    /** Which repository system this Collector reads, e.g. "artifactory". */
    String connectorId();

    /**
     * Which of these coordinates the repository holds.
     *
     * <p>A coordinate the repository does not have is omitted rather than
     * reported as an error (FR-085). An artifact that is not there yet is an
     * ordinary situation — a build that has not run, a chart not yet published, a
     * template with a typo in it — and the caller tells that apart from an
     * unreachable repository by whether this returned or threw.
     *
     * @throws RuntimeException when the repository could not be read at all
     */
    List<ConfirmedArtifact> read(String system, List<String> coordinates);

    /** Confirms the repository answers, without modifying it (FR-061). */
    ConnectionTest checkConnection(String system);

    /**
     * @param digest    the repository's own immutable identity for these bytes,
     *                  never normalised, and blank where the repository gives
     *                  none. The field this record exists for: a coordinate names
     *                  a moving target, a digest names bytes
     * @param storedAt  when the repository says it received this, which is
     *                  neither when it was built nor when it was deployed
     * @param sizeBytes how large, or zero where the repository does not say
     */
    record ConfirmedArtifact(String coordinate, String digest, Instant storedAt,
                             long sizeBytes, String url) {
    }
}
