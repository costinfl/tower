package dev.tower.connector.api;

import java.util.List;

/**
 * Reads work items from an issue tracker (Connector-Model.md, ADR-018).
 *
 * <p>The one Connector category that produces no Observations. Its subject is a
 * record in somebody else's database rather than a running system, so there is
 * nothing here for Tower to have seen. ADR-018 records why, and corrects
 * Connector-Model.md § Observation Mapping, which had claimed otherwise.
 *
 * <p>What it does instead is resolve references a developer already stated:
 * given identifiers, report what the tracker currently says about them. Tower
 * shows that beside the title it holds and stores none of it.
 *
 * <p>Read-only, like every Connector (ADR-001). There is no operation here that
 * creates, transitions, comments on or assigns a work item, and there never will
 * be — Guardrails.md lists "Creates or updates Jira issues automatically" among
 * the things Tower must never do.
 */
public interface IssueTrackerConnector {

    /** Stable identifier for this Connector, e.g. "github-issues" or "jira". */
    String connectorId();

    /**
     * Reads the work items named by the given identifiers.
     *
     * <p>An identifier the tracker does not know is <em>omitted</em> from the
     * result rather than reported as an error or invented as an empty issue. A
     * reference Tower cannot resolve is an ordinary situation — a typo, a ticket
     * moved to another project, a tracker the credential cannot see — and the
     * caller distinguishes "the tracker does not have this" from "the tracker
     * could not be reached" by whether this method returned or threw.
     *
     * <p>Order is not guaranteed and must not be relied upon; callers match on
     * {@link TrackedIssue#identifier()}.
     *
     * @throws ConnectorException when the tracker could not be read at all
     */
    List<TrackedIssue> readIssues(IssueLocator locator, List<String> identifiers,
                                  ConnectorCredential credential);

    /**
     * Confirms the tracker answers and the credential is accepted, without
     * modifying anything (FR-061).
     *
     * @throws ConnectorException when it does not
     */
    void checkConnection(IssueLocator locator, ConnectorCredential credential);
}
