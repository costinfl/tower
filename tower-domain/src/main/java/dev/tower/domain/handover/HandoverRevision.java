package dev.tower.domain.handover;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import dev.tower.domain.releasepack.ReleasePackId;
import dev.tower.domain.shared.DomainException;

/**
 * What a Release Pack's Handover said at one point in time (IA-02, FR-054,
 * ADR-016).
 *
 * <p>Immutable, and appended rather than replaced. The reason is the same one
 * ADR-002 gives for an Observation: what a team was told on 3 August remains what
 * they were told, whatever is written afterwards. Handover holds the deployment
 * instructions and the rollback procedure — the text someone follows when a
 * release is going wrong — and overwriting it destroys the record most needed at
 * the moment it is needed.
 *
 * <p>Numbered from 1 and rising, so a revision can be named without a timestamp.
 * The newest revision is what the Release Pack currently holds; ADR-016 records
 * why that value appears both here and on the aggregate.
 *
 * <p>Carries no author. ADR-009 has every developer running their own instance,
 * so the author is always the person reading it and the column would hold the
 * same name on every row.
 */
public record HandoverRevision(
        HandoverRevisionId id,
        ReleasePackId releasePackId,
        int revisionNumber,
        Handover handover,
        Instant recordedAt) {

    public HandoverRevision {
        DomainException.require(id != null, "A Handover revision must have an identity.");
        DomainException.require(releasePackId != null,
                "A Handover revision must belong to a Release Pack.");
        DomainException.require(revisionNumber >= 1,
                "Handover revisions are numbered from 1.");
        DomainException.require(handover != null, "A Handover revision must carry the Handover.");
        DomainException.require(recordedAt != null, "A Handover revision must record when it was written.");
    }

    public static HandoverRevision record(
            ReleasePackId releasePackId, int revisionNumber, Handover handover, Instant recordedAt) {
        return new HandoverRevision(
                HandoverRevisionId.newId(), releasePackId, revisionNumber, handover, recordedAt);
    }

    /** True when this revision recorded that nothing had been prepared. */
    public boolean isEmpty() {
        return handover.isEmpty();
    }

    /** Identity of a {@link HandoverRevision}. */
    public record HandoverRevisionId(UUID value) {

        public HandoverRevisionId {
            DomainException.require(value != null, "Handover revision id is required.");
        }

        public static HandoverRevisionId newId() {
            return new HandoverRevisionId(UUID.randomUUID());
        }

        public static HandoverRevisionId of(String value) {
            Objects.requireNonNull(value, "Handover revision id is required.");
            try {
                return new HandoverRevisionId(UUID.fromString(value));
            } catch (IllegalArgumentException e) {
                throw new DomainException("Handover revision id is not a valid identifier: " + value);
            }
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }
}
