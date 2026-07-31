package dev.tower.connector.api;

/**
 * Where a tracker lives, in that tracker's own terms (ADR-012, ADR-018).
 *
 * <p>Opaque to Tower on purpose. GitHub reads it as {@code owner/repo}, Jira as
 * the site a project belongs to; the Connector interprets it and nothing above
 * the Connector may. That is what keeps {@code ReleasePack} free of a
 * GitHub-shaped or Jira-shaped field (Principles.md: replacing Jira with Azure
 * DevOps must not require changes to the domain model).
 *
 * <p>Bound per Connector rather than per Application, unlike a repository. Work
 * items belong to a release rather than to one Application, so there is one
 * tracker named once. ADR-018 records that difference deliberately.
 */
public record IssueLocator(String value) {

    public IssueLocator {
        if (value == null || value.isBlank()) {
            throw new ConnectorException("An issue locator must name the tracker to read.");
        }
        value = value.trim();
    }

    @Override
    public String toString() {
        return value;
    }
}
