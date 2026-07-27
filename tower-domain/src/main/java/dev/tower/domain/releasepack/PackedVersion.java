package dev.tower.domain.releasepack;

import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.shared.DomainException;

/**
 * One Application Version included in a Release Pack.
 *
 * <p>The owning Application is carried alongside the version so the aggregate
 * can enforce, on its own, that a pack never contains two versions of the same
 * Application. Without it the rule would have to move to the application layer,
 * where a repository lookup would be needed to answer a question the pack ought
 * to be able to answer about itself.
 */
public record PackedVersion(ApplicationId applicationId, ApplicationVersionId versionId) {

    public PackedVersion {
        DomainException.require(applicationId != null, "Application id is required.");
        DomainException.require(versionId != null, "Application Version id is required.");
    }
}
