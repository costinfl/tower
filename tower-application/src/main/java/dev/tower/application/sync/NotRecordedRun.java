package dev.tower.application.sync;

/**
 * A pipeline run Tower read and deliberately did not record (FR-078, FR-080).
 *
 * <p>Two different things end up here, and both are reported rather than
 * dropped. A run that did not succeed is not evidence that anything reached an
 * Environment (ADR-020), and a run whose bound version source held nothing — or
 * held something the pattern did not recognise — cannot be attributed to an
 * Application Version without guessing, which ADR-012 forbids.
 *
 * <p>Kept apart from {@link UnrecognizedWorkload} rather than folded into it.
 * That type names a scope, a workload and an image reference, because those are
 * what a user needs in order to write a binding for a running thing. A run needs
 * a job, a run identifier and the outcome, and putting a run number in a field
 * called {@code name} beside a parameter value in one called
 * {@code imageReference} would make every screen that renders it say something
 * untrue.
 *
 * @param job     the job the run belongs to, as the CI system names it
 * @param runId   which run, as the CI system numbers it
 * @param outcome the system's own word for how it ended, so a reader can tell a
 *                failed run from an unattributable one at a glance
 * @param reason  why Tower did not record it, in plain words
 */
public record NotRecordedRun(String job, String runId, String outcome, String reason) {

    /** The run did not succeed, so it is not evidence of a deployment (FR-078). */
    public static NotRecordedRun didNotSucceed(String job, String runId, String outcome) {
        return new NotRecordedRun(job, runId, outcome,
                "The run did not succeed, so it is not evidence that anything was deployed.");
    }

    /** The place the binding says the version lives held nothing. */
    public static NotRecordedRun noVersionFound(String job, String runId, String outcome,
                                                String where, String whatItDidCarry) {
        return new NotRecordedRun(job, runId, outcome,
                "This run carried no version in " + where + "."
                        + (whatItDidCarry.isBlank()
                                ? " It carried no named values at all."
                                : " It did carry: " + whatItDidCarry + "."));
    }

    /** Something was there, but the version pattern did not recognise it. */
    public static NotRecordedRun patternDidNotMatch(String job, String runId, String outcome,
                                                    String value, String versionPattern) {
        return new NotRecordedRun(job, runId, outcome,
                "\"" + value + "\" does not match the version pattern " + versionPattern + ".");
    }
}
