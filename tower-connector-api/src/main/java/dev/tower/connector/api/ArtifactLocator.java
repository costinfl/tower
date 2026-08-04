package dev.tower.connector.api;

/**
 * Where an Artifact Repository Connector should look (ADR-021).
 *
 * <p>One field, like {@link RepositoryLocator} and unlike {@link PipelineLocator}.
 * A pipeline locator needs a job as well as a server because a run belongs to
 * one; an artifact is addressed by a coordinate that travels with the request
 * rather than with the binding, because a single read asks about several.
 *
 * @param system where the repository lives, as its own address
 */
public record ArtifactLocator(String system) {

    public ArtifactLocator {
        if (system == null || system.isBlank()) {
            throw new ConnectorException(
                    "An artifact locator must name the repository system to read.");
        }
        system = system.trim();
    }

    /** Rendered into failure messages, so it stays readable. */
    @Override
    public String toString() {
        return system;
    }
}
