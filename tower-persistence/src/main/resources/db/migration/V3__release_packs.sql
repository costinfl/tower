-- V3: Applications, Application Versions and Release Packs (Epic 2, issue #20).
--
-- Dialect-neutral SQL only (ADR-009): no H2-specific or PostgreSQL-specific syntax, so the
-- deferred move to PostgreSQL stays contained to this module.
--
-- TEXT is used for unbounded free-text columns (descriptions, Handover fields, Iteration notes):
-- the domain places no length limit on them, and TEXT is understood natively by both H2 and
-- PostgreSQL, unlike CLOB which PostgreSQL does not support. VARCHAR(n) is reserved for fields
-- the domain itself caps (Application/Release Pack/Iteration names, Application Version version).

-- name_ci follows the V2 generated-column approach: a functional index directly on UPPER(name)
-- is rejected by H2 in PostgreSQL compatibility mode, so case-insensitive uniqueness is expressed
-- as a generated column plus a plain unique index, which is standard SQL PostgreSQL also supports.
CREATE TABLE application (
    id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    name_ci VARCHAR(150) GENERATED ALWAYS AS (UPPER(name)),
    CONSTRAINT pk_application PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_application_name_ci ON application (name_ci);

-- BR-01: Application Versions are immutable once recorded. This table therefore has no
-- updated_at/updated_by style bookkeeping and the adapter (ApplicationVersionJdbcRepository)
-- never issues an UPDATE against it, only INSERT and DELETE.
CREATE TABLE application_version (
    id UUID NOT NULL,
    application_id UUID NOT NULL,
    version VARCHAR(100) NOT NULL,
    branch TEXT,
    tag TEXT,
    commit_ref TEXT,
    build_identifier TEXT,
    CONSTRAINT pk_application_version PRIMARY KEY (id),
    CONSTRAINT fk_application_version_application FOREIGN KEY (application_id)
        REFERENCES application (id) ON DELETE CASCADE,
    -- Mirrors ApplicationService.registerVersion's uniqueness check at the database level.
    CONSTRAINT ux_application_version_app_version UNIQUE (application_id, version)
);

CREATE INDEX ix_application_version_application ON application_version (application_id);

-- SM-05: a Release Pack may exist before it has a Promotion Path, so promotion_path_id and
-- promotion_path_version are nullable together. The check constraint below keeps them paired -
-- either both null (no assignment yet) or both set (ADR-007: a pack pins a specific published
-- version, never merely a path).
CREATE TABLE release_pack (
    id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    promotion_path_id UUID,
    promotion_path_version INT,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    name_ci VARCHAR(150) GENERATED ALWAYS AS (UPPER(name)),
    CONSTRAINT pk_release_pack PRIMARY KEY (id),
    CONSTRAINT ck_release_pack_promotion_path_pair CHECK (
        (promotion_path_id IS NULL AND promotion_path_version IS NULL)
        OR (promotion_path_id IS NOT NULL AND promotion_path_version IS NOT NULL)
    ),
    -- Composite FK against the (path_id, version_number) primary key of promotion_path_version
    -- (V2 migration): a Release Pack can only ever pin a version that was actually published.
    -- A composite FK is not checked when either column is null (SQL MATCH SIMPLE, the default),
    -- which is exactly the "no assignment yet" state the check constraint above allows.
    CONSTRAINT fk_release_pack_promotion_path_version FOREIGN KEY (promotion_path_id, promotion_path_version)
        REFERENCES promotion_path_version (path_id, version_number)
);

CREATE UNIQUE INDEX ux_release_pack_name_ci ON release_pack (name_ci);

-- Supports ReleasePackRepository.findAllReferencingPromotionPath without a full table scan
-- (ADR-007: this is what lets PromotionPathService refuse deletion of a referenced path).
CREATE INDEX ix_release_pack_promotion_path ON release_pack (promotion_path_id);

-- Pack contents (FR-003, FR-004). Unlike promotion_path_version_environment, ordering is not
-- significant here, so there is deliberately no position column.
--
-- The primary key is (release_pack_id, application_id) rather than a synthetic id: the domain
-- rule that a pack holds at most one version of any given Application (ReleasePack.
-- addApplicationVersion) is exactly the uniqueness a primary key on that pair expresses, so the
-- database enforces the same invariant the aggregate already enforces in memory.
CREATE TABLE release_pack_version (
    release_pack_id UUID NOT NULL,
    application_id UUID NOT NULL,
    application_version_id UUID NOT NULL,
    CONSTRAINT pk_release_pack_version PRIMARY KEY (release_pack_id, application_id),
    CONSTRAINT fk_release_pack_version_pack FOREIGN KEY (release_pack_id)
        REFERENCES release_pack (id) ON DELETE CASCADE,
    CONSTRAINT fk_release_pack_version_application FOREIGN KEY (application_id)
        REFERENCES application (id),
    CONSTRAINT fk_release_pack_version_app_version FOREIGN KEY (application_version_id)
        REFERENCES application_version (id)
);

-- Supports ApplicationService.deleteVersion's precondition check (ReleasePackRepository.
-- findAllContaining) without a full table scan.
CREATE INDEX ix_release_pack_version_app_version ON release_pack_version (application_version_id);

-- Handover (FR-006): every Release Pack has exactly one Handover, created empty alongside the
-- pack (Handover.empty()) and replaced wholesale by ReleasePackUseCases.updateHandover. One row
-- per pack, keyed by the pack's own id rather than a synthetic one.
CREATE TABLE release_pack_handover (
    release_pack_id UUID NOT NULL,
    deployment_instructions TEXT NOT NULL DEFAULT '',
    shell_commands TEXT NOT NULL DEFAULT '',
    database_migrations TEXT NOT NULL DEFAULT '',
    rollback_procedure TEXT NOT NULL DEFAULT '',
    validation_notes TEXT NOT NULL DEFAULT '',
    operational_notes TEXT NOT NULL DEFAULT '',
    CONSTRAINT pk_release_pack_handover PRIMARY KEY (release_pack_id),
    CONSTRAINT fk_release_pack_handover_pack FOREIGN KEY (release_pack_id)
        REFERENCES release_pack (id) ON DELETE CASCADE
);

-- Validation Iterations (gap G4). Unlike Handover, a pack may have any number of these (including
-- zero), so each gets its own id and a plain foreign key back to the pack.
CREATE TABLE release_pack_iteration (
    id UUID NOT NULL,
    release_pack_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    notes TEXT NOT NULL DEFAULT '',
    CONSTRAINT pk_release_pack_iteration PRIMARY KEY (id),
    CONSTRAINT fk_release_pack_iteration_pack FOREIGN KEY (release_pack_id)
        REFERENCES release_pack (id) ON DELETE CASCADE
);

CREATE INDEX ix_release_pack_iteration_pack ON release_pack_iteration (release_pack_id);
