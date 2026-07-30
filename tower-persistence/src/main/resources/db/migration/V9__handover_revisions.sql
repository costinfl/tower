-- V9: Handover revisions (issue #8, IA-02, ADR-016).
--
-- Dialect-neutral SQL only (ADR-009), matching V1 to V8.
--
-- Handover holds the deployment instructions, the shell commands and the rollback procedure - the
-- text someone follows at three in the morning when a release is going wrong. Until this table
-- existed, editing it overwrote what was there, so the first question asked after a bad deployment
-- - what did we actually hand over - had no answer.
--
-- Append-only, for the reason ADR-002 gives for observation: what a team was told on 3 August
-- remains what they were told, whatever is written afterwards. Nothing updates a row here and
-- nothing deletes one, and the outbound port offers neither operation.
--
-- Its own table rather than columns on release_pack, because a Release Pack is loaded on every
-- listing and an unbounded edit history on the aggregate would be read every time to answer a
-- question almost no listing asks. release_pack keeps the current Handover; ADR-016 records why
-- that value deliberately appears in both places and which single use case is allowed to write it.
--
-- No author column. ADR-009 has every developer running their own instance, so the author is always
-- the person reading it and the column would hold the same name on every row.
--
-- ON DELETE CASCADE, considered rather than defaulted, and arrived at by getting it wrong first.
--
-- The protection this table exists to give is that an *edit* never loses history. Deleting the
-- whole Release Pack is a different act, it is explicit, and it already has its own guards - a pack
-- carrying validation history cannot be deleted at all. Blocking the delete here would not have
-- protected a deliberate act; it would have blocked one, and it would have made every Release Pack
-- whose Handover was ever touched permanently undeletable.
--
-- Revisions of a release that no longer exists are also unreachable: nothing can name them once the
-- pack is gone. Keeping them would preserve rows rather than preserve a record.
CREATE TABLE handover_revision (
    id UUID NOT NULL,
    release_pack_id UUID NOT NULL,
    revision_number INTEGER NOT NULL,
    deployment_instructions TEXT NOT NULL,
    shell_commands TEXT NOT NULL,
    database_migrations TEXT NOT NULL,
    rollback_procedure TEXT NOT NULL,
    validation_notes TEXT NOT NULL,
    operational_notes TEXT NOT NULL,
    recorded_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_handover_revision PRIMARY KEY (id),
    -- The number is how a revision is referred to, so two rows must never share one.
    CONSTRAINT uq_handover_revision_number UNIQUE (release_pack_id, revision_number),
    CONSTRAINT fk_handover_revision_release_pack FOREIGN KEY (release_pack_id)
        REFERENCES release_pack (id) ON DELETE CASCADE
);

-- The history screen reads one pack's revisions newest first, and the next revision number comes
-- from the same ordering. Both are covered by this index.
CREATE INDEX ix_handover_revision_pack ON handover_revision (release_pack_id, revision_number DESC);
