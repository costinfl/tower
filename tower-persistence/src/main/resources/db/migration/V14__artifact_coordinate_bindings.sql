-- V14: Artifact coordinate bindings (ADR-012, ADR-021, FR-083).
--
-- Dialect-neutral SQL only (ADR-009), matching V1 to V13.
--
-- How to address one kind of artifact an Application Version produced. This is the only binding in
-- the schema that runs in the composing direction: V5, V8 and V12 all carry a version_pattern that
-- extracts an Application Version from a string a vendor produced, and this carries a template that
-- builds a string a vendor will recognise out of a version Tower already holds. ADR-021 records the
-- symmetry so that a reader does not take this table for a mistaken copy of one of those.
--
-- The primary key is (application_id, connector_id, kind). Three artifact kinds on one Application
-- is the ordinary case rather than an edge: a build in the setting this was written for publishes
-- the application, an image and a chart, and a team may have all three, any two, or one. The kind
-- distinguishes them and nothing else does, which is why it is in the key.
--
-- kind is the team's own word, stored as they typed it except for case. Tower never interprets it -
-- the same restraint V10 applies to a work item's status. Case is folded because a kind is a key a
-- person types twice, once when binding and once when reading a report, and "Image" beside "image"
-- would be two rows saying the same thing.
--
-- coordinate_template carries {version}, {commit} and {shortCommit} standing in for what Tower
-- holds. It is TEXT rather than a bounded type for the same reason version_pattern is: a Docker
-- coordinate with a long registry host and a path is not short, and there is no length at which
-- refusing one would be doing a user a favour.
--
-- short_commit_length exists because "the short commit" is not a fixed thing. Seven characters is
-- what git rev-parse --short gives by default, but git lengthens it where seven would be ambiguous
-- and a pipeline may have pinned another length years ago. A team whose build uses a different one
-- and does not say so here sees every artifact reported absent - which is the mild failure ADR-021
-- describes, and the reason a default is safe at all.
--
-- repository_system is the repository's address and, as in V12, is deliberately repeated per row
-- rather than normalised into a table of servers. It is the key the credential store uses
-- (NFR-028), so one token serves every artifact in the same repository. The column carries a prefix
-- for the same reason V12's ci_system does: SYSTEM is reserved or semi-reserved in enough dialects
-- to be worth stepping around under ADR-009.
--
-- No credential column, matching V5, V8, V11 and V12.
--
-- Nothing this binding leads to is ever stored. FR-084 is explicit: an artifact has no Environment,
-- so it is not an Observation, and nobody stated it, so it is not User-Owned Information. There is
-- consequently no artifact table anywhere in this schema and there is not meant to be one. What a
-- release document prints is an accepted digest, which is a separate thing a person stated.
--
-- Foreign key to application, cascading, because a template for a deleted Application addresses
-- nothing.

CREATE TABLE artifact_coordinate_binding (
    application_id      UUID NOT NULL,
    connector_id        VARCHAR(100) NOT NULL,
    kind                VARCHAR(50) NOT NULL,
    repository_system   TEXT NOT NULL,
    coordinate_template TEXT NOT NULL,
    short_commit_length INTEGER NOT NULL,
    CONSTRAINT pk_artifact_coordinate_binding PRIMARY KEY (application_id, connector_id, kind),
    CONSTRAINT fk_artifact_coordinate_binding_application FOREIGN KEY (application_id)
        REFERENCES application (id) ON DELETE CASCADE
);

-- Confirming artifacts iterates every template one Connector is responsible for, which the primary
-- key cannot serve because it leads with the Application. The same index V8 and V12 have.
CREATE INDEX ix_artifact_coordinate_binding_connector ON artifact_coordinate_binding (connector_id);
