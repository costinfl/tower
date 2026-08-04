package dev.tower.application.port.in;

import java.time.Instant;
import java.util.List;

import dev.tower.application.sync.ConnectionTest;
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
 * <p>Nothing here is stored, and that is not an omission (FR-084). An artifact
 * has no Environment, so there is no Observation to make; nobody stated it, so it
 * is not User-Owned Information. What a release document prints is an accepted
 * digest, which is a different thing that a person did state.
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
     * @param state      PRESENT, ABSENT, NOT_ADDRESSABLE or UNREAD
     * @param detail     why, for the states that need one, in words a reader can
     *                   act on and never carrying a credential (NFR-028)
     */
    record ConfirmedArtifact(String kind, String connectorId, String system, String coordinate,
                             String digest, Instant storedAt, long sizeBytes, String url,
                             State state, String detail) {
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
