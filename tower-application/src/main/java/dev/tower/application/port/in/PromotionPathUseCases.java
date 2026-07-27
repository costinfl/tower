package dev.tower.application.port.in;

import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.promotionpath.PromotionPath;
import dev.tower.domain.promotionpath.PromotionPathId;

import java.util.List;

/**
 * Inbound port for managing Promotion Paths (issues #10, #11).
 *
 * <p>Tower records Promotion Paths and never executes a promotion (ADR-001).
 */
public interface PromotionPathUseCases {

    PromotionPath create(CreatePromotionPath command);

    /** Publishes a new version. Earlier versions are never modified (ADR-007). */
    PromotionPath publishVersion(PublishVersion command);

    PromotionPath rename(RenamePromotionPath command);

    /** Archives rather than deletes once the path carries history (ADR-007). */
    PromotionPath archive(PromotionPathId id);

    PromotionPath restore(PromotionPathId id);

    /**
     * Deletes a Promotion Path outright.
     *
     * <p>ADR-007 permits this only while nothing references the path. A path
     * that has been archived carries history and is kept.
     */
    void delete(PromotionPathId id);

    List<PromotionPath> list();

    PromotionPath get(PromotionPathId id);

    record CreatePromotionPath(String name, List<EnvironmentId> environments) {}

    record PublishVersion(PromotionPathId id, List<EnvironmentId> environments) {}

    record RenamePromotionPath(PromotionPathId id, String name) {}
}
