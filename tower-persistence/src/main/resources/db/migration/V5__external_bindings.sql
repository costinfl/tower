-- V5: External Bindings (Milestone 2, issue #47).
--
-- Dialect-neutral SQL only (ADR-009), matching V1 to V4.
--
-- ADR-012: these tables hold the correspondence between Tower's concepts and a vendor's
-- locators. They exist so that Environment and Application do not have to. A namespace column on
-- the environment table would have been smaller and would have coupled the Canonical Model to a
-- Deployment Platform, which CM-03, CM-05, FR-037 and ADR-003 all forbid.
--
-- Nothing here is a business fact, so unlike observation these rows are freely replaced and
-- deleted. Re-pointing an Environment at a different namespace is an ordinary correction. It does
-- not rewrite history: Observations recorded under a previous binding remain facts about what was
-- seen, because an Observation records what was observed and not the configuration that led Tower
-- to look there.
--
-- Credentials are deliberately absent. The target column names an endpoint, and the secret for
-- that endpoint lives in the credential store in the Tower data directory (NFR-028), never in the
-- database and never in an export (ADR-010).
--
-- Foreign key cascade behaviour, considered rather than defaulted: ON DELETE CASCADE is correct
-- here, and is the opposite of the choice made in V4 for observation. A binding is configuration
-- describing where to observe an Environment; once that Environment is gone the binding describes
-- nothing and keeping it would be an orphan the user cannot see or remove. This is safe precisely
-- because a binding carries no history - deleting one loses a setting, not a fact. Note that an
-- Environment that has ever been observed cannot be deleted at all, since V4's observation foreign
-- keys block it, so this cascade only ever fires for an Environment with no Observations.
CREATE TABLE environment_binding (
    environment_id UUID NOT NULL,
    connector_id VARCHAR(100) NOT NULL,
    target TEXT NOT NULL,
    scope TEXT NOT NULL,
    CONSTRAINT pk_environment_binding PRIMARY KEY (environment_id, connector_id),
    CONSTRAINT fk_environment_binding_environment FOREIGN KEY (environment_id)
        REFERENCES environment (id) ON DELETE CASCADE
);

-- version_pattern is a regular expression with at least one capturing group; the first group is
-- the Application Version (ADR-012, FR-056). It is validated in ApplicationBinding before it ever
-- reaches this table, so an unparseable pattern is rejected at the point of saving rather than
-- during a synchronization run, where the failure would be remote from the mistake.
CREATE TABLE application_binding (
    application_id UUID NOT NULL,
    connector_id VARCHAR(100) NOT NULL,
    image TEXT NOT NULL,
    version_pattern TEXT NOT NULL,
    CONSTRAINT pk_application_binding PRIMARY KEY (application_id, connector_id),
    CONSTRAINT fk_application_binding_application FOREIGN KEY (application_id)
        REFERENCES application (id) ON DELETE CASCADE
);

-- A synchronization run loads every binding for one Connector at a time (ADR-011), so both
-- lookups are by connector_id rather than by the Tower concept. The primary keys above lead with
-- the concept and cannot serve these.
CREATE INDEX ix_environment_binding_connector ON environment_binding (connector_id);
CREATE INDEX ix_application_binding_connector ON application_binding (connector_id);
