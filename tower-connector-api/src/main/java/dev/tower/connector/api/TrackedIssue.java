package dev.tower.connector.api;

/**
 * A work item as the tracker describes it right now (ADR-018).
 *
 * <p>Deliberately thin. Tower holds an identifier and a title somebody accepted;
 * it holds no description, no comments, no assignee, no workflow and no
 * relationships, because anything beyond identifying the work belongs to the
 * tracker. This record carries only what is needed to name a work item, say
 * whether it is finished, and link to it.
 *
 * <p>Nothing here is stored as an Observation, or stored at all. It is read on
 * request and shown beside what Tower holds.
 *
 * @param identifier the key in the tracker, as the tracker writes it — "PROJ-123", "42"
 * @param title      the tracker's current summary
 * @param status     the tracker's own word for where the item stands, not normalised into
 *                   a Tower vocabulary: "In Progress", "Done", "closed". Trackers disagree
 *                   about what states exist, and inventing a common set would mean deciding
 *                   which of a team's states counts as finished — a judgement Tower has no
 *                   standing to make
 * @param closed     whether the tracker considers the item resolved, which is the one thing
 *                   every tracker does agree on and the only normalisation offered
 * @param url        where a reader can see the item for themselves
 */
public record TrackedIssue(String identifier, String title, String status, boolean closed, String url) {

    public TrackedIssue {
        if (identifier == null || identifier.isBlank()) {
            throw new ConnectorException("A tracked issue must carry the identifier the tracker gave it.");
        }
        identifier = identifier.trim();
        title = title == null ? "" : title.trim();
        status = status == null ? "" : status.trim();
        url = url == null ? "" : url.trim();
    }
}
