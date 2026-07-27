package dev.tower.api.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import dev.tower.domain.application.Application;

/** Request body for POST /api/applications and PUT /api/applications/{id}. */
public record ApplicationRequest(
        @NotBlank(message = "name is required") @Size(max = Application.NAME_MAX_LENGTH,
                message = "name must be at most " + Application.NAME_MAX_LENGTH + " characters") String name,
        String description) {
}
