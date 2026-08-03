package dev.tower.connector.api;

import java.time.Instant;
import java.util.List;

/**
 * Reads runs of a pipeline (Connector-Model.md, ADR-020).
 *
 * <p>Read-only, like every Connector (ADR-001, CM-01, FR-076), and this is the
 * category where that guarantee is least automatic. A CI system's whole purpose
 * is to be told to do things, and a token that can read builds can very often
 * start them. Guardrails.md lists triggering pipelines among the things Tower
 * must never do, and there is no operation here that starts, stops, retries or
 * configures anything — nor will there be.
 *
 * <p>What this Connector produces depends on what a run <em>is</em>, which
 * ADR-020 settles: a successful run of a deployment job becomes an Observation,
 * on the footing ADR-006 established for manual entry; a run of a build job
 * becomes a candidate Application Version, on the footing ADR-014 established
 * for a git ref. That distinction is made by configuration above this interface,
 * not here. A Connector reads runs and reports what it read.
 *
 * <p>Deliberately named for what CI systems generally offer rather than for the
 * first one implemented. The target when this was written was Jenkins, with a
 * fair chance of being decommissioned inside a year; the interface is the part
 * meant to outlive it, and every term in it — job, run, outcome, instant, named
 * values — is one that Jenkins, GitHub Actions, GitLab CI and Tekton all have
 * under their own names.
 */
public interface CiCdConnector {

    /** Stable identifier for this Connector, e.g. "jenkins". */
    String connectorId();

    /**
     * Runs of the named job, most recent first.
     *
     * <p>Bounded twice, and both bounds are needed. {@code since} keeps a routine
     * synchronization from re-reading history it has already seen; {@code limit}
     * keeps the first synchronization of a job with ten thousand builds from
     * trying to read all of them. A Connector may apply either bound on the
     * server or after reading, depending on what its system supports — what it
     * must not do is return more than {@code limit} runs or runs older than
     * {@code since}.
     *
     * <p>Returns an empty list for a job that exists and has never run. A job that
     * cannot be read throws instead, because an empty list would be
     * indistinguishable from a job that has never run and would make Tower report
     * that nothing had been deployed.
     *
     * <p>Runs are returned whatever their outcome. Deciding that a failed run
     * records nothing is the Collector's business (FR-078): a Connector that
     * filtered them would leave the Collector unable to report that a deployment
     * had been attempted and failed.
     *
     * @param since ignore runs that started at or before this instant; null reads
     *              as far back as {@code limit} allows
     * @param limit the most runs to return, which must be positive
     * @throws ConnectorException when the job or the system cannot be read
     */
    List<PipelineRun> readRuns(PipelineLocator locator, Instant since, int limit,
                               ConnectorCredential credential);

    /**
     * Confirms the CI system answers and the credential is accepted, without
     * modifying anything (FR-061, FR-036).
     *
     * @throws ConnectorException when it does not
     */
    void checkConnection(PipelineLocator locator, ConnectorCredential credential);
}
