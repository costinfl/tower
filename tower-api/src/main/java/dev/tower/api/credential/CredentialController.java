package dev.tower.api.credential;

import java.util.Arrays;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.tower.application.port.out.ConnectorCredentialsPort;

/**
 * Issue #46: REST API for Connector credentials.
 *
 * <p>Write-only by construction. {@code PUT} accepts a secret and {@code GET}
 * returns {@link CredentialStatusResponse}, which has no field capable of
 * carrying one. There is deliberately no endpoint that reads a credential back:
 * the only caller that needs the value is the Collector, which reaches the port
 * directly inside the process.
 *
 * <p>This controller talks to an outbound port rather than a use case, which is
 * unusual here. Storing a credential is not a business operation — NFR-028 puts
 * it outside the business architecture — so inventing a use case to wrap it
 * would add a layer that carries no rule.
 */
@RestController
@RequestMapping("/api/credentials")
public class CredentialController {

    private final ConnectorCredentialsPort credentials;

    public CredentialController(ConnectorCredentialsPort credentials) {
        this.credentials = credentials;
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void store(@Valid @RequestBody CredentialRequest request) {
        char[] secret = request.secret().toCharArray();
        try {
            credentials.store(request.connectorId(), request.target(), secret);
        } finally {
            // The String inside the request still holds it until collection, so
            // this narrows the window rather than closing it. Worth doing anyway.
            Arrays.fill(secret, '\0');
        }
    }

    @GetMapping
    public CredentialStatusResponse status(
            @RequestParam("connectorId") String connectorId,
            @RequestParam("target") String target) {
        return CredentialStatusResponse.from(credentials.status(connectorId, target));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forget(
            @RequestParam("connectorId") String connectorId,
            @RequestParam("target") String target) {
        credentials.forget(connectorId, target);
    }
}
