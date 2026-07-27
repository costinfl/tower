package dev.tower.domain.shared;

/**
 * Signals that a request is well-formed but conflicts with the current state of
 * an aggregate.
 *
 * <p>This is a genuine domain distinction rather than an HTTP concern that has
 * leaked inward. "This name is blank" and "this pack already contains a
 * different version of that Application" are different kinds of wrong: the
 * first says the caller sent nonsense, the second says the caller sent
 * something reasonable that the current state does not permit. Only the second
 * can be resolved by changing the aggregate and retrying.
 *
 * <p>The distinction happens to map cleanly onto 400 versus 409 at the web
 * edge, which is where that translation belongs.
 */
public class DomainConflictException extends DomainException {

    public DomainConflictException(String message) {
        super(message);
    }

    public static void requireNoConflict(boolean condition, String message) {
        if (!condition) {
            throw new DomainConflictException(message);
        }
    }
}
