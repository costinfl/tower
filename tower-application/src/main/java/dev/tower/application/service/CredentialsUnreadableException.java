package dev.tower.application.service;

/**
 * Signals that stored credentials cannot be read with the key now in force.
 *
 * <p>Its own type, rather than an {@link IllegalStateException}, for one reason:
 * the message has to reach the person. It names what happened and the two ways
 * out — restore the previous key, or clear the store and save the credentials
 * again — and neither is something they could work out from a generic failure.
 * The API's catch-all deliberately hides messages, so a message worth showing
 * needs a type worth handling.
 *
 * <p>Carries no secret and must never be given one. NFR-028 governs this message
 * as it governs every other: it names an environment variable and a file, and
 * nothing that was encrypted.
 *
 * <p>Declared in the application layer because that is where the credentials port
 * lives, though it is raised by the implementation in tower-config. The layer
 * that owns the port owns the vocabulary of its failures.
 */
public class CredentialsUnreadableException extends RuntimeException {

    public CredentialsUnreadableException(String message, Throwable cause) {
        super(message, cause);
    }
}
