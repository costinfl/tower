package dev.tower.application.port.out;

import java.util.List;

import dev.tower.application.sync.ConnectionTest;

/**
 * Reads work items from a tracker, on the application layer's terms (ADR-018).
 *
 * <p>Names no Connector type. ArchUnit forbids tower-application from
 * referencing {@code dev.tower.connector..}, and that rule is doing real work
 * here: an interface carrying {@code TrackedIssue} would put a vendor-facing
 * shape into the layer that must stay vendor-neutral. The Collector implements
 * this and speaks to the Connector on the other side.
 *
 * <p>Produces no Observations, unlike every other Collector in Tower. What it
 * returns is shown beside what Tower holds and stored nowhere — ADR-018 records
 * why an issue is not something Tower observed.
 */
public interface WorkItemCollector {

    /** Which tracker this Collector reads, e.g. "github-issues" or "jira". */
    String connectorId();

    /**
     * Reads the named work items.
     *
     * <p>An identifier the tracker does not know is omitted rather than reported
     * as an error: a reference Tower cannot resolve is an ordinary situation, and
     * the caller tells that apart from an unreachable tracker by whether this
     * returned or threw.
     *
     * @throws RuntimeException when the tracker could not be read at all
     */
    List<CollectedWorkItem> read(List<String> identifiers);

    /** Confirms the tracker answers, without modifying it (FR-061). */
    ConnectionTest checkConnection();

    /**
     * @param status the tracker's own word for where the item stands, not
     *               normalised: trackers disagree about what states exist, and
     *               choosing which of a team's states counts as finished is a
     *               judgement Tower has no standing to make
     */
    record CollectedWorkItem(String identifier, String title, String status,
                             boolean closed, String url) {
    }
}
