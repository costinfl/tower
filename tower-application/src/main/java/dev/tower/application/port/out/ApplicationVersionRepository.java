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

    void deleteById(ApplicationVersionId id);
}
