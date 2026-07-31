package dev.tower.domain.convergence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.observation.ReleasePackProgression;
import dev.tower.domain.releasepack.ReleasePackId;
import dev.tower.domain.shared.DomainException;

/**
 * Which Release Packs are converging on one Environment (Milestone 5, issue #7).
 *
 * <p>This is the question the operational dashboard exists to answer: not "where
 * is this release" — a Release Pack page already says that — but "which releases
 * are competing for UAT right now", asked across every release at once, so a
 * business team can decide which one proceeds.
 *
 * <p>Tower shows the situation. It does not rank the packs, recommend one, or
 * imply an order (ADR-001; Guardrails.md lists "Visualizes" and does not list
 * "Decides"). That constraint is enforced here rather than left to the screen:
 * packs are ordered <strong>by name</strong>, because every other order a
 * dashboard might reach for — nearest to arriving, oldest, furthest along — is a
 * ranking wearing a sort's clothing, and a reader would take the top row as the
 * recommendation.
 *
 * <p>A pack converges on an Environment because the pack's own pinned Promotion
 * Path names that Environment (ADR-007). That expectation comes from the team's
 * own topology, never from Tower inferring where a release ought to go.
 */
public record EnvironmentConvergence(EnvironmentId environmentId, List<ConvergingPack> packs) {

    public EnvironmentConvergence {
        DomainException.require(environmentId != null, "Environment id is required.");
        packs = List.copyOf(packs);
    }

    /**
     * Derives convergence from the progression of each pack whose pinned
     * Promotion Path names this Environment.
     *
     * @param converging one entry per pack naming this Environment; a pack whose
     *                   path does not name it is not converging on it and must
     *                   not be passed in
     */
    public static EnvironmentConvergence from(
            EnvironmentId environmentId, Collection<PackProgression> converging) {

        DomainException.require(environmentId != null, "Environment id is required.");

        List<ConvergingPack> packs = new ArrayList<>();
        for (PackProgression entry : converging) {
            if (entry == null) {
                continue;
            }
            packs.add(entry.progression().in(environmentId)
                    .map(arrival -> new ConvergingPack(
                            entry.releasePackId(), entry.name(),
                            arrival.isComplete() ? Standing.FULLY_OBSERVED : Standing.PARTLY_OBSERVED,
                            arrival.firstObservedAt(), arrival.completeAt(),
                            arrival.observedCount(), arrival.packedCount()))
                    // No arrival means no Observation of this pack here. That is
                    // not evidence the release is absent (Scenario 4), which is
                    // why the standing is named for what Tower was told rather
                    // than for what is running.
                    .orElseGet(() -> new ConvergingPack(
                            entry.releasePackId(), entry.name(), Standing.NOT_OBSERVED_HERE,
                            null, null, 0, entry.packedCount())));
        }

        // Alphabetical, deliberately. See the class comment: any order derived
        // from progress would read as a recommendation.
        packs.sort(Comparator.comparing(ConvergingPack::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(pack -> pack.releasePackId().toString()));
        return new EnvironmentConvergence(environmentId, packs);
    }

    /**
     * More than one active release is heading for this Environment.
     *
     * <p>The fact the dashboard exists to surface. Stated rather than left to be
     * counted off a list, because it is the thing a reader is scanning for.
     */
    public boolean isContested() {
        return packs.size() > 1;
    }

    /** Packs whose Promotion Path names this Environment but which have not been seen here. */
    public List<ConvergingPack> notObservedHere() {
        return packs.stream().filter(pack -> pack.standing() == Standing.NOT_OBSERVED_HERE).toList();
    }

    /**
     * One Release Pack heading for an Environment, and how far it has got there.
     *
     * @param progression the pack's progression across all Environments; this
     *                    type picks out the one arrival that concerns it
     * @param packedCount how many versions the pack contains, needed even when
     *                    there is no arrival to read it from
     */
    public record PackProgression(
            ReleasePackId releasePackId, String name,
            int packedCount, ReleasePackProgression progression) {

        public PackProgression {
            DomainException.require(releasePackId != null, "Release Pack id is required.");
            DomainException.require(progression != null, "Progression is required.");
        }
    }

    /**
     * @param firstObservedAt null when nothing of this pack has been seen here
     * @param completeAt      null until all of it has
     */
    public record ConvergingPack(
            ReleasePackId releasePackId, String name, Standing standing,
            Instant firstObservedAt, Instant completeAt,
            int observedCount, int packedCount) {
    }

    /**
     * How much of a Release Pack has been observed in one Environment.
     *
     * <p>Named for what Tower was told, not for what is deployed.
     * {@code NOT_OBSERVED_HERE} says nobody has reported this release in this
     * Environment; it does not say the release is missing from it.
     */
    public enum Standing {
        NOT_OBSERVED_HERE,
        PARTLY_OBSERVED,
        FULLY_OBSERVED
    }
}
