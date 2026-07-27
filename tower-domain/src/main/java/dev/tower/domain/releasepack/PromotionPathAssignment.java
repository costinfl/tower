package dev.tower.domain.releasepack;

import dev.tower.domain.promotionpath.PromotionPathId;
import dev.tower.domain.shared.DomainException;

/**
 * The Promotion Path a Release Pack follows, pinned to a specific version.
 *
 * <p>This is where ADR-007 does its real work. A Release Pack references a
 * Promotion Path <em>version</em>, not merely the path identity, so a pack that
 * has already progressed keeps reporting the topology it actually followed even
 * after the team publishes a new version of that path.
 */
public record PromotionPathAssignment(PromotionPathId pathId, int versionNumber) {

    public PromotionPathAssignment {
        DomainException.require(pathId != null, "Promotion Path id is required.");
        DomainException.require(versionNumber >= 1,
                "Promotion Path version number starts at 1.");
    }
}
