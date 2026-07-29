package dev.tower.connector.k8s;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ListOptionsBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.client.OpenShiftClient;
import org.springframework.stereotype.Component;

import dev.tower.connector.api.ConnectorCredential;
import dev.tower.connector.api.ConnectorException;
import dev.tower.connector.api.DeploymentLocator;
import dev.tower.connector.api.DeploymentPlatformConnector;
import dev.tower.connector.api.RunningWorkload;

/**
 * Reads what is running in a Kubernetes or OpenShift namespace (issue #48).
 *
 * <p>Strictly read-only (ADR-001, CM-01, FR-036, FR-039). Every call below is a
 * list operation. Two architecture tests guard this — one forbidding calls to
 * methods named for mutation from any connector package, one confining fabric8
 * types to this module — but neither is proof. Only the cluster's own audit log
 * can show what Tower actually asked for, which is why the Milestone 2 exit
 * criteria require reading it.
 *
 * <p>Reads both {@code Deployment} and OpenShift's {@code DeploymentConfig}.
 * DeploymentConfig is deprecated from OpenShift 4.14 but still carries plenty of
 * existing workloads, and a Connector that read only Deployments would report an
 * empty namespace — which under ADR-011 is indistinguishable from a namespace
 * where nothing is deployed. Reporting nothing and finding nothing must not look
 * the same.
 *
 * <p>The image comes from the pod template's container spec rather than from a
 * running pod's status. On OpenShift those differ: an ImageStream trigger
 * resolves the status image to a digest while the spec usually keeps the tag,
 * and the tag is what a version pattern needs. Where the spec is digest-pinned
 * too, the workload is reported as such and becomes unrecognized rather than
 * failing the run.
 *
 * <p>Fabric8's {@code openshift-client} is a superset of {@code
 * kubernetes-client}, so one dependency serves both platforms. Against plain
 * Kubernetes the DeploymentConfig read is simply skipped.
 */
@Component
public class KubernetesDeploymentConnector implements DeploymentPlatformConnector {

    /** Stable: stored Observations keep referring to it (FR-022). */
    public static final String CONNECTOR_ID = "kubernetes";

    private final Function<Config, OpenShiftClient> clientFactory;

    public KubernetesDeploymentConnector() {
        this(config -> new KubernetesClientBuilder().withConfig(config).build()
                .adapt(OpenShiftClient.class));
    }

    /** Visible for testing, so the mock server can supply a client. */
    KubernetesDeploymentConnector(Function<Config, OpenShiftClient> clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public String connectorId() {
        return CONNECTOR_ID;
    }

    @Override
    public List<RunningWorkload> readWorkloads(DeploymentLocator locator, ConnectorCredential credential) {
        try (OpenShiftClient client = connect(locator, credential)) {
            List<RunningWorkload> workloads = new ArrayList<>();

            client.apps().deployments().inNamespace(locator.scope()).list().getItems()
                    .forEach(deployment -> collect(
                            workloads, deployment.getMetadata(), deployment.getSpec().getTemplate()));

            readDeploymentConfigs(client, locator, workloads);

            return List.copyOf(workloads);
        } catch (KubernetesClientException e) {
            throw new ConnectorException(
                    "Could not read " + locator + ": " + describe(e), e);
        }
    }

    /**
     * OpenShift-only, and absent on plain Kubernetes.
     *
     * <p>A cluster without the DeploymentConfig API answers 404, which must not
     * fail a run that already read Deployments successfully. A permission
     * failure is different and is allowed to propagate: silently returning fewer
     * workloads because Tower may not look would misreport the namespace.
     */
    private void readDeploymentConfigs(
            OpenShiftClient client, DeploymentLocator locator, List<RunningWorkload> workloads) {
        try {
            client.deploymentConfigs().inNamespace(locator.scope()).list().getItems()
                    .forEach(config -> collect(
                            workloads, config.getMetadata(), config.getSpec().getTemplate()));
        } catch (KubernetesClientException e) {
            if (e.getCode() == 404) {
                return;
            }
            throw e;
        }
    }

    private static void collect(List<RunningWorkload> workloads, ObjectMeta metadata, PodTemplateSpec template) {
        if (metadata == null || template == null || template.getSpec() == null) {
            return;
        }
        Instant observedAt = creationTimestamp(metadata);
        for (Container container : template.getSpec().getContainers()) {
            ImageReference image = ImageReference.parse(container.getImage());
            if (image.name().isEmpty()) {
                continue;
            }
            String name = metadata.getName() + "/" + container.getName();
            workloads.add(image.tag() != null
                    ? RunningWorkload.tagged(name, image.name(), image.tag(), observedAt)
                    : RunningWorkload.digestPinned(name, image.name(), image.digest(), observedAt));
        }
    }

    /**
     * The platform's own timestamp, preserved for FR-021.
     *
     * <p>This is the workload's creation time, which is the closest thing a
     * Deployment offers to "when this became the running state" without reading
     * ReplicaSet history. Falling back to now() when it is missing or unparseable
     * is a deliberate approximation, and it is the only place this Connector
     * substitutes its own clock for the platform's.
     */
    private static Instant creationTimestamp(ObjectMeta metadata) {
        String timestamp = metadata.getCreationTimestamp();
        if (timestamp == null || timestamp.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(timestamp);
        } catch (DateTimeParseException e) {
            return Instant.now();
        }
    }

    @Override
    public void checkConnection(DeploymentLocator locator, ConnectorCredential credential) {
        try (OpenShiftClient client = connect(locator, credential)) {
            // Limited to one item: the cheapest read that still proves both
            // reachability and that the credential is accepted for this
            // namespace. It writes nothing (FR-061, FR-036).
            client.apps().deployments().inNamespace(locator.scope())
                    .list(new ListOptionsBuilder().withLimit(1L).build());
        } catch (KubernetesClientException e) {
            throw new ConnectorException(
                    "Could not connect to " + locator + ": " + describe(e), e);
        }
    }

    private OpenShiftClient connect(DeploymentLocator locator, ConnectorCredential credential) {
        ConfigBuilder config = new ConfigBuilder()
                .withMasterUrl(locator.target())
                .withNamespace(locator.scope());

        if (credential != null && credential.isPresent()) {
            char[] token = credential.token();
            try {
                config.withOauthToken(new String(token));
            } finally {
                Arrays.fill(token, '\0');
            }
        }
        return clientFactory.apply(config.build());
    }

    /**
     * A message fit to show a user.
     *
     * <p>Fabric8 puts the full response body in the exception message, which can
     * be long. The status message and code are what tells someone whether they
     * typed the wrong namespace or supplied an expired token.
     */
    private static String describe(KubernetesClientException e) {
        if (e.getStatus() != null && e.getStatus().getMessage() != null) {
            return e.getStatus().getMessage();
        }
        return e.getCode() > 0 ? "HTTP " + e.getCode() : e.getMessage();
    }
}
