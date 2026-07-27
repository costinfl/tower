package dev.tower.api.releasepack;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Request body for PUT /api/release-packs/{id}/promotion-path. */
public record AssignPromotionPathRequest(
        @NotBlank(message = "pathId is required") String pathId,
        @Min(value = 1, message = "versionNumber starts at 1") int versionNumber) {
}
