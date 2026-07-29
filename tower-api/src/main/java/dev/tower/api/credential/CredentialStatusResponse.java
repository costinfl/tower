package dev.tower.api.credential;

import java.time.Instant;

import dev.tower.application.port.out.CredentialStatus;

/**
 * Everything the API will say about a stored credential.
 *
 * <p>Three fields, none of which is the secret or any part of it. This type is
 * the reason a screen can show "configured" without the API ever having a code
 * path that returns a token.
 */
public record CredentialStatusResponse(
        String connectorId,
        String target,
        boolean configured,
        Instant updatedAt) {

    public static CredentialStatusResponse from(CredentialStatus status) {
        return new CredentialStatusResponse(
                status.connectorId(), status.target(), status.configured(), status.updatedAt());
    }
}
