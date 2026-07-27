package dev.tower.api.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import dev.tower.domain.application.ApplicationVersion;

/** Request body for POST /api/application-versions. */
public record ApplicationVersionRequest(
        @NotBlank(message = "applicationId is required") String applicationId,
        @NotBlank(message = "version is required") @Size(max = ApplicationVersion.VERSION_MAX_LENGTH,
                message = "version must be at most " + ApplicationVersion.VERSION_MAX_LENGTH + " characters")
        String version,
        String branch,
        String tag,
        String commit,
        String buildIdentifier) {
}
