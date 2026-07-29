-- V6: Sync Runs (Milestone 2, issue #50).
--
-- Dialect-neutral SQL only (ADR-009), matching V1 to V5.
--
-- ADR-011: a synchronization run appends an Observation only when observed state has changed,
-- which would leave Tower unable to distinguish "still deployed" from "we stopped looking". This
-- table is what closes that gap: every run is recorded whether or not it appended anything, so the
-- Observation says when the version last changed and the run says when Tower last looked and found
-- it unchanged.
--
-- Operational telemetry, not a business fact. It records what Tower did, not what Tower observed
-- about the world, which is why the type it maps to lives in the application layer and never in
-- the Domain Model.
--
-- Deliberately no foreign key to environment or application. A run describes an activity rather
-- than a relationship between Tower's concepts, and the unrecognized rows below name things Tower
-- could not attribute to either - by definition there is nothing to point at.
CREATE TABLE sync_run (
    id UUID NOT NULL,
    connector_id VARCHAR(100) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    workloads_read INTEGER NOT NULL,
    observations_appended INTEGER NOT NULL,
    CONSTRAINT pk_sync_run PRIMARY KEY (id)
);

-- Child rows rather than a JSON column. ADR-009 requires dialect-neutral SQL, which rules out a
-- native JSON type, and storing JSON in TEXT would make the one question a user most wants to ask
-- - "what is running that Tower cannot explain?" - unanswerable without parsing every row in the
-- application.
--
-- position preserves the order the Collector reported them in, so a list read back matches what
-- the run actually saw rather than whatever order the database returns.
CREATE TABLE sync_run_unrecognized (
    sync_run_id UUID NOT NULL,
    position INTEGER NOT NULL,
    scope TEXT,
    name TEXT,
    image_reference TEXT,
    reason TEXT,
    CONSTRAINT pk_sync_run_unrecognized PRIMARY KEY (sync_run_id, position),
    CONSTRAINT fk_sync_run_unrecognized_run FOREIGN KEY (sync_run_id)
        REFERENCES sync_run (id) ON DELETE CASCADE
);

CREATE TABLE sync_run_failure (
    sync_run_id UUID NOT NULL,
    position INTEGER NOT NULL,
    message TEXT NOT NULL,
    CONSTRAINT pk_sync_run_failure PRIMARY KEY (sync_run_id, position),
    CONSTRAINT fk_sync_run_failure_run FOREIGN KEY (sync_run_id)
        REFERENCES sync_run (id) ON DELETE CASCADE
);

-- ON DELETE CASCADE above is safe here in a way it would not be for observation: these rows are
-- parts of a run rather than facts in their own right, so they cannot outlive it meaningfully.
-- Nothing deletes a sync_run today - ADR-011 leaves the retention decision open and the outbound
-- port offers no delete - but when a retention policy arrives it must not have to remember to
-- clear the children by hand.

-- The history screen reads the newest runs first, and the liveness pairing reads the newest
-- successful run for one Connector. Both are covered by this index.
CREATE INDEX ix_sync_run_connector_started ON sync_run (connector_id, started_at DESC);
