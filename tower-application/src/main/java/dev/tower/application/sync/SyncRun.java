package dev.tower.application.sync;

import java.time.Instant;
import java.util.List;

/**
 * What one synchronization run did (ADR-011, FR-059, FR-060).
 *
 * <p>ADR-011 records an Observation only when observed state has changed, which
 * leaves Tower unable to distinguish "still deployed" from "we stopped looking"
 * — unless something records that a run happened and found nothing new. This is
 * that record, and it is why change-only Observations do not lose liveness.
 *
 * <p>The Observation says when the deployed version last changed. A successful
 * Sync Run says when Tower last looked and found it unchanged. Neither claim is
 * invented, and neither required an Observation that recorded nothing.
 *
 * <p>Operational telemetry, not a business fact. ADR-011 keeps it out of the
 * Domain Model deliberately: it records what Tower did, not what Tower observed
 * about the world. Being in the application layer, it also cannot reach a domain
 * type by accident — {@code domain_depends_only_on_the_jdk} makes that
 * structural.
 *
 * @param connectorId            which Connector ran
 * @param startedAt              when the run began
 * @param finishedAt             when it ended
 * @param outcome                whether it wholly succeeded, partly succeeded or failed
 * @param workloadsRead          how many running things the platform reported
 * @param observationsAppended   how many were new facts (ADR-011); zero is the normal steady state
 * @param unrecognized           what Tower saw but could not attribute (FR-060)
 * @param failures               what went wrong, per scope, in words fit to show a user
 */
public record SyncRun(
        String connectorId,
        Instant startedAt,
        Instant finishedAt,
        Outcome outcome,
        int workloadsRead,
        int observationsAppended,
        List<UnrecognizedWorkload> unrecognized,
        List<String> failures) {

    public enum Outcome {
        /** Every bound scope was read. */
        SUCCEEDED,
        /**
         * Some scopes were read and others failed.
         *
         * <p>Recorded distinctly because Connector-Model.md requires that
         * incomplete synchronization never corrupt what Tower already knows: the
         * Observations from the scopes that succeeded are valid, and the ones
         * that failed simply have no new facts.
         */
        PARTIALLY_SUCCEEDED,
        /** Nothing could be read. No Observation was appended. */
        FAILED
    }

    public SyncRun {
        unrecognized = unrecognized == null ? List.of() : List.copyOf(unrecognized);
        failures = failures == null ? List.of() : List.copyOf(failures);
    }

    /** True when nothing changed — the ordinary result once a system is stable. */
    public boolean foundNoChange() {
        return outcome != Outcome.FAILED && observationsAppended == 0;
    }
}
