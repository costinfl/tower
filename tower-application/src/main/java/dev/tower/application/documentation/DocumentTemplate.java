package dev.tower.application.documentation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import dev.tower.application.service.InvalidRequestException;

/**
 * Which sections a release document contains, and in what order (OQ-010,
 * ADR-013, FR-062 to FR-065).
 *
 * <p>A template selects from {@link DocumentSection} and orders it. It carries
 * no markup, no expressions and no user-authored text. ADR-013 records the
 * reasoning in full; the short version is NFR-025, which requires regenerating
 * an unchanged Release Pack to produce byte-identical output. Rendering that
 * stays in Tower's own code keeps that guarantee; a template engine is the usual
 * way it dies quietly.
 *
 * <p>User-owned configuration, so this lives in the application layer beside
 * External Bindings rather than in the Domain Model, and like them it is
 * excluded from export (ADR-010).
 *
 * <p>Order is a {@link List} rather than a set, and deliberately so. Sections
 * render in the order held here, and a collection with unspecified iteration
 * order would make the same template produce different bytes on different runs
 * — precisely the failure NFR-025 exists to prevent.
 */
public record DocumentTemplate(DocumentTemplateId id, String name, List<DocumentSection> sections) {

    /**
     * Identity of the complete document.
     *
     * <p>A fixed value rather than a null id, so every caller — the API, the
     * Viewer, the renderers — handles one shape of template instead of two. It
     * is never written to the database: {@code DocumentTemplateService} refuses
     * to store or delete it, because the complete document is what Tower
     * produces when nobody has expressed a preference, not a preference someone
     * expressed.
     */
    public static final DocumentTemplateId COMPLETE_ID =
            new DocumentTemplateId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    /** Reserved so a stored template cannot shadow the built-in in a picker. */
    public static final String COMPLETE_NAME = "Complete document";

    /** Longest a template name may be, matching the column that stores it. */
    public static final int MAX_NAME_LENGTH = 100;

    public DocumentTemplate {
        if (id == null) {
            throw new InvalidRequestException("A Document Template must have an identity.");
        }
        name = requireName(name);
        sections = requireSections(sections);
    }

    /**
     * Every section, in the order {@link DocumentSection} declares them.
     *
     * <p>What Tower generates when no template is chosen, and what it generated
     * before templates existed — so an existing document keeps rendering
     * byte-for-byte as it did (NFR-025).
     */
    public static DocumentTemplate complete() {
        return new DocumentTemplate(COMPLETE_ID, COMPLETE_NAME, Arrays.asList(DocumentSection.values()));
    }

    public boolean isComplete() {
        return sections.size() == DocumentSection.values().length;
    }

    public boolean isBuiltIn() {
        return COMPLETE_ID.equals(id);
    }

    public boolean includes(DocumentSection section) {
        return sections.contains(section);
    }

    /**
     * The sections this template leaves out, in declaration order.
     *
     * <p>Not decoration. The renderers name these in the document's closing
     * note, so a reader can tell "no Iterations have been recorded" from
     * "Iterations were not included in this document". The existing renderers
     * already state absent information rather than omitting it; a template that
     * could silently drop a section would have broken that rule at the one
     * moment it matters — when someone is deciding whether a release is ready.
     */
    public List<DocumentSection> omitted() {
        List<DocumentSection> missing = new ArrayList<>();
        for (DocumentSection section : DocumentSection.values()) {
            if (!sections.contains(section)) {
                missing.add(section);
            }
        }
        return List.copyOf(missing);
    }

    private static String requireName(String value) {
        InvalidRequestException.require(value != null && !value.isBlank(),
                "A Document Template must have a name.");
        String trimmed = value.trim();
        InvalidRequestException.require(trimmed.length() <= MAX_NAME_LENGTH,
                "A Document Template name may be at most " + MAX_NAME_LENGTH + " characters.");
        return trimmed;
    }

    private static List<DocumentSection> requireSections(List<DocumentSection> value) {
        InvalidRequestException.require(value != null && !value.isEmpty(),
                "A Document Template must include at least one section. A document with none would "
                        + "be a title and a footer, which is a mistake rather than a preference.");
        // Looped rather than value.contains(null), which throws on an immutable
        // list instead of answering false — so the caller would have seen a
        // NullPointerException where a plain rejection was intended.
        for (DocumentSection section : value) {
            InvalidRequestException.require(section != null,
                    "A Document Template cannot include an unknown section.");
        }

        // Rejected rather than de-duplicated. Rendering Contents twice is not a
        // layout someone wanted; it is a mistake, and silently repairing it
        // would leave the stored template disagreeing with the one they saved.
        InvalidRequestException.require(EnumSet.copyOf(value).size() == value.size(),
                "A Document Template may include each section at most once.");

        return List.copyOf(value);
    }
}
