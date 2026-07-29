package dev.tower.application.binding;

import dev.tower.application.service.InvalidRequestException;
import dev.tower.domain.environment.EnvironmentId;

/**
 * Where a Connector should look to observe a given Environment (ADR-012, FR-055).
 *
 * <p>An Environment is a business concept with identity, name and Stage
 * (Domain-Model.md, ADR-005). It is not a namespace. This record holds the
 * correspondence between the two, and it lives in the application layer
 * precisely so that no vendor locator reaches a domain type — a boundary the
 * architecture test {@code domain_depends_only_on_the_jdk} makes structural
 * rather than a matter of discipline.
 *
 * <p>{@code target} and {@code scope} carry no vendor vocabulary. For Kubernetes
 * they are the API server URL and the namespace; for another platform they are
 * whatever that platform calls the same two ideas.
 *
 * <p>{@code target} doubles as the key under which the credential for this
 * endpoint is stored, so binding an Environment and supplying a token refer to
 * the same thing without the user naming a connection separately.
 *
 * @param environmentId the Environment this describes
 * @param connectorId   the Connector that can read it
 * @param target        the platform endpoint
 * @param scope         the partition within it
 */
public record EnvironmentBinding(
        EnvironmentId environmentId, String connectorId, String target, String scope) {

    public EnvironmentBinding {
        InvalidRequestException.require(environmentId != null,
                "A binding must name the Environment it describes.");
        connectorId = requireText(connectorId, "connectorId");
        target = requireText(target, "target");
        scope = requireText(scope, "scope");
    }

    private static String requireText(String value, String what) {
        InvalidRequestException.require(value != null && !value.isBlank(),
                "A binding must name its " + what + ".");
        return value.trim();
    }
}
