package dev.tower.api.releasepack;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import dev.tower.domain.releasepack.ReleasePack;

/** Request body for POST /api/release-packs and PUT /api/release-packs/{id}. */
public record ReleasePackRequest(
        @NotBlank(message = "name is required") @Size(max = ReleasePack.NAME_MAX_LENGTH,
                message = "name must be at most " + ReleasePack.NAME_MAX_LENGTH + " characters") String name,
        String description) {
}
