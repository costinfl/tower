package dev.tower.application.documentation;

import java.util.UUID;

import dev.tower.application.service.InvalidRequestException;

/**
 * Stable identity of a {@link DocumentTemplate}.
 *
 * <p>In the application layer for the same reason {@code SyncRunId} is: a
 * template is user-owned configuration about generated documentation, not a
 * business fact, and the Domain Model may depend only on the Java standard
 * library.
 *
 * <p>Identity rather than the name, so renaming a template does not orphan
 * whatever refers to it.
 */
public record DocumentTemplateId(UUID value) {

    public DocumentTemplateId {
        if (value == null) {
            throw new InvalidRequestException("Document Template id is required.");
        }
    }

    public static DocumentTemplateId newId() {
        return new DocumentTemplateId(UUID.randomUUID());
    }

    public static DocumentTemplateId of(String value) {
        try {
            return new DocumentTemplateId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidRequestException("Document Template id is not a valid identifier: " + value);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
