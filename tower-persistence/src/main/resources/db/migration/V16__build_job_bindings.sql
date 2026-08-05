-- V16: Build job bindings (ADR-012, ADR-020).
--
-- Dialect-neutral SQL only (ADR-009), matching V1 to V15.
--
-- Which job's runs build an Application. The sibling of V12, and the difference between the two
-- tables is the whole of ADR-020: that one names an Environment, this one does not. A build says
-- "version 2.5.0 was produced from this job", which is not a fact about any Environment, so there
-- is nothing to put on the left but the Application.
--
-- Why this is not V12 with a nullable environment_id. A null there would have to mean "this run
-- deployed nothing anywhere", and every query over deployments would have to remember to exclude
-- it - the kind of rule that is obeyed until somebody writes the query that forgets. It would also
-- stop V12's primary key being a key: two build jobs for one Application are ordinary (a team may
-- build a service and its migrations separately), while V12 exists precisely to forbid two
-- deployment jobs for one Environment and Application, because both would claim to say when it
-- arrived and nothing would say which to believe.
--
-- The primary key is (application_id, connector_id, job). The job is in the key, which is what
-- allows those two build jobs; V12 could not do that, because there the pair on the left is what
-- must be unique.
--
-- version_source, version_key and version_pattern carry the same meanings as V12's, and a build job
-- keeps its version in the same small set of places a deployment job does. ADR-020's refusal
-- applies unchanged: Tower will not read a console log to find it.
--
-- The consequence of a wrong pattern is milder here than anywhere else in the schema, and that is
-- worth recording because it is the opposite of V12's warning. A wrong pattern on a deployment job
-- writes Observations that are immutable and outlive the correction. A wrong pattern here produces
-- no candidate, and a candidate is a proposal nobody has accepted - nothing is written, so nothing
-- survives the mistake.
--
-- ci_system carries a prefix for the reason V12's does: SYSTEM is reserved or semi-reserved in
-- enough dialects to be worth stepping around under ADR-009. It is the key the credential store
-- uses (NFR-028), so one token serves every job on the same server, deployment jobs included.
--
-- No credential column, matching V5, V8, V11, V12 and V14.
--
-- Nothing this binding leads to is ever stored. A build run produces a candidate Application
-- Version, proposed and stored nowhere (ADR-020, on the footing ADR-014 set for a git ref), so
-- there is no table of build runs anywhere in this schema and there is not meant to be one. Whether
-- a candidate becomes an Application Version is a person's decision, taken through the ordinary
-- creation path, which is what keeps BR-01 in one place.
--
-- Foreign key to application, cascading, because a build job for a deleted Application builds
-- nothing.

CREATE TABLE build_job_binding (
    application_id  UUID NOT NULL,
    connector_id    VARCHAR(100) NOT NULL,
    ci_system       TEXT NOT NULL,
    job             VARCHAR(500) NOT NULL,
    version_source  VARCHAR(20) NOT NULL,
    version_key     VARCHAR(200) NOT NULL,
    version_pattern TEXT NOT NULL,
    CONSTRAINT pk_build_job_binding PRIMARY KEY (application_id, connector_id, job),
    CONSTRAINT fk_build_job_binding_application FOREIGN KEY (application_id)
        REFERENCES application (id) ON DELETE CASCADE
);

-- Discovery iterates every build job one Connector is responsible for, which the primary key
-- cannot serve because it leads with the Application. The same index V8, V12 and V14 have.
CREATE INDEX ix_build_job_binding_connector ON build_job_binding (connector_id);
