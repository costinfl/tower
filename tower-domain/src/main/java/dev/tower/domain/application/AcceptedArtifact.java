package dev.tower.domain.application;

import java.time.Instant;
import java.util.Locale;

import dev.tower.domain.shared.DomainException;

/**
 * A digest somebody accepted for one kind of artifact an Application Version
 * produced (ADR-021, FR-086).
 *
 * <p>User-Owned Information, and the reason it exists is worth stating plainly.
 * Everything else the Artifact Repository Connector learns is shown and thrown
 * away (FR-084) — an artifact has no Environment, so it is not an Observation,
 * and nobody stated it, so it is not Intent. This one thing is different because
 * somebody <em>did</em> state it: they looked at what the repository reported and
 * accepted it, and from then on it is Tower's own.
 *
 * <p>That is exactly {@link dev.tower.domain.releasepack.WorkItemReference}'s
 * arrangement for a title, reached for the same reason. A tag is mutable:
 * {@code 2.5.0-abc1234} can be pushed over and the bytes beneath it change. A
 * document that printed whatever the repository said at the moment of rendering
 * would stop regenerating byte-identically the day somebody re-pushed, which
 * NFR-025 forbids. So a document prints this, and the repository's current answer
 * is shown beside it rather than silently replacing it.
 *
 * <p>The coordinate is kept as well as the digest, and not as decoration: a
 * document that named a digest without saying where it was found would send a
 * reader nowhere, and a template corrected after acceptance would otherwise make
 * the record unreadable.
 *
 * <p>The digest is stored as the repository spelled it and is never parsed here.
 * {@code sha256:…} for a container image, a checksum for a file — all just text
 * to the Domain Model, which is what lets the repository be replaced without
 * changing it (Principles.md). Tower only ever compares it for equality.
 *
 * @param kind       the team's own word for what this addresses, folded to lower
 *                   case because a kind is a key a person types twice
 * @param acceptedAt when a person accepted it, which is not when the repository
 *                   received the bytes
 */
public record AcceptedArtifact(
        ApplicationVersionId applicationVersionId,
        String kind,
        String coordinate,
        String digest,
        Instant acceptedAt) {

    public static final int KIND_MAX_LENGTH = 50;
    public static final int COORDINATE_MAX_LENGTH = 500;
    public static final int DIGEST_MAX_LENGTH = 200;

    public AcceptedArtifact {
        DomainException.require(applicationVersionId != null,
                "An accepted artifact must belong to an Application Version.");

        kind = normaliseKind(kind);
        DomainException.require(!kind.isEmpty(),
                "An accepted artifact must say which kind of artifact it is.");
        DomainException.require(kind.length() <= KIND_MAX_LENGTH,
                "An artifact kind may be at most " + KIND_MAX_LENGTH + " characters.");

        DomainException.require(coordinate != null && !coordinate.isBlank(),
                "An accepted artifact must say where it was found.");
        coordinate = coordinate.trim();
        DomainException.require(coordinate.length() <= COORDINATE_MAX_LENGTH,
                "An artifact coordinate may be at most " + COORDINATE_MAX_LENGTH + " characters.");

        // Required, unlike a work item's title. A reference with no title is
        // still a claim about what a release delivers; an accepted artifact with
        // no digest is nothing at all — the digest is the whole of what is being
        // accepted, and a coordinate on its own names a moving target.
        DomainException.require(digest != null && !digest.isBlank(),
                "An accepted artifact must carry the digest that was accepted."
                        + " A coordinate on its own names a tag, and a tag can be pushed over.");
        digest = digest.trim();
        DomainException.require(digest.length() <= DIGEST_MAX_LENGTH,
                "An artifact digest may be at most " + DIGEST_MAX_LENGTH + " characters.");

        DomainException.require(acceptedAt != null,
                "An accepted artifact must say when it was accepted.");
    }

    /**
     * The canonical spelling of an artifact kind.
     *
     * <p>Defined here rather than beside the binding that also uses it, because
     * two definitions of "the same kind" would eventually disagree, and the one
     * that decides whether a document finds its digest is this one.
     */
    public static String normaliseKind(String kind) {
        return kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
    }

    /** Whether the repository now reports different bytes under the same name. */
    public boolean differsFrom(String reportedDigest) {
        return reportedDigest != null && !reportedDigest.isBlank() && !digest.equals(reportedDigest);
    }
}
