package dev.tower.domain.iteration;

import dev.tower.domain.shared.DomainException;

import java.time.Instant;

/**
 * A validation cycle executed against a Release Pack, such as "SIT Iteration 1"
 * or "UAT Iteration" (Glossary.md).
 *
 * <p>Iterations provide traceability between a Release Pack and the validation
 * activities performed on it. Tower records that an Iteration happened; it does
 * not run tests, score them or decide whether one passed.
 *
 * <p>There is deliberately no status vocabulary. An Iteration that has a
 * completion timestamp is finished and one that does not is still open, which is
 * all the documentation asks for. Inventing PASSED and FAILED here would put
 * Tower in the business of judging QA outcomes.
 */
public record Iteration(IterationId id, String name, Instant startedAt, Instant completedAt, String notes) {

    public static final int NAME_MAX_LENGTH = 150;

    public Iteration {
        DomainException.require(id != null, "Iteration id is required.");
        DomainException.require(name != null && !name.isBlank(), "Iteration name is required.");
        DomainException.require(name.length() <= NAME_MAX_LENGTH,
                "Iteration name must be at most " + NAME_MAX_LENGTH + " characters.");
        DomainException.require(startedAt != null, "Iteration requires a start timestamp.");
        DomainException.require(completedAt == null || !completedAt.isBefore(startedAt),
                "An Iteration cannot complete before it started.");
        name = name.trim();
        notes = notes == null ? "" : notes.strip();
    }

    public static Iteration start(String name, Instant startedAt, String notes) {
        return new Iteration(IterationId.newId(), name, startedAt, null, notes);
    }

    public Iteration complete(Instant completedAt) {
        return new Iteration(id, name, startedAt, completedAt, notes);
    }

    public Iteration reopen() {
        return new Iteration(id, name, startedAt, null, notes);
    }

    public Iteration withNotes(String newNotes) {
        return new Iteration(id, name, startedAt, completedAt, newNotes);
    }

    public boolean isComplete() {
        return completedAt != null;
    }
}
