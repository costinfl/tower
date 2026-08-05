package dev.tower.application.discovery;

/**
 * A version some system holds that Tower could register (issue #3, ADR-020).
 *
 * <p>A <em>candidate</em>, and the word is load-bearing. BR-01 makes an
 * Application Version immutable, so discovery may surface a version Tower has not
 * seen and may never revise one Tower already holds. Nothing here is stored by
 * the act of discovering it; whether a candidate becomes an Application Version
 * is the user's decision, taken through the ordinary creation path.
 *
 * <p>Two systems produce these now, which is why {@code source} exists. Source
 * control offers a ref (ADR-014); a build job offers a run (ADR-020). ADR-020
 * put it as "the version discovery screen gains a source rather than a mode" —
 * the two are the same kind of proposal and belong in one list, but a reader
 * deciding whether to accept one needs to know which system said so.
 *
 * <p>{@code alreadyRegistered} is what makes that decision possible without
 * making it for them. A rediscovered version Tower already holds is not a change
 * and not a problem — it is the normal case on the second run — so it is reported
 * and marked rather than hidden. Hiding it would leave a user wondering whether
 * Tower had lost the version.
 *
 * <p>The vendor-specific fields are deliberately allowed to be null, and each is
 * null for the source that has no such thing. A ref has a branch, a tag and a
 * commit and no build identifier; ADR-014 records why the last one stays absent
 * rather than being derived. A build run has a build identifier and, unless it
 * recorded one, no commit — deriving a commit from a build would be the same
 * guess in the opposite direction. Reporting a field as absent is honest;
 * inventing one is not.
 *
 * @param version           the Application Version this denotes, after the
 *                          binding's pattern has been applied
 * @param source            which Connector proposed it, so a reader can tell a
 *                          ref from a build at a glance
 * @param origin            where it came from within that source, in that
 *                          source's own words — a ref name, or a job and run —
 *                          so a user can see what the version was derived from
 *                          rather than trusting it
 * @param branch            the branch name when this came from a branch, else null
 * @param tag               the tag name when this came from a tag, else null
 * @param commit            the commit this resolves to, or null when the source
 *                          does not record one
 * @param buildIdentifier   the run that produced it, or null for a ref
 * @param alreadyRegistered whether Tower already holds this version for this Application
 */
public record DiscoveredVersion(
        String version,
        String source,
        String origin,
        String branch,
        String tag,
        String commit,
        String buildIdentifier,
        boolean alreadyRegistered) {

    /** A candidate read from a git ref (ADR-014). */
    public static DiscoveredVersion fromRef(String version, String source, String refName,
                                            String branch, String tag, String commit,
                                            boolean alreadyRegistered) {
        return new DiscoveredVersion(version, source, refName, branch, tag, commit, null,
                alreadyRegistered);
    }

    /**
     * A candidate read from a build run (ADR-020).
     *
     * <p>Carries no branch and no tag. A build knows what it produced, not which
     * ref somebody cut it from, and a Connector that guessed would be wrong for
     * the first team that builds from a detached commit.
     */
    public static DiscoveredVersion fromBuild(String version, String source, String origin,
                                              String buildIdentifier, boolean alreadyRegistered) {
        return new DiscoveredVersion(version, source, origin, null, null, null, buildIdentifier,
                alreadyRegistered);
    }

    public boolean isNew() {
        return !alreadyRegistered;
    }
}
