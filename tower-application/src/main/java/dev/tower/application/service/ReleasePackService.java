package dev.tower.application.service;

import dev.tower.application.port.in.ReleasePackUseCases;
import dev.tower.application.port.out.ApplicationVersionRepository;
import dev.tower.application.port.out.PromotionPathRepository;
import dev.tower.application.port.out.HandoverRevisionRepository;
import dev.tower.application.port.out.ReleasePackRepository;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.handover.Handover;
import dev.tower.domain.handover.HandoverRevision;
import dev.tower.domain.iteration.Iteration;
import dev.tower.domain.iteration.IterationId;
import dev.tower.domain.promotionpath.PromotionPath;
import dev.tower.domain.promotionpath.PromotionPathId;
import dev.tower.domain.releasepack.ReleasePack;
import dev.tower.domain.releasepack.ReleasePackId;
import dev.tower.domain.releasepack.WorkItemReference;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Release Pack use cases (issues #15 to #19).
 *
 * <p>Carries no framework annotation; Spring wiring lives in tower-api.
 */
public class ReleasePackService implements ReleasePackUseCases {

    private final ReleasePackRepository releasePacks;
    private final ApplicationVersionRepository versions;
    private final PromotionPathRepository promotionPaths;
    private final HandoverRevisionRepository handoverRevisions;
    private final Clock clock;

    public ReleasePackService(ReleasePackRepository releasePacks,
                              ApplicationVersionRepository versions,
                              PromotionPathRepository promotionPaths,
                              HandoverRevisionRepository handoverRevisions,
                              Clock clock) {
        this.releasePacks = Objects.requireNonNull(releasePacks);
        this.versions = Objects.requireNonNull(versions);
        this.promotionPaths = Objects.requireNonNull(promotionPaths);
        this.handoverRevisions = Objects.requireNonNull(handoverRevisions);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public ReleasePack create(CreateReleasePack command) {
        String name = command.name() == null ? "" : command.name().trim();
        if (releasePacks.existsByNameIgnoringCase(name)) {
            throw new ApplicationException("A Release Pack named '" + name + "' already exists.");
        }
        return releasePacks.save(ReleasePack.create(command.name(), command.description()));
    }

    @Override
    public ReleasePack updateMetadata(UpdateReleasePack command) {
        ReleasePack pack = get(command.id());
        String name = command.name() == null ? "" : command.name().trim();
        if (!name.equalsIgnoreCase(pack.name()) && releasePacks.existsByNameIgnoringCase(name)) {
            throw new ApplicationException("A Release Pack named '" + name + "' already exists.");
        }
        return releasePacks.save(pack.updateMetadata(command.name(), command.description()));
    }

    /**
     * FR-005, ADR-007.
     *
     * <p>The requested version must exist, because pinning a version that was
     * never published would leave the pack describing a topology nobody defined.
     */
    @Override
    public ReleasePack assignPromotionPath(AssignPromotionPath command) {
        ReleasePack pack = get(command.id());
        PromotionPath path = promotionPaths.findById(command.pathId())
                .orElseThrow(() -> new NotFoundException(
                        "Promotion Path " + command.pathId() + " does not exist."));

        if (path.version(command.versionNumber()).isEmpty()) {
            throw new ApplicationException("Promotion Path '" + path.name() + "' has no version "
                    + command.versionNumber() + ". It currently has "
                    + path.versions().size() + " version(s).");
        }
        if (path.isArchived()) {
            throw new ApplicationException("Promotion Path '" + path.name()
                    + "' is archived and cannot be assigned to a Release Pack.");
        }
        return releasePacks.save(pack.assignPromotionPath(command.pathId(), command.versionNumber()));
    }

    @Override
    public ReleasePack clearPromotionPath(ReleasePackId id) {
        return releasePacks.save(get(id).clearPromotionPath());
    }

    /**
     * FR-003.
     *
     * <p>The owning Application is resolved here rather than supplied by the
     * caller, so the pack's one-version-per-Application rule cannot be bypassed
     * by passing a mismatched pair.
     */
    @Override
    public ReleasePack addApplicationVersion(ReleasePackId id, ApplicationVersionId versionId) {
        ReleasePack pack = get(id);
        ApplicationVersion version = versions.findById(versionId)
                .orElseThrow(() -> new NotFoundException(
                        "Application Version " + versionId + " does not exist."));
        return releasePacks.save(pack.addApplicationVersion(version.applicationId(), version.id()));
    }

    @Override
    public ReleasePack removeApplicationVersion(ReleasePackId id, ApplicationVersionId versionId) {
        return releasePacks.save(get(id).removeApplicationVersion(versionId));
    }

    @Override
    public ReleasePack linkWorkItem(ReleasePackId id, String identifier, String title) {
        return releasePacks.save(get(id).linkWorkItem(new WorkItemReference(identifier, title)));
    }

    @Override
    public ReleasePack acceptWorkItemTitle(ReleasePackId id, String identifier, String title) {
        return releasePacks.save(get(id).acceptWorkItemTitle(identifier, title));
    }

    @Override
    public ReleasePack unlinkWorkItem(ReleasePackId id, String identifier) {
        return releasePacks.save(get(id).unlinkWorkItem(identifier));
    }

    @Override
    public ReleasePack updateHandover(ReleasePackId id, Handover handover) {
        ReleasePack updated = releasePacks.save(get(id).updateHandover(handover));

        // ADR-016: the current Handover lives on the aggregate and every version
        // lives in the revision store, so this is the one place that writes both.
        // Nothing else may write either, which is what keeps the newest revision
        // and the aggregate's Handover the same thing.
        handoverRevisions.append(HandoverRevision.record(
                id,
                handoverRevisions.highestRevisionNumber(id) + 1,
                updated.handover(),
                clock.instant()));

        return updated;
    }

    @Override
    public List<HandoverRevision> handoverHistory(ReleasePackId id) {
        // Fails for a Release Pack that does not exist rather than answering with
        // an empty history, which would read as "nothing was ever handed over".
        get(id);
        return handoverRevisions.findAllByReleasePack(id);
    }

    @Override
    public ReleasePack startIteration(StartIteration command) {
        ReleasePack pack = get(command.id());
        Instant startedAt = command.startedAt() == null ? clock.instant() : command.startedAt();
        return releasePacks.save(pack.startIteration(command.name(), startedAt, command.notes()));
    }

    @Override
    public ReleasePack completeIteration(ReleasePackId id, IterationId iterationId, Instant completedAt) {
        ReleasePack pack = get(id);
        Iteration iteration = requireIteration(pack, iterationId);
        Instant when = completedAt == null ? clock.instant() : completedAt;
        return releasePacks.save(pack.replaceIteration(iteration.complete(when)));
    }

    @Override
    public ReleasePack reopenIteration(ReleasePackId id, IterationId iterationId) {
        ReleasePack pack = get(id);
        return releasePacks.save(pack.replaceIteration(requireIteration(pack, iterationId).reopen()));
    }

    @Override
    public ReleasePack updateIterationNotes(ReleasePackId id, IterationId iterationId, String notes) {
        ReleasePack pack = get(id);
        return releasePacks.save(pack.replaceIteration(requireIteration(pack, iterationId).withNotes(notes)));
    }

    @Override
    public ReleasePack removeIteration(ReleasePackId id, IterationId iterationId) {
        return releasePacks.save(get(id).removeIteration(iterationId));
    }

    @Override
    public ReleasePack archive(ReleasePackId id) {
        return releasePacks.save(get(id).archive());
    }

    @Override
    public ReleasePack restore(ReleasePackId id) {
        return releasePacks.save(get(id).restore());
    }

    /**
     * Deletes a Release Pack outright.
     *
     * <p>Refused once the pack carries validation history. An Iteration records
     * that a QA or business team actually did work against this release, and
     * dropping that silently would destroy the traceability the pack exists to
     * provide. Archiving keeps it readable instead (ADR-008).
     */
    @Override
    public void delete(ReleasePackId id) {
        ReleasePack pack = get(id);
        if (!pack.iterations().isEmpty()) {
            throw new ApplicationException("Release Pack '" + pack.name() + "' has "
                    + pack.iterations().size() + " validation Iteration(s) recorded against it."
                    + " Archive it instead of deleting it, so that history is preserved.");
        }
        releasePacks.deleteById(id);
    }

    @Override
    public List<ReleasePack> list() {
        return releasePacks.findAll();
    }

    @Override
    public ReleasePack get(ReleasePackId id) {
        return releasePacks.findById(id)
                .orElseThrow(() -> new NotFoundException("Release Pack " + id + " does not exist."));
    }

    private Iteration requireIteration(ReleasePack pack, IterationId iterationId) {
        return pack.iteration(iterationId)
                .orElseThrow(() -> new NotFoundException(
                        "Iteration " + iterationId + " does not belong to Release Pack '" + pack.name() + "'."));
    }
}
