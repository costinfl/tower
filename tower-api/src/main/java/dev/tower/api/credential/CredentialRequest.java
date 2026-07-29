package dev.tower.api.credential;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for PUT /api/credentials.
 *
 * <p>Carries the secret inbound only. There is no corresponding response type
 * that holds one: Implementation-Plan.md / Credential Handling makes credentials
 * write-only through the API, so the value travels in this direction and never
 * back.
 *
 * <p>{@code toString} is overridden because a validation failure, a request log
 * or a debugger would otherwise render the token. A record's generated
 * {@code toString} prints every component, which is exactly wrong here.
 */
public record CredentialRequest(
        @NotBlank(message = "connectorId is required") String connectorId,
        @NotBlank(message = "target is required") String target,
        @NotBlank(message = "secret is required") String secret) {

    @Override
    public String toString() {
        return "CredentialRequest[connectorId=" + connectorId + ", target=" + target + ", secret=****]";
    }
}
