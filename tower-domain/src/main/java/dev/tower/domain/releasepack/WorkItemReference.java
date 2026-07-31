package dev.tower.domain.releasepack;

import dev.tower.domain.shared.DomainException;

/**
 * A work item a Release Pack claims to deliver (ADR-018).
 *
 * <p>Intent, not an Observation. A developer states that this release delivers
 * PROJ-123; Tower did not see that, it was told it. Product-Boundaries.md says
 * Tower does not own work items, and this holds to that: an identifier and a
 * title somebody accepted, and nothing else. No description, no comments, no
 * assignee, no workflow, no relationships — those stay in the tracker, and the
 * Viewer links to them.
 *
 * <p>The title is <em>accepted</em> rather than fetched. Tower reads it from the
 * tracker once, a person accepts it, and from then on it is Tower's own. That is
 * what keeps NFR-025 true: a generated document reads nothing external, so
 * regenerating an unchanged Release Pack still produces the same bytes after
 * somebody rewrites a ticket summary. The tracker's current wording is shown in
 * the Viewer beside this one, and where they differ the difference is shown
 * rather than silently corrected — a handover already given to another team does
 * not change because a ticket was edited.
 *
 * <p>The identifier is written as the tracker writes it and is not parsed here.
 * "PROJ-123", "#42" and "GH-7" are all just text to the Domain Model, which is
 * what lets the tracker be replaced without changing it (Principles.md).
 */
public record WorkItemReference(String identifier, String title) {

    public static final int IDENTIFIER_MAX_LENGTH = 100;
    public static final int TITLE_MAX_LENGTH = 500;

    public WorkItemReference {
        DomainException.require(identifier != null && !identifier.isBlank(),
                "A work item reference must name the item, as the tracker writes it.");
        identifier = identifier.trim();
        DomainException.require(identifier.length() <= IDENTIFIER_MAX_LENGTH,
                "A work item identifier may be at most " + IDENTIFIER_MAX_LENGTH + " characters.");

        // A title is optional: a reference may be linked before any Connector is
        // configured, or to a tracker Tower cannot reach. The reference is the
        // claim; the title is a convenience the document borrows.
        title = title == null ? "" : title.trim();
        DomainException.require(title.length() <= TITLE_MAX_LENGTH,
                "A work item title may be at most " + TITLE_MAX_LENGTH + " characters.");
    }

    /** Named only by its identifier, with no title accepted yet. */
    public static WorkItemReference of(String identifier) {
        return new WorkItemReference(identifier, "");
    }

    /**
     * Whether a person has accepted a title for this reference.
     *
     * <p>Stated rather than left to be read off an empty string, because a
     * document that prints an identifier alone should say why.
     */
    public boolean hasTitle() {
        return !title.isEmpty();
    }

    /** The same item, with a title a person has accepted from the tracker. */
    public WorkItemReference withTitle(String accepted) {
        return new WorkItemReference(identifier, accepted);
    }

    /**
     * Two references naming the same item are the same reference, whatever
     * titles they carry.
     *
     * <p>A Release Pack may not deliver PROJ-123 twice, and it would not stop
     * being the same work item because somebody accepted a newer summary for one
     * of them. Case-insensitive: trackers are, and "proj-123" is not a second
     * ticket.
     */
    public boolean namesSameItemAs(WorkItemReference other) {
        return other != null && identifier.equalsIgnoreCase(other.identifier);
    }
}
