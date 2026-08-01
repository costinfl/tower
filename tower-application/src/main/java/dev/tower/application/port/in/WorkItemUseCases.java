package dev.tower.application.port.in;

import java.util.List;

import dev.tower.domain.releasepack.ReleasePackId;

/**
 * Reading a Release Pack's work items against the tracker (ADR-018).
 *
 * <p>Separate from {@link ReleasePackUseCases} on purpose. Linking a work item
 * is Intent and always succeeds; resolving one reaches an external system and
 * may not. Keeping them apart means a Release Pack can always be read, edited
 * and documented with no tracker configured and none reachable — which is the
 * property that lets a reference be linked before any Connector exists.
 */
public interface WorkItemUseCases {

    /**
     * What the tracker says about this release's work items right now, beside
     * what Tower holds.
     *
     * <p>Nothing here is stored. A title Tower shows in a document is the one a
     * person accepted; this is the comparison that lets somebody notice the two
     * have drifted and decide whether to accept the newer wording.
     */
    Resolution resolve(ReleasePackId releasePackId);

    /**
     * Confirms the tracker answers and the credential is accepted (FR-061).
     *
     * <p>Reads nothing about any particular work item and writes nothing at all.
     */
    ConnectionReport testConnection(String connectorId);

    /**
     * @param connectorId the tracker consulted, or null when none is configured
     * @param items       one entry per reference the release carries, in the order
     *                    it carries them
     * @param failure     why the tracker could not be read, or null when it was.
     *                    A failure does not empty {@code items}: what Tower holds
     *                    is still shown, marked as unresolved, because a tracker
     *                    being unreachable says nothing about the release
     */
    record Resolution(String connectorId, List<ResolvedWorkItem> items, String failure) {

        public Resolution {
            items = List.copyOf(items);
        }

        public boolean reachedTracker() {
            return failure == null;
        }
    }

    /**
     * One work item, as Tower holds it and as the tracker has it.
     *
     * @param acceptedTitle what a person accepted, and what documents print
     * @param trackerTitle  what the tracker says now, or null when unresolved
     * @param status        the tracker's own word, never normalised (ADR-018)
     * @param closed        whether the tracker considers it resolved — the one
     *                      normalisation offered, because it is the only thing
     *                      every tracker agrees on
     * @param url           where a reader can see it, or null when unresolved
     * @param state         RESOLVED, DIVERGED, NOT_FOUND or UNRESOLVED
     */
    record ResolvedWorkItem(String identifier, String acceptedTitle,
                            String trackerTitle, String status, boolean closed, String url,
                            State state) {

        /** The accepted title and the tracker's have drifted apart. */
        public boolean hasDiverged() {
            return state == State.DIVERGED;
        }
    }

    /**
     * What Tower was able to learn about one reference.
     *
     * <p>Named rather than left to be inferred from which fields are null,
     * because the difference between "the tracker does not have this" and "Tower
     * could not ask" is exactly the distinction a reader needs and exactly the
     * one a null would hide.
     */
    enum State {
        /** The tracker has it, and its title matches the one Tower holds. */
        RESOLVED,
        /** The tracker has it, and its title differs from the accepted one. */
        DIVERGED,
        /** The tracker was read and does not know this identifier. */
        NOT_FOUND,
        /** The tracker was not read: none configured, or it could not be reached. */
        UNRESOLVED
    }

    /**
     * @param reachable whether the tracker answered
     * @param message   what happened, in words a reader can act on, and never
     *                  carrying the credential
     */
    record ConnectionReport(String connectorId, String locator, boolean reachable, String message) {
    }
}
