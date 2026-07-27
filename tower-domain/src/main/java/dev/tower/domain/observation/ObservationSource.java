package dev.tower.domain.observation;

import dev.tower.domain.shared.DomainException;

/**
 * Where an Observation came from.
 *
 * <p>ADR-002 requires every fact to be traceable to its origin, and FR-013,
 * FR-022 and NFR-012 require Tower to say so. This is that record.
 *
 * <p>ADR-006 widened the definition of an Observation from "collected from an
 * external system" to "collected from an identified source", so that manual
 * entry is a first-class Collector rather than an exception to the model. A
 * manually entered fact is as immutable and as traceable as one a Connector
 * retrieved; it simply names a person instead of a system.
 *
 * @param collector      identifies the Collector, e.g. {@code manual} or {@code kubernetes}
 * @param actor          who entered it, for manual entry; null for automated Collectors
 * @param originInstance the Tower instance that first observed the fact, or null when this one did
 */
public record ObservationSource(String collector, String actor, String originInstance) {

    public static final String MANUAL = "manual";

    public ObservationSource {
        DomainException.require(collector != null && !collector.isBlank(),
                "An Observation must name the Collector it came from.");
        collector = collector.trim();
        actor = blankToNull(actor);
        originInstance = blankToNull(originInstance);
    }

    /**
     * A fact entered by a person (ADR-006).
     *
     * <p>ADR-009 resolves the actor to the local operating system user until
     * authentication exists, which is weaker than an authenticated identity and
     * is recorded as such rather than presented as more than it is.
     */
    public static ObservationSource manual(String actor) {
        return new ObservationSource(MANUAL, actor, null);
    }

    /** A fact retrieved by an automated Collector. */
    public static ObservationSource collector(String collectorId) {
        return new ObservationSource(collectorId, null, null);
    }

    public boolean isManual() {
        return MANUAL.equals(collector);
    }

    /**
     * True when this fact was first observed by another Tower instance and
     * reached this one through import.
     *
     * <p>ADR-010: import transfers existing immutable facts rather than minting
     * new ones, so an imported Observation keeps naming the instance that
     * actually saw it. Tower never claims to have observed something it did not.
     */
    public boolean isImported() {
        return originInstance != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
