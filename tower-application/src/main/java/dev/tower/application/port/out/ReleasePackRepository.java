package dev.tower.application.port.out;

import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.promotionpath.PromotionPathId;
import dev.tower.domain.releasepack.ReleasePack;
import dev.tower.domain.releasepack.ReleasePackId;

import java.util.List;
import java.util.Optional;

/** Outbound port for Release Pack storage. */
public interface ReleasePackRepository {

    ReleasePack save(ReleasePack pack);

    Optional<ReleasePack> findById(ReleasePackId id);

    List<ReleasePack> findAll();

    boolean existsByNameIgnoringCase(String name);

    /** Packs containing a given Application Version, so deletion can be refused. */
    List<ReleasePack> findAllContaining(ApplicationVersionId versionId);

    /**
     * Packs pinned to any version of a Promotion Path.
     *
     * <p>ADR-007 permits outright deletion of a path only while nothing
     * references it; this is the lookup that answers that question.
     */
    List<ReleasePack> findAllReferencingPromotionPath(PromotionPathId pathId);

    void deleteById(ReleasePackId id);
}
