-- V2: Environments and Promotion Paths (Epic 1, issue #12).
--
-- Dialect-neutral SQL only (ADR-009): no H2-specific or PostgreSQL-specific syntax, so the
-- deferred move to PostgreSQL stays contained to this module.
--
-- ADR-005: Environment <-> Promotion Path is many-to-many, expressed at the version level.
-- promotion_path_version_environment is a join table carrying an explicit env_position column
-- for the ordered sequence; environment carries no reference back to any path or path version,
-- so the same Environment id can appear under any number of paths/versions.
--
-- ADR-007: a Promotion Path version is immutable once published and versions are numbered
-- consecutively from 1 per path. version_number is part of the composite primary key together
-- with path_id and a check constraint keeps it at 1 or above. True "no gaps" consecutiveness
-- cannot be expressed as a portable check constraint, so it is enforced procedurally: the
-- persistence adapter only ever inserts version_number = (current version count for the path) +
-- 1, and PromotionPath.reconstitute (domain layer) re-validates consecutive numbering on every
-- load, raising a DomainException if that is ever violated.

-- name_ci is a generated (virtual, not stored) column rather than a functional index directly
-- on UPPER(name): H2 in PostgreSQL compatibility mode rejects an expression inside CREATE INDEX,
-- and a generated column plus a plain unique index on it is standard SQL that PostgreSQL also
-- supports, so this stays portable ahead of the ADR-009 migration.
CREATE TABLE environment (
    id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    stage VARCHAR(30) NOT NULL,
    name_ci VARCHAR(100) GENERATED ALWAYS AS (UPPER(name)),
    CONSTRAINT pk_environment PRIMARY KEY (id)
);

-- Case-insensitive uniqueness (issue #12 requirement), enforced at the database level rather
-- than relying solely on the application-layer check in EnvironmentService.
CREATE UNIQUE INDEX ux_environment_name_ci ON environment (name_ci);

CREATE TABLE promotion_path (
    id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    name_ci VARCHAR(100) GENERATED ALWAYS AS (UPPER(name)),
    CONSTRAINT pk_promotion_path PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_promotion_path_name_ci ON promotion_path (name_ci);

CREATE TABLE promotion_path_version (
    path_id UUID NOT NULL,
    version_number INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_promotion_path_version PRIMARY KEY (path_id, version_number),
    CONSTRAINT fk_ppv_path FOREIGN KEY (path_id)
        REFERENCES promotion_path (id) ON DELETE CASCADE,
    CONSTRAINT ck_ppv_version_number_positive CHECK (version_number >= 1)
);

-- The ordered Environment references for one Promotion Path version (ADR-005). env_position is
-- zero-based and, together with (path_id, version_number), fixes the sequence exactly as
-- PromotionPathVersion.environments() returns it.
CREATE TABLE promotion_path_version_environment (
    path_id UUID NOT NULL,
    version_number INT NOT NULL,
    env_position INT NOT NULL,
    environment_id UUID NOT NULL,
    CONSTRAINT pk_ppve PRIMARY KEY (path_id, version_number, env_position),
    CONSTRAINT fk_ppve_version FOREIGN KEY (path_id, version_number)
        REFERENCES promotion_path_version (path_id, version_number) ON DELETE CASCADE,
    CONSTRAINT fk_ppve_environment FOREIGN KEY (environment_id)
        REFERENCES environment (id),
    CONSTRAINT ck_ppve_env_position_non_negative CHECK (env_position >= 0),
    -- Mirrors the domain rule (PromotionPathVersion) that one Environment may not appear twice
    -- within the same version.
    CONSTRAINT ux_ppve_no_duplicate_environment UNIQUE (path_id, version_number, environment_id)
);

-- Supports EnvironmentRepository.deleteById's precondition check (findAllReferencing) without a
-- full table scan.
CREATE INDEX ix_ppve_environment ON promotion_path_version_environment (environment_id);
