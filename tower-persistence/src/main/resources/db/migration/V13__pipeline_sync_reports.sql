-- V13: Pipeline synchronization reports (ADR-020, FR-078, FR-080).
--
-- Dialect-neutral SQL only (ADR-009), matching V1 to V12.
--
-- The sibling of V6's sync_run, and deliberately a second table rather than more columns on that
-- one. A sync_run records reading a Deployment Platform, which reports what is running now; this
-- records reading a CI system, which reports what happened. SyncRun.confirmsLiveness() is true of
-- any successful run and the Connectors screen turns it into "everything Tower already knew was
-- confirmed still present at this time" - true of the first, false of the second. Storing the two
-- in one table would have put that claim on rows that cannot support it.
--
-- OQ-017 records the part left open: whether the two should be merged for the reader, and how.
-- Keeping them apart in storage does not decide that; it only stops a schema from asserting
-- something a row does not know.
--
-- jobs_read and runs_read rather than workloads_read. Different questions: a Deployment Platform
-- run counts things it saw running, this counts runs it read, and reusing the column name would
-- have made a report say it had seen workloads it never looked for.
--
-- Child rows rather than a JSON column, for the reason V6 gives: ADR-009 rules out a native JSON
-- type, and TEXT would make "what did Tower read and decline to record?" unanswerable without
-- parsing every row in the application. That is the question a team with an untidy CI system will
-- ask most often, so it is the one the schema should answer directly.
--
-- ON DELETE CASCADE on the children is safe for the same reason it is in V6: they are parts of a
-- report rather than facts in their own right. The Observations a report produced are not here and
-- do not cascade - they are facts about what was reported at the time (ADR-002) and outlive any
-- bookkeeping about the run that recorded them.

CREATE TABLE pipeline_sync_report (
    id                    UUID NOT NULL,
    connector_id          VARCHAR(100) NOT NULL,
    started_at            TIMESTAMP NOT NULL,
    finished_at           TIMESTAMP NOT NULL,
    jobs_read             INTEGER NOT NULL,
    runs_read             INTEGER NOT NULL,
    observations_appended INTEGER NOT NULL,
    CONSTRAINT pk_pipeline_sync_report PRIMARY KEY (id)
);

-- What Tower read and deliberately did not record: a run that did not succeed (FR-078), or one
-- whose bound version source held nothing or held something the pattern did not recognise
-- (FR-080). Both are reported rather than dropped, because each points at something the user can
-- act on - a broken deployment, or a binding naming the wrong parameter.
CREATE TABLE pipeline_sync_not_recorded (
    report_id UUID NOT NULL,
    position  INTEGER NOT NULL,
    job       TEXT,
    run_id    TEXT,
    outcome   TEXT,
    reason    TEXT,
    CONSTRAINT pk_pipeline_sync_not_recorded PRIMARY KEY (report_id, position),
    CONSTRAINT fk_pipeline_sync_not_recorded_report FOREIGN KEY (report_id)
        REFERENCES pipeline_sync_report (id) ON DELETE CASCADE
);

CREATE TABLE pipeline_sync_failure (
    report_id UUID NOT NULL,
    position  INTEGER NOT NULL,
    message   TEXT NOT NULL,
    CONSTRAINT pk_pipeline_sync_failure PRIMARY KEY (report_id, position),
    CONSTRAINT fk_pipeline_sync_failure_report FOREIGN KEY (report_id)
        REFERENCES pipeline_sync_report (id) ON DELETE CASCADE
);

-- History is read newest first and nothing else queries it.
CREATE INDEX ix_pipeline_sync_report_started ON pipeline_sync_report (started_at);
