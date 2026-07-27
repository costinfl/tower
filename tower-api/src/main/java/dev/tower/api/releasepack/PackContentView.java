package dev.tower.api.releasepack;

import dev.tower.domain.application.Application;
import dev.tower.domain.application.ApplicationVersion;

/**
 * One Application Version held in a Release Pack, fully resolved: the owning Application's name
 * and every identifying attribute of the version, not bare ids. The UI must not need a second
 * round trip to show what a pack contains.
 */
public record PackContentView(
        String applicationId, String applicationName, String versionId, String version, String branch,
        String tag, String commit, String buildIdentifier) {

    public static PackContentView from(Application application, ApplicationVersion version) {
        return new PackContentView(
                application.id().toString(), application.name(), version.id().toString(), version.version(),
                version.branch(), version.tag(), version.commit(), version.buildIdentifier());
    }
}
