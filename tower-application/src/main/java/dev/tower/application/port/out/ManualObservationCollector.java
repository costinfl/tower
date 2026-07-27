package dev.tower.application.port.out;

import dev.tower.domain.observation.ObservationSource;

/**
 * The Collector that produces manually entered Observations (ADR-006).
 *
 * <p>ADR-006 models manual entry as a first-class Collector rather than as an
 * exception to the Observation model, so that one pipeline serves both manual
 * and automated sources and Milestone 2 can add Connectors without reworking
 * anything here.
 *
 * <p>This is a port rather than a direct dependency because the application
 * layer must not reference connector modules. The implementation lives in
 * tower-connector-manual and resolves the actor, which ADR-009 says is the local
 * operating system user until authentication exists.
 */
public interface ManualObservationCollector {

    /** The provenance stamped on Observations this Collector produces. */
    ObservationSource source();
}
