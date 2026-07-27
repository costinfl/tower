package dev.tower.api.environment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.Stage;

/** Request body for POST /api/environments and PUT /api/environments/{id}. */
public record EnvironmentRequest(
        @NotBlank(message = "name is required") @Size(max = Environment.NAME_MAX_LENGTH,
                message = "name must be at most " + Environment.NAME_MAX_LENGTH + " characters") String name,
        @NotNull(message = "stage is required") Stage stage) {
}
