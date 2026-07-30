-- V8: Repository Bindings (issue #3, ADR-014).
--
-- Dialect-neutral SQL only (ADR-009), matching V1 to V7.
--
-- The source-control half of ADR-012. V5 records which running image is an Application; this
-- records where that Application's code lives. Two bindings rather than more columns on one,
-- because they answer different questions and a team may configure either without the other -
-- an Application observed in a cluster but not yet pointed at a repository is an ordinary state,
-- not an incomplete row.
--
-- repository_url is a git remote and nothing more (ADR-014). Whether it points at GitHub, GitLab,
-- Bitbucket, a self-hosted server or a bare repository on a file share is not recorded, because
-- nothing in Tower needs to know: one Connector reads all of them.
--
-- ref_selection is stored as its enum constant name rather than an ordinal, for the same reason
-- V7 stores section names that way: an ordinal silently re-points every stored row the moment a
-- constant is inserted into the middle of the enum.
--
-- version_pattern is a regular expression with at least one capturing group; the first group is
-- the Application Version. Validated in RepositoryBinding before it reaches this table, so an
-- unparseable pattern is rejected where the mistake was made rather than during a discovery run.
--
-- No credential column, deliberately. A private repository's secret lives in the credential store
-- in the Tower data directory (NFR-028), keyed by connector and URL - never in the database beside
-- the configuration pointing at it, and never in an export (ADR-010).
--
-- ON DELETE CASCADE matches V5 and is correct for the same reason: a binding is configuration
-- describing where to look for an Application, and once that Application is gone the binding
-- describes nothing. Deleting one loses a setting, not a fact. Application Versions already
-- registered from this repository are untouched - they are facts about what was released, and BR-01
-- makes them immutable regardless of how they came to be recorded.
CREATE TABLE repository_binding (
    application_id UUID NOT NULL,
    connector_id VARCHAR(100) NOT NULL,
    repository_url TEXT NOT NULL,
    ref_selection VARCHAR(20) NOT NULL,
    version_pattern TEXT NOT NULL,
    CONSTRAINT pk_repository_binding PRIMARY KEY (application_id, connector_id),
    CONSTRAINT fk_repository_binding_application FOREIGN KEY (application_id)
        REFERENCES application (id) ON DELETE CASCADE
);

-- Discovery reads one Application's binding at a time, which the primary key already serves. This
-- index is for the other direction: listing every repository one Connector is responsible for,
-- which is what a Connectors screen shows and what a future scheduled discovery would iterate.
CREATE INDEX ix_repository_binding_connector ON repository_binding (connector_id);
