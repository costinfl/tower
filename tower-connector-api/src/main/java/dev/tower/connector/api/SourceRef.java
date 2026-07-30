package dev.tower.connector.api;

/**
 * One branch or tag a Source Control Connector found, and the commit it resolves
 * to.
 *
 * <p>CM-03 makes this record the termination point for vendor concepts, the same
 * role {@link RunningWorkload} plays for Deployment Platforms. ADR-014 makes that
 * easy here: a branch, a tag and a commit are git concepts, so a GitHub, GitLab
 * or Bitbucket remote all arrive as this shape and nothing above this package can
 * tell which one was read.
 *
 * <p>This is deliberately <em>not</em> an Application Version. It carries no
 * Application, because a Connector cannot know which Application a repository
 * belongs to — that correspondence is user-owned configuration (ADR-012). And it
 * is a <em>candidate</em> rather than a fact: BR-01 makes an Application Version
 * immutable, so a Connector may surface a version Tower has not seen and may
 * never revise one Tower already holds. Whether a candidate is accepted is the
 * user's decision.
 *
 * <p>There is no build identifier. ADR-014 records why: a build identifier comes
 * from a build system and is not recorded in a ref, so Tower leaves it absent
 * rather than deriving something plausible from a commit.
 *
 * @param kind   whether this ref is a branch or a tag
 * @param name   the ref's short name — {@code release/2.5}, {@code v2.5.0} — with
 *               its {@code refs/heads/} or {@code refs/tags/} prefix removed,
 *               because the prefix is git's bookkeeping and not what anyone calls it
 * @param commit the full commit hash the ref resolves to. For an annotated tag
 *               this is the commit the tag points at, not the tag object: the
 *               commit identifies the code, and the tag object is an
 *               implementation detail of how git recorded the label
 */
public record SourceRef(Kind kind, String name, String commit) {

    /** What git can label a commit with, and all this Connector reads. */
    public enum Kind {
        BRANCH,
        TAG
    }

    public SourceRef {
        if (kind == null) {
            throw new ConnectorException("A source ref must say whether it is a branch or a tag.");
        }
        name = requireText(name, "A source ref must carry the name the repository gave it.");
        commit = requireText(commit, "A source ref must carry the commit it resolves to.");
    }

    public static SourceRef branch(String name, String commit) {
        return new SourceRef(Kind.BRANCH, name, commit);
    }

    public static SourceRef tag(String name, String commit) {
        return new SourceRef(Kind.TAG, name, commit);
    }

    public boolean isBranch() {
        return kind == Kind.BRANCH;
    }

    public boolean isTag() {
        return kind == Kind.TAG;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ConnectorException(message);
        }
        return value.trim();
    }

    /** Rendered into reports, so it stays readable. */
    @Override
    public String toString() {
        return kind.name().toLowerCase(java.util.Locale.ROOT) + " " + name + " at " + commit;
    }
}
