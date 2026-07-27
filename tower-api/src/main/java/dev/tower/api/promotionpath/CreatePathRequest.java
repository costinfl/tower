package dev.tower.api.promotionpath;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import dev.tower.domain.promotionpath.PromotionPath;

/** Request body for POST /api/promotion-paths. */
public record CreatePathRequest(
        @NotBlank(message = "name is required") @Size(max = PromotionPath.NAME_MAX_LENGTH,
                message = "name must be at most " + PromotionPath.NAME_MAX_LENGTH + " characters") String name,
        @NotEmpty(message = "environmentIds must contain at least one Environment id") List<String> environmentIds) {
}
