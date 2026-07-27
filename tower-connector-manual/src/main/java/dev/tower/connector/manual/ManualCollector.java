package dev.tower.connector.manual;

import dev.tower.application.port.out.ManualObservationCollector;
import dev.tower.domain.observation.ObservationSource;
import org.springframework.stereotype.Component;

/**
 * The Collector for facts a person enters by hand (ADR-006).
 *
 * <p>Milestone 1 ships this Collector alone. Modelling manual entry as a
 * Collector rather than as a special case is what lets Milestone 2 add
 * Connectors for Source Control and Kubernetes without reworking the
 * Observation model: they differ only in what they name as the source.
 *
 * <p>Like every Collector it is strictly read-only with respect to the outside
 * world (ADR-001, CM-01). It retrieves nothing and changes nothing; it only
 * says who is speaking.
 *
 * <p>ADR-009 resolves the actor to the local operating system user, because a
 * loopback-bound single-user instance has nobody to authenticate. That is
 * weaker than an authenticated identity, and it is recorded as what it is
 * rather than dressed up as more.
 */
@Component
public class ManualCollector implements ManualObservationCollector {

    static final String UNKNOWN_ACTOR = "unknown";

    private final String actor;

    public ManualCollector() {
        this(System.getProperty("user.name"));
    }

    ManualCollector(String actor) {
        this.actor = actor == null || actor.isBlank() ? UNKNOWN_ACTOR : actor.trim();
    }

    @Override
    public ObservationSource source() {
        return ObservationSource.manual(actor);
    }
}
