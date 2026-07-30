package dev.tower.domain.observation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.releasepack.ReleasePackId;
import dev.tower.domain.shared.DomainException;

/**
 * Where a Release Pack has got to, and when it got there (Milestone 4, ADR-017).
 *
 * <p>Derived from the Observation stream like everything else in this package,
 * and stored nowhere. The question it answers — when did this release reach UAT —
 * is one the Observations already contain; nothing had to be recorded at the time
 * for it to be answerable now.
 *
 * <p>A release arrives in an Environment piecemeal, and that is the fact this
 * type exists to represent honestly. Reporting a single "arrived at" instant
 * would have to choose between the moment the first version appeared and the
 * moment the last one did, and those can be days apart. Both are reported, and a
 * release with versions still missing is described as partly arrived rather than
 * as arrived or absent.
 *
 * <p>ADR-008 already derives a Release Pack's <em>state</em> as the highest Stage
 * it has been observed at. This is the finer-grained companion: not how far it
 * got, but when, and what is still outstanding.
 */
public record ReleasePackProgression(
        ReleasePackId releasePackId, List<EnvironmentArrival> arrivals) {

    public ReleasePackProgression {
        DomainException.require(releasePackId != null, "Release Pack id is required.");
        arrivals = List.copyOf(arrivals);
    }

    /**
     * Derives progression from the Observations of a Release Pack's contents.
     *
     * <p>Observations of versions the pack does not contain are ignored rather
     * than rejected, so a caller may pass a broad result set without filtering
     * it — the same courtesy {@link EnvironmentState#from} extends.
     *
     * <p>Environments the release has never been seen in do not appear. Tower
     * reports what it was told, and an Environment with no Observation of this
     * release is not evidence that the release is absent from it, only that
     * nobody has looked or nobody has said (Scenario 4).
     *
     * @param contents the Application Versions the pack contains
     */
    public static ReleasePackProgression from(
            ReleasePackId releasePackId,
            Collection<ApplicationVersionId> contents,
            Collection<Observation> observations) {

        DomainException.require(releasePackId != null, "Release Pack id is required.");
        Set<ApplicationVersionId> packed = new LinkedHashSet<>(contents);

        // Per Environment: when each of the pack's versions was first seen there.
        Map<EnvironmentId, Map<ApplicationVersionId, Instant>> firstSeen = new LinkedHashMap<>();
        for (Observation observation : observations) {
            if (observation == null || !packed.contains(observation.applicationVersionId())) {
                continue;
            }
            firstSeen
                    .computeIfAbsent(observation.environmentId(), key -> new LinkedHashMap<>())
                    .merge(observation.applicationVersionId(), observation.observedAt(),
                            (incumbent, candidate) -> candidate.isBefore(incumbent) ? candidate : incumbent);
        }

        List<EnvironmentArrival> arrivals = new ArrayList<>();
        firstSeen.forEach((environmentId, seen) -> {
            List<ApplicationVersionId> missing = packed.stream()
                    .filter(version -> !seen.containsKey(version))
                    .toList();

            Instant first = seen.values().stream().min(Comparator.naturalOrder()).orElseThrow();
            // Complete only when every version has been seen. The instant is when
            // the last of them arrived, which is when the release as a whole was
            // there — not when the first piece of it showed up.
            Instant complete = missing.isEmpty()
                    ? seen.values().stream().max(Comparator.naturalOrder()).orElseThrow()
                    : null;

            arrivals.add(new EnvironmentArrival(
                    environmentId, first, complete, seen.size(), packed.size(), missing));
        });

        // Oldest first: the order the release actually travelled in.
        arrivals.sort(Comparator.comparing(EnvironmentArrival::firstObservedAt));
        return new ReleasePackProgression(releasePackId, arrivals);
    }

    public Optional<EnvironmentArrival> in(EnvironmentId environmentId) {
        return arrivals.stream().filter(a -> a.environmentId().equals(environmentId)).findFirst();
    }

    /** True when the release has been seen somewhere, whole or in part. */
    public boolean hasBeenObserved() {
        return !arrivals.isEmpty();
    }

    /**
     * When a Release Pack reached one Environment, and whether all of it did.
     *
     * @param firstObservedAt when the first of the pack's versions was seen here
     * @param completeAt      when the last one was, or null while any is missing
     * @param observedCount   how many of the pack's versions have been seen here
     * @param packedCount     how many the pack contains
     * @param missing         the versions not yet seen here, so a partial arrival
     *                        names what it is waiting for rather than only counting
     */
    public record EnvironmentArrival(
            EnvironmentId environmentId,
            Instant firstObservedAt,
            Instant completeAt,
            int observedCount,
            int packedCount,
            List<ApplicationVersionId> missing) {

        public EnvironmentArrival {
            missing = List.copyOf(missing);
        }

        public boolean isComplete() {
            return completeAt != null;
        }

        /** Some of the release is here and some is not — the state worth naming. */
        public boolean isPartial() {
            return completeAt == null && observedCount > 0;
        }
    }
}
