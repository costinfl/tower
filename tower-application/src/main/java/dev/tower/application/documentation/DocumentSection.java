package dev.tower.application.documentation;

import java.util.Optional;

/**
 * A part of a release document that a Document Template may include or leave out
 * (OQ-010, ADR-013, FR-062).
 *
 * <p>The constants are the whole vocabulary of customisation. A template selects
 * from this list and orders it; it never supplies markup. ADR-013 records why:
 * NFR-025 requires a regenerated document to be byte-identical, and rendering
 * that stays in Tower's own code is what keeps that true.
 *
 * <p>Declaration order is the order a complete document uses, so
 * {@link DocumentTemplate#complete()} needs no separate list to keep in step
 * with this one — and adding a section here adds it to the complete document by
 * construction rather than by remembering to.
 *
 * <p>The title and the closing provenance note are not here on purpose. They
 * are not sections a team may prefer to drop: the title says which release the
 * page describes, and the note says the page is a view of the Canonical Model
 * and not a source of truth (BR-07). A document that could be configured to
 * omit either would be a document that could mislead.
 */
public enum DocumentSection {

    STATUS("Status",
            "Observed state and lifecycle, with the note that keeps the two apart."),
    PROMOTION_PATH("Promotion Path",
            "The path this release follows, at the version it was pinned to."),
    CONTENTS("Contents",
            "The Application Versions the release contains."),
    // After Contents deliberately: the same release described twice, first in
    // the vocabulary of what is deployed and then in the vocabulary of what was
    // asked for. The people receiving a handover recognise the second.
    WORK_ITEMS("Work Items",
            "The work items this release delivers, as the team stated them."),
    HANDOVER("Handover",
            "Deployment instructions, commands, migrations, rollback and notes."),
    ITERATIONS("Validation Iterations",
            "The validation cycles recorded against the release."),
    SIGHTINGS("Where this release has been observed",
            "Every Environment the release has been seen in, each citing its Observation.");

    private final String heading;
    private final String description;

    DocumentSection(String heading, String description) {
        this.heading = heading;
        this.description = description;
    }

    /** The heading this section carries in a rendered document. */
    public String heading() {
        return heading;
    }

    /** What the section holds, for someone choosing sections in the Viewer. */
    public String description() {
        return description;
    }

    /**
     * Parses a section name, case-insensitively.
     *
     * <p>Returns empty rather than throwing so a caller can decide whether an
     * unknown name is a bad request or a stored row written by a later version
     * of Tower. Those deserve different treatment.
     */
    public static Optional<DocumentSection> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (DocumentSection section : values()) {
            if (section.name().equalsIgnoreCase(value.trim())) {
                return Optional.of(section);
            }
        }
        return Optional.empty();
    }
}
