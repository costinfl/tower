-- V4: Observations (Epic 3, issues #21 to #24).
--
-- Dialect-neutral SQL only (ADR-009): no H2-specific or PostgreSQL-specific syntax, so the
-- deferred move to PostgreSQL stays contained to this module.
--
-- ADR-002, BR-02, FR-023: an Observation is an immutable, append-only fact. This table therefore
-- has no updated_at/updated_by style bookkeeping, and ObservationJdbcRepository (the adapter for
-- ObservationRepository) only ever issues INSERT and SELECT against it, never UPDATE or DELETE -
-- the port itself offers no method that would let it.
--
-- source_collector, source_actor and source_origin_instance mirror ObservationSource. TEXT is
-- used for all three, matching the V3 convention of TEXT for free-text columns the domain places
-- no length limit on; only source_collector is NOT NULL, since actor and originInstance are both
-- optional in the domain (null for an automated Collector's actor, null for a fact this instance
-- observed itself rather than imported).
--
-- Foreign key cascade behaviour, considered deliberately rather than defaulted without thought:
-- none of the three foreign keys below cascades on delete. An Observation is a historical fact
-- (ADR-002); once recorded, ObservationRepository offers no update and no delete, so the only way
-- to correct a mistaken one is to record a later Observation that supersedes it (BR-02, FR-023).
-- ON DELETE CASCADE here would silently destroy that history the moment someone deleted the
-- Environment, Application or Application Version it refers to - exactly the kind of rewrite
-- Observations exist to make impossible. The default (RESTRICT/NO ACTION) instead blocks such a
-- deletion outright once an Observation references the row, the same way ApplicationService
-- already refuses to delete an Application Version a Release Pack still contains. Concretely: an
-- Environment, Application or Application Version that has ever been observed can never be
-- deleted again, only new facts can be recorded about it. That is the correct trade-off for data
-- the system promises never to lose.
CREATE TABLE observation (
    id UUID NOT NULL,
    environment_id UUID NOT NULL,
    application_id UUID NOT NULL,
    application_version_id UUID NOT NULL,
    observed_at TIMESTAMP NOT NULL,
    source_collector TEXT NOT NULL,
    source_actor TEXT,
    source_origin_instance TEXT,
    CONSTRAINT pk_observation PRIMARY KEY (id),
    CONSTRAINT fk_observation_environment FOREIGN KEY (environment_id)
        REFERENCES environment (id),
    CONSTRAINT fk_observation_application FOREIGN KEY (application_id)
        REFERENCES application (id),
    CONSTRAINT fk_observation_application_version FOREIGN KEY (application_version_id)
        REFERENCES application_version (id)
);

-- Supports ObservationRepository.findAllInEnvironment - the query behind both EnvironmentState
-- derivation (FR-010 to FR-013) and Observation history (FR-021) - without a full table scan.
CREATE INDEX ix_observation_environment ON observation (environment_id);

-- Supports ObservationRepository.findAllOfVersions - the query behind Release Pack state
-- derivation (ADR-008) - which fetches Observations for a set of Application Versions at once.
CREATE INDEX ix_observation_application_version ON observation (application_version_id);
