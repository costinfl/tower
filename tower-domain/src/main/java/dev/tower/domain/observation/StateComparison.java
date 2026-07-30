package dev.tower.domain.observation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.shared.DomainException;

/**
 * The difference between two observed states (Milestone 4, ADR-017).
 *
 * <p>Derived, and stored nowhere. Both sides are themselves derived from the
 * Observation stream, so a comparison is a fold of a fold — which is why it can
 * be asked for any pair of instants, or between any two Environments, without
 * anything having been captured in advance.
 *
 * <p>The two sides are deliberately not named "before" and "after". The same
 * type answers "how did UAT change between Monday and Friday" and "how does UAT
 * differ from Production right now", and only the caller knows which question
 * was asked. Calling them left and right keeps this from implying a direction
 * that may not exist.
 *
 * <p>Every entry carries the Observation behind each side, so a difference
 * remains traceable to the facts that produced it (FR-031, FR-032, NFR-011). A
 * comparison that said "this changed" without saying what told Tower so would be
 * worse than none, because it would invite a decision no one could check.
 */
public record StateComparison(List<Difference> differences, List<Unchanged> unchanged) {

    public StateComparison {
        differences = List.copyOf(differences);
        unchanged = List.copyOf(unchanged);
    }

    /**
     * Compares two derived states.
     *
     * <p>Applications observed on only one side are reported as such rather than
     * treated as absent. "Not deployed here" and "never observed here" look
     * identical in a comparison that only listed versions, and they are
     * different facts — Tower reports what it was told and never fills a gap
     * with a guess (Scenario 4).
     */
    public static StateComparison between(EnvironmentState left, EnvironmentState right) {
        DomainException.require(left != null && right != null,
                "Both sides of a comparison are required.");

        // Insertion-ordered so the result is stable: the same two states always
        // compare to the same list in the same order.
        Set<ApplicationId> applications = new LinkedHashSet<>();
        left.deployed().forEach(d -> applications.add(d.applicationId()));
        right.deployed().forEach(d -> applications.add(d.applicationId()));

        List<Difference> differences = new ArrayList<>();
        List<Unchanged> unchanged = new ArrayList<>();

        for (ApplicationId application : applications) {
            Optional<EnvironmentState.DeployedApplication> onLeft = left.deploymentOf(application);
            Optional<EnvironmentState.DeployedApplication> onRight = right.deploymentOf(application);

            ApplicationVersionId leftVersion = onLeft.map(
                    EnvironmentState.DeployedApplication::applicationVersionId).orElse(null);
            ApplicationVersionId rightVersion = onRight.map(
                    EnvironmentState.DeployedApplication::applicationVersionId).orElse(null);

            if (leftVersion != null && leftVersion.equals(rightVersion)) {
                unchanged.add(new Unchanged(application, leftVersion,
                        onLeft.get().observationId(), onRight.get().observationId()));
            } else {
                differences.add(new Difference(application, leftVersion, rightVersion,
                        onLeft.map(EnvironmentState.DeployedApplication::observationId).orElse(null),
                        onRight.map(EnvironmentState.DeployedApplication::observationId).orElse(null)));
            }
        }

        return new StateComparison(differences, unchanged);
    }

    public boolean isIdentical() {
        return differences.isEmpty();
    }

    /**
     * One Application whose observed version differs between the two sides.
     *
     * @param leftVersion  the version on the left, or null when never observed there
     * @param rightVersion the version on the right, or null when never observed there
     */
    public record Difference(
            ApplicationId applicationId,
            ApplicationVersionId leftVersion,
            ApplicationVersionId rightVersion,
            ObservationId leftObservationId,
            ObservationId rightObservationId) {

        /** Observed on the right and not on the left — new, or newly arrived. */
        public boolean onlyOnRight() {
            return leftVersion == null;
        }

        /** Observed on the left and not on the right — gone, or not yet there. */
        public boolean onlyOnLeft() {
            return rightVersion == null;
        }

        /** Observed on both sides, at different versions. */
        public boolean changed() {
            return leftVersion != null && rightVersion != null;
        }
    }

    /**
     * One Application at the same version on both sides.
     *
     * <p>Reported rather than omitted. A comparison that listed only differences
     * would leave a reader unable to tell "the same" from "not covered", and the
     * question behind a comparison is usually whether two things match — which
     * needs the matches to be visible.
     */
    public record Unchanged(
            ApplicationId applicationId,
            ApplicationVersionId applicationVersionId,
            ObservationId leftObservationId,
            ObservationId rightObservationId) {
    }
}
