package dev.tower.api.promotionpath;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import dev.tower.domain.promotionpath.PromotionPath;

/** Request body for PUT /api/promotion-paths/{id}/name. */
public record RenameRequest(
        @NotBlank(message = "name is required") @Size(max = PromotionPath.NAME_MAX_LENGTH,
                message = "name must be at most " + PromotionPath.NAME_MAX_LENGTH + " characters") String name) {
}
