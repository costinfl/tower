-- V11: Issue tracker bindings (ADR-018).
--
-- Dialect-neutral SQL only (ADR-009), matching V1 to V10.
--
-- Where a Connector should look for work items. The odd binding out: V5 keys on an Environment,
-- V5 and V8 key on an Application, and this keys on the Connector alone. ADR-018 records why -
-- a work item belongs to a release rather than to one Application, and a team has one tracker.
-- Keying on an Application would ask the same question once per Application and invite three
-- answers where there is one; keying on a Release Pack would ask it again every release.
--
-- connector_id is therefore the whole primary key, which enforces "one tracker per Connector"
-- in the table rather than in a service that could be bypassed.
--
-- locator is opaque and is never parsed outside the Connector: "owner/repo" for GitHub, a site
-- for Jira. The moment anything above the Connector split it, this column would be GitHub-shaped
-- and Principles.md would stop holding - replacing Jira with Azure DevOps must not require
-- changes to the domain model. That is also why there is no project column, no site column and
-- no host column: one field the Connector understands, and nothing else pretending to.
--
-- No credential column, matching V5 and V8. A tracker's token lives in the credential store in
-- the Tower data directory (NFR-028), keyed by connector and locator - never in the database
-- beside the configuration pointing at it, and never in an export (ADR-010).
--
-- No foreign key, because there is nothing in Tower to point at. Every other binding references
-- a row this schema owns; this one references a Connector, which is code rather than data. A
-- binding naming a Connector that is not installed is an ordinary state - the configuration
-- survives the Connector being absent, and WorkItemService reports it as "no Connector named
-- this is installed" rather than losing the setting.
--
-- Nothing cascades into this table and nothing cascades out of it. Deleting a Release Pack does
-- not unbind a tracker, and unbinding a tracker does not touch a single work item reference:
-- those are Intent the team stated and they outlive any Connector configuration. That
-- independence is the point - a release keeps saying what it delivers whether or not Tower can
-- currently reach the tracker.

CREATE TABLE issue_tracker_binding (
    connector_id VARCHAR(100) NOT NULL,
    locator      VARCHAR(500) NOT NULL,
    CONSTRAINT pk_issue_tracker_binding PRIMARY KEY (connector_id)
);
