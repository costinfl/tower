package dev.tower.application.discovery;

import java.util.List;

import dev.tower.domain.application.ApplicationId;

/**
 * What one look at an Application's repository found (issue #3).
 *
 * <p>Reports refs it could not turn into a version rather than dropping them,
 * for the same reason a Sync Run reports unrecognized workloads (FR-060): a user
 * whose pattern is wrong needs to see the refs it failed on, and a silently
 * shorter list gives them nothing to work from.
 *
 * <p>Distinguishes "found nothing" from "could not look". Those are the two
 * answers a discovery can give that look identical in an empty list, and they
 * lead to opposite next steps — fix the pattern, or fix the credential.
 *
 * @param applicationId the Application looked up
 * @param repositoryUrl the remote that was read, so a surprising result can be
 *                      traced to the configuration that produced it
 * @param candidates    versions the repository holds, newest-looking last; ordering
 *                      is the Connector's, which ADR-014 makes stable
 * @param unmatched     ref names the binding's pattern did not recognise
 * @param failure       why the repository could not be read, or null when it was
 */
public record VersionDiscovery(
        ApplicationId applicationId,
        String repositoryUrl,
        List<DiscoveredVersion> candidates,
        List<String> unmatched,
        String failure) {

    public VersionDiscovery {
        candidates = List.copyOf(candidates);
        unmatched = List.copyOf(unmatched);
    }

    public static VersionDiscovery found(ApplicationId applicationId, String repositoryUrl,
                                         List<DiscoveredVersion> candidates, List<String> unmatched) {
        return new VersionDiscovery(applicationId, repositoryUrl, candidates, unmatched, null);
    }

    /**
     * A repository that could not be read.
     *
     * <p>Returned rather than thrown, matching how a Sync Run records a scope it
     * could not read: an unreachable repository is an answer about the world, not
     * a failure of the question.
     */
    public static VersionDiscovery failed(ApplicationId applicationId, String repositoryUrl, String failure) {
        return new VersionDiscovery(applicationId, repositoryUrl, List.of(), List.of(), failure);
    }

    public boolean succeeded() {
        return failure == null;
    }

    /** True when the repository was read and held nothing this binding recognises. */
    public boolean foundNothing() {
        return succeeded() && candidates.isEmpty();
    }

    public List<DiscoveredVersion> newVersions() {
        return candidates.stream().filter(DiscoveredVersion::isNew).toList();
    }
}
