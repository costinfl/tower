package dev.tower.domain.shared;

/**
 * Signals that an operation would leave the domain in a state the business
 * rules forbid.
 *
 * <p>The domain carries no framework dependency (NFR-004, NFR-006), so it
 * reports rule violations with a plain unchecked exception rather than any
 * validation library.
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new DomainException(message);
        }
    }
}
