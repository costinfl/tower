package dev.tower.application.port.out;

import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.observation.Observation;
import dev.tower.domain.observation.ObservationId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port for Observation storage.
 *
 * <p>Observations are immutable and append-only (BR-02, FR-023), so this port
 * offers no update. It offers no delete either: removing a fact would rewrite
 * history, and superseding it with a later Observation is the documented way to
 * correct a mistake.
 */
public interface ObservationRepository {

    Observation append(Observation observation);

    Optional<Observation> findById(ObservationId id);

    List<Observation> findAllInEnvironment(EnvironmentId environmentId);

    /** Every Observation of the given versions, across all Environments. */
    List<Observation> findAllOfVersions(Collection<ApplicationVersionId> versionIds);

    List<Observation> findAll();
}
