package dev.tower.application.port.in;

import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.handover.Handover;
import dev.tower.domain.handover.HandoverRevision;
import dev.tower.domain.iteration.IterationId;
import dev.tower.domain.promotionpath.PromotionPathId;
import dev.tower.domain.releasepack.ReleasePack;
import dev.tower.domain.releasepack.ReleasePackId;

import java.time.Instant;
import java.util.List;

/**
 * Inbound port for Release Packs (issues #15 to #19).
 *
 * <p>The Release Pack is the central business concept (ADR-004), so most of
 * Tower's Milestone 1 capability hangs off this port.
 */
public interface ReleasePackUseCases {

    ReleasePack create(CreateReleasePack command);

    ReleasePack updateMetadata(UpdateReleasePack command);

    /** FR-005. Pins a specific Promotion Path version (ADR-007). */
    ReleasePack assignPromotionPath(AssignPromotionPath command);

    ReleasePack clearPromotionPath(ReleasePackId id);

    /** FR-003. The owning Application is resolved from the Version. */
    ReleasePack addApplicationVersion(ReleasePackId id, ApplicationVersionId versionId);

    /** FR-004. */
    ReleasePack removeApplicationVersion(ReleasePackId id, ApplicationVersionId versionId);

    /** FR-006. */
    ReleasePack updateHandover(ReleasePackId id, Handover handover);

    /**
     * Every version this Release Pack's Handover has had, newest first (ADR-016).
     *
     * <p>Read-only, and there is deliberately no counterpart that restores one. A
     * revision records what a team was told; putting an old one back is an edit
     * like any other, and it appends a new revision rather than rewriting
     * history.
     */
    List<HandoverRevision> handoverHistory(ReleasePackId id);

    ReleasePack startIteration(StartIteration command);

    ReleasePack completeIteration(ReleasePackId id, IterationId iterationId, Instant completedAt);

    ReleasePack reopenIteration(ReleasePackId id, IterationId iterationId);

    ReleasePack updateIterationNotes(ReleasePackId id, IterationId iterationId, String notes);

    ReleasePack removeIteration(ReleasePackId id, IterationId iterationId);

    /** ADR-008. An explicit lifecycle decision, not a derived state. */
    ReleasePack archive(ReleasePackId id);

    ReleasePack restore(ReleasePackId id);

    void delete(ReleasePackId id);

    List<ReleasePack> list();

    ReleasePack get(ReleasePackId id);

    record CreateReleasePack(String name, String description) {}

    record UpdateReleasePack(ReleasePackId id, String name, String description) {}

    record AssignPromotionPath(ReleasePackId id, PromotionPathId pathId, int versionNumber) {}

    record StartIteration(ReleasePackId id, String name, Instant startedAt, String notes) {}
}
