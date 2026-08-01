package dev.tower.application.binding;

import dev.tower.application.service.InvalidRequestException;

/**
 * Where a Connector should look for work items (ADR-018).
 *
 * <p>Bound per Connector, not per Tower concept, and that makes it the odd one
 * out among bindings. ADR-012 maps a Tower concept on the left to a vendor
 * locator on the right — an Environment to a namespace, an Application to an
 * image or a repository — because each of those Tower concepts has its own place
 * in the external system.
 *
 * <p>Work items do not work that way. A work item belongs to a release rather
 * than to one Application, and a team has one tracker. Binding per Application
 * would ask the same question once per Application and invite three answers
 * where there is one; binding per Release Pack would ask it again every release.
 * So the tracker is named once, and ADR-018 records the difference deliberately
 * rather than leaving it to look like an oversight.
 *
 * <p>The locator is opaque here and interpreted only inside the Connector:
 * {@code owner/repo} for GitHub, a site for Jira. Nothing above the Connector
 * may parse it, which is what keeps this record from becoming GitHub-shaped.
 */
public record IssueTrackerBinding(String connectorId, String locator) {

    public static final int LOCATOR_MAX_LENGTH = 500;

    public IssueTrackerBinding {
        InvalidRequestException.require(connectorId != null && !connectorId.isBlank(),
                "A binding must name the Connector it configures.");
        connectorId = connectorId.trim();

        InvalidRequestException.require(locator != null && !locator.isBlank(),
                "A binding must name where the tracker lives,"
                        + " in the form that Connector expects.");
        locator = locator.trim();
        InvalidRequestException.require(locator.length() <= LOCATOR_MAX_LENGTH,
                "A tracker locator may be at most " + LOCATOR_MAX_LENGTH + " characters.");
    }
}
