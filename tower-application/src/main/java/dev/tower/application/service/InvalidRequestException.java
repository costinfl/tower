package dev.tower.application.service;

/**
 * Signals that a request carries a value a use case cannot accept.
 *
 * <p>Distinct from {@link ApplicationException}, which reports a conflict with
 * state that already exists and answers 409. This one reports input that is
 * wrong on its own terms — a regular expression that does not compile, say — and
 * answers 400. Resubmitting it unchanged will fail the same way, which is
 * precisely what the two status codes distinguish.
 *
 * <p>Distinct from {@code DomainException} because the values it guards belong
 * to the application layer rather than the Domain Model. External Bindings are
 * the first such values: ADR-012 places them outside the domain deliberately.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidRequestException(message);
        }
    }
}
