package dev.tower.connector.api;

import java.time.Instant;

/**
 * An artifact the repository holds (ADR-021).
 *
 * <p>The digest is the field this record exists for. A coordinate names a moving
 * target — a tag can be pushed over, and the bytes beneath it change — where a
 * digest names bytes. For a release record a year later, it is the only identity
 * that still means anything, and a digest that has changed under a version Tower
 * already knew about is a fact a team almost never learns any other way.
 *
 * <p>Carries no notion of promotion or of which repository is "production".
 * ADR-021 records why: an artifact in a repository named {@code docker-prod} is a
 * file in a folder whose name says "prod", and nothing about it says anything is
 * running.
 *
 * @param coordinate what was asked for, echoed back so a caller matching several
 *                   answers to several questions does not have to rely on order
 * @param digest     the repository's own immutable identity for these bytes,
 *                   however it spells it — {@code sha256:…} for a container
 *                   image, a checksum for a file. Never normalised: what a
 *                   repository calls a digest is its own business, and Tower only
 *                   ever compares it for equality
 * @param storedAt   when the repository says it received this, which is not when
 *                   it was built and not when it was deployed
 * @param sizeBytes  how large, or zero where the repository does not say
 * @param url        the address a reader can follow
 */
public record StoredArtifact(String coordinate, String digest, Instant storedAt,
                             long sizeBytes, String url) {

    public StoredArtifact {
        if (coordinate == null || coordinate.isBlank()) {
            throw new ConnectorException("A stored artifact must say which coordinate it answers.");
        }
        coordinate = coordinate.trim();
        digest = digest == null ? "" : digest.trim();
        url = url == null ? "" : url.trim();
    }

    /** Whether the repository gave a digest at all; some do not for some types. */
    public boolean hasDigest() {
        return !digest.isBlank();
    }
}
