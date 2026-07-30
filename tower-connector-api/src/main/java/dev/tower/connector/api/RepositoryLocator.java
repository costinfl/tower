package dev.tower.connector.api;

/**
 * Where a Source Control Connector should look.
 *
 * <p>A single field, unlike {@link DeploymentLocator}. A deployment locator needs
 * a scope because a cluster holds many namespaces; a git remote URL already
 * identifies exactly one repository, so inventing a second field to keep the two
 * locators symmetrical would add a value with nothing to put in it.
 *
 * <p>ADR-014: the URL is a git remote and nothing more. That is what makes the
 * choice between GitHub, GitLab, Bitbucket and a self-hosted server a matter of
 * what a user types here rather than an architectural decision. A bare
 * repository on a file share is equally valid, which is how this Connector is
 * tested without a network.
 *
 * @param url the git remote to read refs from
 */
public record RepositoryLocator(String url) {

    public RepositoryLocator {
        if (url == null || url.isBlank()) {
            throw new ConnectorException("A repository locator must name the git remote to read.");
        }
        url = url.trim();
    }

    /** Rendered into failure messages, so it stays readable. */
    @Override
    public String toString() {
        return url;
    }
}
