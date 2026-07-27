package dev.tower.application.port.out;

import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.promotionpath.PromotionPath;
import dev.tower.domain.promotionpath.PromotionPathId;

import java.util.List;
import java.util.Optional;

/** Outbound port for Promotion Path storage. Implemented by an adapter module. */
public interface PromotionPathRepository {

    PromotionPath save(PromotionPath path);

    Optional<PromotionPath> findById(PromotionPathId id);

    List<PromotionPath> findAll();

    boolean existsByNameIgnoringCase(String name);

    /**
     * Paths referencing an Environment in any of their versions.
     *
     * <p>ADR-005 makes this a list rather than an optional: several paths may
     * converge on the same Environment. It is also what makes deletion of a
     * referenced Environment refusable.
     */
    List<PromotionPath> findAllReferencing(EnvironmentId environment);

    void deleteById(PromotionPathId id);
}
