package dev.tower.application.port.out;

import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.application.ApplicationVersionId;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for Application Version storage.
 *
 * <p>BR-01 makes Application Versions immutable, so this port offers creation
 * and removal but no update.
 */
public interface ApplicationVersionRepository {

    ApplicationVersion save(ApplicationVersion version);

    Optional<ApplicationVersion> findById(ApplicationVersionId id);

    List<ApplicationVersion> findAll();

    List<ApplicationVersion> findAllByApplication(ApplicationId applicationId);

    List<ApplicationVersion> findAllById(List<ApplicationVersionId> ids);

    boolean existsByApplicationAndVersion(ApplicationId applicationId, String version);

    /**
     * The existing Version with this identifier, if Tower already knows it.
     *
     * <p>Added for synchronization (issue #49). A Connector reports a version
     * string; observing the same deployment twice must reuse the Application
     * Version already recorded rather than create a second one carrying the same
     * identifier, which would make the two Observations look like different
     * things.
     */
    Optional<ApplicationVersion> findByApplicationAndVersion(ApplicationId applicationId, String version);

    void deleteById(ApplicationVersionId id);
}
