package dev.tower.domain.application;

import dev.tower.domain.shared.DomainException;

/**
 * One immutable version of an Application (Glossary.md, Domain-Model.md).
 *
 * <p>BR-01: Application Versions are immutable. Tower never modifies one. This
 * type therefore offers no mutator at all — correcting a mistake means creating
 * a different version, exactly as it would in the systems Tower observes.
 *
 * <p>The identifying attributes below are the ones the Glossary lists, and all
 * except {@code version} are optional. Tower does not prescribe how external
 * systems represent versions, so a team that tags releases but does not record
 * build numbers is fully supported.
 */
public record ApplicationVersion(
        ApplicationVersionId id,
        ApplicationId applicationId,
        String version,
        String branch,
        String tag,
        String commit,
        String buildIdentifier) {

    public static final int VERSION_MAX_LENGTH = 100;

    public ApplicationVersion {
        DomainException.require(id != null, "Application Version id is required.");
        DomainException.require(applicationId != null,
                "An Application Version must belong to an Application.");
        DomainException.require(version != null && !version.isBlank(),
                "Application Version requires a version.");
        DomainException.require(version.length() <= VERSION_MAX_LENGTH,
                "Version must be at most " + VERSION_MAX_LENGTH + " characters.");

        version = version.trim();
        branch = blankToNull(branch);
        tag = blankToNull(tag);
        commit = blankToNull(commit);
        buildIdentifier = blankToNull(buildIdentifier);
    }

    public static ApplicationVersion create(ApplicationId applicationId, String version, String branch,
                                            String tag, String commit, String buildIdentifier) {
        return new ApplicationVersion(ApplicationVersionId.newId(), applicationId, version,
                branch, tag, commit, buildIdentifier);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
