package dev.tower.application.port.out;

import java.util.List;
import java.util.Optional;

import dev.tower.domain.handover.HandoverRevision;
import dev.tower.domain.releasepack.ReleasePackId;

/**
 * Outbound port for Handover history (IA-02, ADR-016).
 *
 * <p>Append-only, and shaped like {@link ObservationRepository} rather than like
 * the binding repositories: there is no update and no delete, because a revision
 * records what a team was told and that does not stop having been true.
 *
 * <p>The absence of those methods is the enforcement. A repository that offered
 * a delete would leave the rule to whoever remembered it.
 */
public interface HandoverRevisionRepository {

    HandoverRevision append(HandoverRevision revision);

    /** Newest first, which is the order the question is usually asked in. */
    List<HandoverRevision> findAllByReleasePack(ReleasePackId releasePackId);

    Optional<HandoverRevision> findByReleasePackAndNumber(ReleasePackId releasePackId, int revisionNumber);

    /**
     * The highest revision number recorded, or 0 when there is none.
     *
     * <p>Asked rather than counted, so a numbering gap could never renumber
     * existing revisions — the numbers are how a revision is referred to.
     */
    int highestRevisionNumber(ReleasePackId releasePackId);
}
