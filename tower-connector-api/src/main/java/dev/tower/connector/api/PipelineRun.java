package dev.tower.connector.api;

import java.time.Instant;
import java.util.Map;

/**
 * One run of a pipeline, as the CI system reports it (ADR-020).
 *
 * <p>The fields are the ones every CI system has, chosen deliberately so that
 * this record survives the system it was first written for. Jenkins calls them
 * build number, result, timestamp and parameters; GitHub Actions calls them run
 * id, conclusion, run_started_at and inputs; GitLab calls them pipeline id,
 * status, created_at and variables. The names differ and the shape does not.
 *
 * <p>{@code outcome} is the system's own word, never normalised — the same rule
 * ADR-018 applies to an issue's status, for the same reason: CI systems disagree
 * about what outcomes exist, and "UNSTABLE" means something to a team that no
 * Tower vocabulary would carry. {@code succeeded} is normalised, because whether
 * a run worked is the one thing every CI system agrees it has an opinion about,
 * and ADR-020 turns on it: only a successful deployment run becomes an
 * Observation.
 *
 * <p>{@code values} are whatever the run carried by name — parameters, inputs,
 * variables. Tower reads them because a job that deploys usually records which
 * version it deployed in one of them, and a binding says which. What it holds is
 * the system's, uninterpreted: no key is special here, and the meaning of one is
 * decided by configuration rather than by this record.
 *
 * @param runId       identifies the run within its job, as the system numbers it —
 *                  a string rather than a number, because not every system uses
 *                  one, and because Tower only ever compares it for equality
 * @param displayName what the run calls itself, where the system lets a run be
 *                    named — Jenkins sets one, GitHub Actions and GitLab both
 *                    have theirs. Empty where it has none. Present because a
 *                    team whose jobs record nothing else sometimes puts the
 *                    version here, and a binding may say to read it
 * @param outcome     the system's own word for how the run ended
 * @param succeeded   whether that word means it worked
 * @param startedAt   when the run began, as the system reported it — never when
 *                    Tower read it, which is the whole point of FR-077
 * @param url         the address a reader can follow to the run itself
 * @param values      the run's named values, uninterpreted
 */
public record PipelineRun(String runId, String displayName, String outcome, boolean succeeded,
                          Instant startedAt, String url, Map<String, String> values) {

    public PipelineRun {
        if (runId == null || runId.isBlank()) {
            throw new ConnectorException("A pipeline run must identify itself within its job.");
        }
        runId = runId.trim();
        displayName = displayName == null ? "" : displayName.trim();
        outcome = outcome == null ? "" : outcome.trim();
        url = url == null ? "" : url.trim();
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    /** The value recorded under this name, or empty when the run carried none. */
    public java.util.Optional<String> value(String name) {
        return java.util.Optional.ofNullable(values.get(name));
    }
}
