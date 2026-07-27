package dev.tower.api.application;

import dev.tower.domain.application.ApplicationVersion;

/** API-layer representation of an Application Version. Never serialise {@link ApplicationVersion} directly. */
public record ApplicationVersionResponse(
        String id, String applicationId, String version, String branch, String tag, String commit,
        String buildIdentifier) {

    public static ApplicationVersionResponse from(ApplicationVersion version) {
        return new ApplicationVersionResponse(
                version.id().toString(),
                version.applicationId().toString(),
                version.version(),
                version.branch(),
                version.tag(),
                version.commit(),
                version.buildIdentifier());
    }
}
