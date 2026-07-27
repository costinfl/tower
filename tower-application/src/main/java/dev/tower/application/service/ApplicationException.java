package dev.tower.application.service;

/**
 * Signals that a use case cannot proceed for a reason spanning aggregates,
 * such as a name collision or a reference that must not be broken.
 *
 * <p>Rules internal to a single aggregate are enforced in the domain and raise
 * DomainException instead.
 */
public class ApplicationException extends RuntimeException {

    public ApplicationException(String message) {
        super(message);
    }
}
