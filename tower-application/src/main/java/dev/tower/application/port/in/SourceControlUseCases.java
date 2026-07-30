package dev.tower.application.port.in;

import java.util.List;

import dev.tower.application.discovery.VersionDiscovery;
import dev.tower.application.sync.ConnectionTest;
import dev.tower.domain.application.ApplicationId;

/**
 * Inbound port for discovering Application Versions from source control
 * (issue #3, ADR-014).
 *
 * <p>Every operation here is a read, and that is the whole shape of this port.
 * There is no "import these versions" use case, deliberately: registering an
 * Application Version already has a use case, and giving discovery its own
 * creation path would put BR-01's immutability rule in two places. A user accepts
 * a candidate by creating a version from it, through the same door as always.
 */
public interface SourceControlUseCases {

    /**
     * What the repository bound to this Application currently holds.
     *
     * <p>Never throws for an unreachable repository or an unbound Application.
     * Both are answers about configuration or the world, carried in the result so
     * a screen can show them, and both are ordinary enough that an exception would
     * be the wrong shape.
     */
    VersionDiscovery discover(ApplicationId applicationId);

    /** Discovery across every Application with a repository bound. */
    List<VersionDiscovery> discoverAll();

    /**
     * Confirms a repository is reachable and any credential accepted, without
     * modifying it (FR-061, FR-036).
     */
    ConnectionTest checkConnection(String repositoryUrl);
}
