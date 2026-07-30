package dev.tower.application.discovery;

/**
 * A version source control holds that Tower could register (issue #3).
 *
 * <p>A <em>candidate</em>, and the word is load-bearing. BR-01 makes an
 * Application Version immutable, so discovery may surface a version Tower has not
 * seen and may never revise one Tower already holds. Nothing here is stored by
 * the act of discovering it; whether a candidate becomes an Application Version
 * is the user's decision, taken through the ordinary creation path.
 *
 * <p>{@code alreadyRegistered} is what makes that decision possible without
 * making it for them. A rediscovered ref for a version Tower already holds is not
 * a change and not a problem — it is the normal case on the second run — so it is
 * reported and marked rather than hidden. Hiding it would leave a user wondering
 * whether Tower had lost the version.
 *
 * <p>Carries no build identifier. ADR-014 records why: a build identifier comes
 * from a build system and is not recorded in a ref, so it stays absent rather
 * than being derived from a commit.
 *
 * @param version           the Application Version the ref name denotes, after the
 *                          binding's pattern has been applied
 * @param refName           the ref as source control names it, so a user can see
 *                          what the version was derived from rather than trusting it
 * @param branch            the branch name when this came from a branch, else null
 * @param tag               the tag name when this came from a tag, else null
 * @param commit            the commit the ref resolves to
 * @param alreadyRegistered whether Tower already holds this version for this Application
 */
public record DiscoveredVersion(
        String version,
        String refName,
        String branch,
        String tag,
        String commit,
        boolean alreadyRegistered) {

    public boolean isNew() {
        return !alreadyRegistered;
    }
}
