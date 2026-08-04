package dev.tower.application.port.out;

import java.util.List;
import java.util.Optional;

import dev.tower.domain.application.AcceptedArtifact;
import dev.tower.domain.application.ApplicationVersionId;

/**
 * Outbound port for accepted artifact digests (ADR-021, FR-086).
 *
 * <p>The only thing an Artifact Repository Connector leads to that is stored at
 * all. Everything else it learns is shown and thrown away (FR-084); this is
 * different because a person stated it, which is what makes it User-Owned
 * Information rather than a fact Tower read.
 *
 * <p>Offers replace and delete, unlike {@link ObservationRepository}. Accepting a
 * newer digest for the same kind is an ordinary correction rather than a second
 * historical fact — a re-pushed tag is exactly the situation this exists to make
 * visible, and the record of what a release shipped is the accepted digest, not
 * the sequence of acceptances.
 */
public interface AcceptedArtifactRepository {

    AcceptedArtifact save(AcceptedArtifact accepted);

    Optional<AcceptedArtifact> find(ApplicationVersionId applicationVersionId, String kind);

    List<AcceptedArtifact> findAllFor(ApplicationVersionId applicationVersionId);

    /**
     * Every accepted digest for a set of versions, which is what a release
     * document reads.
     *
     * <p>Taken in one call rather than one per version, because a document
     * assembles a whole Release Pack and NFR-025's guarantee is about what it
     * prints rather than how many queries it took to get there.
     */
    List<AcceptedArtifact> findAllFor(List<ApplicationVersionId> applicationVersionIds);

    void delete(ApplicationVersionId applicationVersionId, String kind);
}
