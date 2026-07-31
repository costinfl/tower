-- V10: Work item references (ADR-018).
--
-- Dialect-neutral SQL only (ADR-009), matching V1 to V9.
--
-- What a Release Pack claims to deliver, in the vocabulary the people receiving a handover use.
-- This is Intent (IA-05): the team said it. Tower did not observe it, and nothing in this table
-- is an Observation - the Issue Tracking Connector produces none, which is what ADR-018 records
-- and why Connector-Model.md's "every Connector ultimately produces Observations" is corrected
-- rather than worked around.
--
-- identifier is written exactly as the tracker writes it and is not parsed: "PROJ-123", "#42",
-- "GH-7". Nothing in Tower splits it into a project and a number, because the moment it did, the
-- column would be Jira-shaped and Principles.md would stop holding - replacing Jira with Azure
-- DevOps must not require changes to the domain model.
--
-- title is the summary a person ACCEPTED from the tracker, not a cached copy Tower refreshes.
-- That distinction is the whole of ADR-018 and the reason NFR-025 survives: a generated document
-- reads nothing external, so regenerating an unchanged Release Pack still produces the same bytes
-- after somebody rewrites a ticket summary. The tracker's current wording is read on request and
-- shown beside this one; where they differ the Viewer says so. Tower does not correct itself
-- silently, because a handover already given to another team does not change because a ticket
-- was edited.
--
-- title is nullable-by-emptiness rather than NULL: a reference may be linked before any Connector
-- is configured, or to a tracker Tower cannot reach, and an empty title means "nobody has accepted
-- one" rather than "the item has no summary". Stored as '' so every read gets a String and no
-- caller has to decide what NULL means.
--
-- No status column, and none is coming. Status is what the tracker is for; storing it would make
-- Tower a second issue tracker that is wrong more often than it is right, and Guardrails.md lists
-- "a replacement for Jira" among the things Tower must never become. The Viewer reads status live
-- and stores nothing.
--
-- No connector column either. The tracker is bound once per Connector (ADR-018), not per reference:
-- a work item belongs to a release rather than to one Application, so there is one tracker named
-- once. Recording a connector here would invite two trackers per release and a per-row question
-- nobody has asked to answer.
--
-- ON DELETE CASCADE matches release_pack_version and release_pack_iteration in V3. A reference
-- describes one release; once that release is gone it describes nothing. Deleting a Release Pack
-- is already guarded against losing validation history, and the guard belongs there rather than
-- here (ADR-016 records the same reasoning for Handover revisions, after blocking the delete
-- turned out to be the wrong call).
--
-- The unique constraint is on (release_pack_id, LOWER(identifier)) in spirit, and on the plain
-- pair in SQL: dialect-neutral SQL has no portable functional index, so case-insensitive
-- uniqueness is enforced in ReleasePack.linkWorkItem, which is where the rule belongs anyway and
-- where the message can name the identifier already present. This constraint is the backstop that
-- catches an exact duplicate reaching the table by any other route.

CREATE TABLE release_pack_work_item (
    release_pack_id UUID NOT NULL,
    identifier      VARCHAR(100) NOT NULL,
    title           VARCHAR(500) NOT NULL DEFAULT '',
    position        INTEGER NOT NULL,
    CONSTRAINT pk_release_pack_work_item PRIMARY KEY (release_pack_id, identifier),
    CONSTRAINT fk_release_pack_work_item_pack FOREIGN KEY (release_pack_id)
        REFERENCES release_pack (id) ON DELETE CASCADE
);

-- position preserves the order the team linked them in, which is the order a document lists them.
-- Without it the order would be whatever the database happened to return, and NFR-025 requires a
-- generated document to be byte-identical on regeneration - an unordered read would break that
-- without any change to the release at all.
CREATE INDEX idx_release_pack_work_item_order ON release_pack_work_item (release_pack_id, position);
