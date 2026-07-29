-- V7: Document Templates (Milestone 3, OQ-010).
--
-- Dialect-neutral SQL only (ADR-009), matching V1 to V6.
--
-- ADR-013: a template chooses which sections a release document contains and in what order. It
-- holds no markup, no expressions and no user-authored text, which is why the schema below is a
-- name and a list of section names rather than a body column. NFR-025 requires regenerating an
-- unchanged Release Pack to produce byte-identical output, and that survives only while every byte
-- of the document is written by Tower's own renderers.
--
-- User-owned configuration, not a business fact. Like the binding tables in V5 these rows are
-- freely replaced and deleted, and like them they are excluded from export (ADR-010). Deleting a
-- template loses a preference; it cannot lose a document, because a generated document is
-- disposable (IA-03) and was never stored.
--
-- The complete document has no row here on purpose. It is what Tower generates when nobody has
-- expressed a preference, so it is defined in code and cannot be edited, renamed or deleted -
-- there is always one template that tells the whole truth about a release, whatever anyone has
-- configured.
CREATE TABLE document_template (
    id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT pk_document_template PRIMARY KEY (id),
    CONSTRAINT uq_document_template_name UNIQUE (name)
);

-- A child table with a position column rather than a delimited string, for the same reason V6 uses
-- one: ADR-009 rules out a native JSON type, and packing the list into TEXT would put the ordering
-- - the whole point of the row - beyond the reach of the database.
--
-- position is what makes the order the user chose survive a round trip. Without it the sections
-- would come back in whatever order the database found convenient, and the same template would
-- render differently on different machines. That is exactly the quiet failure NFR-025 exists to
-- prevent, so it is a column rather than an assumption.
--
-- section holds the enum constant name, not an ordinal. Ordinals would silently re-point every
-- stored row the moment a section is inserted into the middle of the enum.
CREATE TABLE document_template_section (
    document_template_id UUID NOT NULL,
    position INTEGER NOT NULL,
    section VARCHAR(50) NOT NULL,
    CONSTRAINT pk_document_template_section PRIMARY KEY (document_template_id, position),
    CONSTRAINT uq_document_template_section UNIQUE (document_template_id, section),
    CONSTRAINT fk_document_template_section_template FOREIGN KEY (document_template_id)
        REFERENCES document_template (id) ON DELETE CASCADE
);

-- ON DELETE CASCADE is safe here in the way it is in V6 and unlike V4: these rows are parts of a
-- template rather than facts in their own right, and cannot outlive it meaningfully.
