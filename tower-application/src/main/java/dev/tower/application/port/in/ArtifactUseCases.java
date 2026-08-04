package dev.tower.application.port.in;

import java.time.Instant;
import java.util.List;

import dev.tower.application.sync.ConnectionTest;
import dev.tower.domain.application.AcceptedArtifact;
import dev.tower.domain.application.ApplicationVersionId;

/**
 * Confirming that an Application Version's artifacts are where it says they
 * should be (ADR-021, FR-082).
 *
 * <p>Separate from {@link ApplicationUseCases} for the reason
 * {@link WorkItemUseCases} is separate from {@link ReleasePackUseCases}:
 * registering a version is Tower's own bookkeeping and always succeeds, while
 * confirming an artifact reaches an external system and may not. Keeping them
 * apart is what lets a version be registered, promoted and documented with no
 * repository configured and none reachable.
 *
 * <p>Nothing Tower <em>reads</em> here is stored, and that is not an omission
 * (FR-084). An artifact has no Environment, so there is no Observation to make;
 * nobody stated it, so it is not User-Owned Information.
 *
 * <p>The one exception proves the rule rather than bending it. {@link #accept}
 * writes a digest, and it may because a person stated that one — the same
 * distinction ADR-018 draws between a tracker's current wording, which is shown
 * and discarded, and the title somebody accepted, which a document prints.
 */
public interface ArtifactUseCases {

    /**
     * What the repositories say about this version's artifacts right now.
     *
     * <p>Every template bound to the Application is reported, including the ones
     * that found nothing. A chart reported absent is the fact worth having before
     * a release window rather than during one, and a list that quietly omitted it
     * would say the opposite.
     */
    Confirmation confirm(ApplicationVersionId applicationVersionId);

    /**
     * Accepts a digest a person has looked at (FR-086).
     *
     * <p>The one thing this whole Connector category writes, and it writes it
     * because a person stated it rather than because Tower read it. From here on
     * the digest is Tower's own: a release document prints this, and what the
     * repository says later is shown beside it rather than replacing it.
     *
     * <p>Takes the digest explicitly rather than accepting whatever the
     * repository reports at the moment the call arrives. Somebody is accepting
     * what they saw, and between seeing it and pressing the button a tag can be
     * pushed over — which is the exact situation this exists to make visible.
     * ADR-018 takes a work item's title the same way, for the same reason.
     */
    AcceptedArtifact accept(AcceptDigest command);

    /** Withdraws an acceptance, for a digest accepted in error. */
    void withdrawAcceptance(ApplicationVersionId applicationVersionId, String kind);

    /**
     * Every accepted digest for these versions, which is what a release document
     * prints.
     *
     * <p>Reads nothing external and cannot: a document that consulted a
     * repository at generation time would stop regenerating byte-identically the
     * day a tag was pushed over, which is precisely what NFR-025 forbids and what
     * accepting a digest exists to prevent.
     */
    List<AcceptedArtifact> acceptedArtifactsFor(List<ApplicationVersionId> applicationVersionIds);

    /**
     * @param coordinate where it was found, kept because a digest without an
     *                   address sends a reader nowhere
     */
    record AcceptDigest(ApplicationVersionId applicationVersionId, String kind,
                        String coordinate, String digest) {}

    /**
     * Confirms a repository answers and the credential is accepted (FR-061).
     *
     * <p>Takes a system as well as a Connector, because one Connector may be
     * pointed at more than one repository — an image registry and a chart
     * repository are commonly different addresses — and a test that only named the
     * Connector could not say which was being asked.
     */
    ConnectionTest testConnection(String connectorId, String system);

    /**
     * @param version   the Application Version the coordinates were composed
     *                  from, echoed so a reader can see what was asked
     * @param commit    the commit those coordinates used, or null when the
     *                  version carries none
     * @param artifacts one entry per template bound to the Application, in a
     *                  stable order
     */
    record Confirmation(String applicationVersionId, String version, String commit,
                        List<ConfirmedArtifact> artifacts) {

        public Confirmation {
            artifacts = List.copyOf(artifacts);
        }

        /** Whether any template is configured at all, which is the common case of nothing to show. */
        public boolean hasTemplates() {
            return !artifacts.isEmpty();
        }
    }

    /**
     * One artifact kind, as the binding addresses it and as the repository has it.
     *
     * @param kind       the team's own word, never interpreted (FR-083)
     * @param coordinate what was asked for, or null when the template could not
     *                   be composed from this version
     * @param digest     the repository's immutable identity for these bytes, or
     *                   null when it is not present or was not read
     * @param acceptedDigest what a person accepted, and what documents print, or
     *                   null when nobody has accepted one
     * @param state      PRESENT, DIVERGED, ABSENT, NOT_ADDRESSABLE or UNREAD
     * @param detail     why, for the states that need one, in words a reader can
     *                   act on and never carrying a credential (NFR-028)
     */
    record ConfirmedArtifact(String kind, String connectorId, String system, String coordinate,
                             String digest, String acceptedDigest, Instant storedAt,
                             long sizeBytes, String url, State state, String detail) {

        /** The repository now reports different bytes under the same name. */
        public boolean hasDiverged() {
            return state == State.DIVERGED;
        }
    }

    /**
     * What Tower was able to learn about one template.
     *
     * <p>Named rather than inferred from which fields are null, for the reason
     * {@link WorkItemUseCases.State} is: the difference between "the repository
     * does not have this" and "Tower could not ask" is exactly the distinction a
     * reader needs and exactly the one a null would hide.
     */
    enum State {

        /** The repository has it, at the coordinate this version composes. */
        PRESENT,

        /**
         * The repository has it, and its digest differs from the accepted one.
         *
         * <p>A tag was pushed over. This is a fact a team almost never learns any
         * other way, and it is the reason an artifact is shown beside what Tower
         * holds rather than merely looked up. Nothing is corrected: a handover
         * already given to another team does not change because somebody
         * re-published an image, exactly as ADR-018 rules for a rewritten ticket
         * summary.
         */
        DIVERGED,

        /**
         * The repository was read and has nothing at that coordinate.
         *
         * <p>Ordinary rather than alarming on its own: a build may not have run.
         * It is also what a wrong template produces, which ADR-021 argues is the
         * mildest failure any binding in Tower can cause — visible, harmless, and
         * fixed by correcting the template, because nothing was written.
         */
        ABSENT,

        /**
         * The template asks for something this version does not carry.
         *
         * <p>A template naming {@code {commit}} cannot be composed for a version
         * registered without one. Reported rather than guessed at: composing
         * something with a hole in it would ask the repository about a coordinate
         * no build ever wrote.
         */
        NOT_ADDRESSABLE,

        /** The repository was not read: no Connector installed, or it could not be reached. */
        UNREAD
    }
}
