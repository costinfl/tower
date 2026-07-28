package dev.tower.connector.api;

/**
 * Where a Deployment Platform Connector should look.
 *
 * <p>ADR-012: an Environment is a business concept, not a namespace. The
 * correspondence between the two is user-owned configuration held outside the
 * Domain Model, and this record is the vendor-neutral half of it — the part the
 * Connector understands.
 *
 * <p>The fields are deliberately generic. A Kubernetes Connector reads
 * {@code scope} as a namespace; another Deployment Platform may read it as a
 * project, an account or a service group. CM-03 requires vendor concepts to
 * terminate at the Connector boundary, so no Kubernetes vocabulary appears here.
 *
 * @param target the platform endpoint to read, such as a cluster
 * @param scope  the partition within that endpoint, such as a namespace
 */
public record DeploymentLocator(String target, String scope) {

    public DeploymentLocator {
        target = requireText(target, "A deployment locator must name the platform endpoint to read.");
        scope = requireText(scope, "A deployment locator must name the scope to read within that endpoint.");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ConnectorException(message);
        }
        return value.trim();
    }

    /** Rendered into failure messages and Sync Run records, so it stays readable. */
    @Override
    public String toString() {
        return target + "/" + scope;
    }
}
