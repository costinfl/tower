package dev.tower.api.promotionpath;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/** Request body for POST /api/promotion-paths/{id}/versions. */
public record VersionRequest(
        @NotEmpty(message = "environmentIds must contain at least one Environment id") List<String> environmentIds) {
}
