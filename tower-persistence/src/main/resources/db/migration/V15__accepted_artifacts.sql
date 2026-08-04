-- V15: Accepted artifact digests (ADR-021, FR-086).
--
-- Dialect-neutral SQL only (ADR-009), matching V1 to V14.
--
-- The only table the Artifact Repository Connector ever leads to. FR-084 says nothing an
-- Artifact Repository Connector reads is stored, and that stands: what is here is not what the
-- Connector read, it is what a person accepted after reading it. The distinction is the same one
-- V10 makes for a work item's title, and it is the whole reason this table can exist without
-- contradicting the requirement two lines above it.
--
-- Why it has to exist at all: a tag is mutable. 2.5.0-abc1234 can be pushed over and the bytes
-- beneath it change. A release document that printed whatever the repository said at the moment
-- of rendering would stop regenerating byte-identically the day somebody re-pushed, which NFR-025
-- forbids. So a document prints this, and the repository's current answer is shown beside it.
--
-- The primary key is (application_version_id, kind). One accepted digest per kind per version,
-- replaced rather than appended when a person accepts a newer one. That is deliberate and unlike
-- observation, which appends: the record of what a release shipped is the digest somebody stands
-- behind, not the sequence of times they looked. A re-pushed tag is made visible by comparing this
-- against what the repository reports now, which needs one row rather than a history.
--
-- coordinate is stored as well as the digest, and not as decoration. A document naming a digest
-- without saying where it was found would send a reader nowhere, and a template corrected after
-- acceptance would otherwise leave the record unreadable.
--
-- digest is stored exactly as the repository spelled it - sha256:... for a container image, a
-- checksum for a file - and is never parsed. Tower only ever compares it for equality, which is
-- what lets the repository be replaced without touching this table.
--
-- accepted_at is when a person accepted it, which is neither when the repository received the
-- bytes nor when the build produced them. Three different instants, and only this one belongs to
-- Tower.
--
-- No connector_id column. What is accepted is a fact about bytes, not about which Connector
-- happened to report them, and a team that moved repositories would not thereby un-accept what
-- they shipped.
--
-- Foreign key to application_version, cascading: an accepted digest for a version that no longer
-- exists describes nothing.

CREATE TABLE accepted_artifact (
    application_version_id UUID NOT NULL,
    kind                   VARCHAR(50) NOT NULL,
    coordinate             VARCHAR(500) NOT NULL,
    digest                 VARCHAR(200) NOT NULL,
    accepted_at            TIMESTAMP NOT NULL,
    CONSTRAINT pk_accepted_artifact PRIMARY KEY (application_version_id, kind),
    CONSTRAINT fk_accepted_artifact_version FOREIGN KEY (application_version_id)
        REFERENCES application_version (id) ON DELETE CASCADE
);
