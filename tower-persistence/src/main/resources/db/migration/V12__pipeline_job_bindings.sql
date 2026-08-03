-- V12: Pipeline job bindings (ADR-012, ADR-020, FR-079).
--
-- Dialect-neutral SQL only (ADR-009), matching V1 to V11.
--
-- Which job's runs mean that an Application reached an Environment. The widest binding in the
-- schema, and it has to be: V5 maps an Environment to a namespace, V5 and V8 map an Application
-- to an image or a repository, and this maps both at once, because that is what a run of a
-- deployment job actually asserts - this Application arrived in that Environment.
--
-- The primary key is (environment_id, application_id, connector_id). One job per pair per
-- Connector, enforced here rather than in a service that could be bypassed. Two different jobs
-- deploying the same Application to the same Environment is a situation Tower would have no way
-- to reconcile: both would claim to say when it arrived, and nothing would say which to believe.
--
-- version_source and version_key exist because a CI system used as most of them actually are does
-- not record the version anywhere a Connector could guess. ADR-020 records the refusal that makes
-- these columns necessary: Tower will not read the console log to find it, because that would make
-- correctness depend on log formatting and a wrong parse produces Observations that are immutable.
-- So the place is configuration, and these are the places a machine can read without guessing -
-- a named value the run carried, the run's own name, or the job's path.
--
-- version_key is empty except when version_source is PARAMETER. Not null: an absent name and an
-- empty one would mean the same thing here, and a nullable column invites a reader to wonder
-- whether they differ.
--
-- ci_system is the CI server's address and is deliberately repeated per row rather than normalised
-- into a table of servers. It matches V5's target column, and it is the key the credential store
-- uses (NFR-028), so one token serves every job on the same server. A table of servers would buy
-- nothing here except a join and a second screen. The column carries a prefix because SYSTEM is
-- reserved or semi-reserved in enough dialects to be worth stepping around under ADR-009.
--
-- No credential column, matching V5, V8 and V11.
--
-- Foreign keys to environment and application, both cascading, because a binding for a deleted
-- Environment describes nothing. That is the opposite of V11's deliberate lack of one: this
-- binding names Tower's own rows on the left, where an issue tracker binding names only a
-- Connector, which is code rather than data.
--
-- Nothing here cascades into observation. Observations recorded from earlier runs are facts about
-- what was reported at the time (ADR-002) and survive the binding being changed or removed - the
-- same rule V5 states for a re-pointed namespace.

CREATE TABLE pipeline_job_binding (
    environment_id  UUID NOT NULL,
    application_id  UUID NOT NULL,
    connector_id    VARCHAR(100) NOT NULL,
    ci_system       TEXT NOT NULL,
    job             TEXT NOT NULL,
    version_source  VARCHAR(20) NOT NULL,
    version_key     VARCHAR(200) NOT NULL,
    version_pattern TEXT NOT NULL,
    CONSTRAINT pk_pipeline_job_binding PRIMARY KEY (environment_id, application_id, connector_id),
    CONSTRAINT fk_pipeline_job_binding_environment FOREIGN KEY (environment_id)
        REFERENCES environment (id) ON DELETE CASCADE,
    CONSTRAINT fk_pipeline_job_binding_application FOREIGN KEY (application_id)
        REFERENCES application (id) ON DELETE CASCADE
);

-- Synchronization iterates every job one Connector is responsible for, which the primary key
-- cannot serve because it leads with the Environment. The same index V8 has, for the same reason.
CREATE INDEX ix_pipeline_job_binding_connector ON pipeline_job_binding (connector_id);
