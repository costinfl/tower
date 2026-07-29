package dev.tower.connector.k8s;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.openshift.api.model.DeploymentConfigBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.server.mock.EnableOpenShiftMockClient;
import io.fabric8.openshift.client.server.mock.OpenShiftMockServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.tower.connector.api.ConnectorCredential;
import dev.tower.connector.api.ConnectorException;
import dev.tower.connector.api.DeploymentLocator;
import dev.tower.connector.api.RunningWorkload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives the Connector against fabric8's in-process mock API server.
 *
 * <p>No cluster and no network: the mock speaks the real Kubernetes and OpenShift
 * wire protocol over a local socket, so the client code under test is the code
 * that will talk to a real cluster. What this cannot prove is that Tower issues
 * only read verbs — the cluster's own audit log is the evidence for that, which
 * is why the Milestone 2 exit criteria require reading it.
 *
 * <p>Flat rather than nested: the fabric8 JUnit extension reads its annotation
 * from the test class itself and does not find it on {@code @Nested} members.
 */
@EnableOpenShiftMockClient(crud = true)
@DisplayName("The Kubernetes Deployment Platform Connector")
class KubernetesDeploymentConnectorTest {

    private static final String NAMESPACE = "customer-uat";
    private static final String UNREACHABLE = "https://127.0.0.1:1";

    /** Both injected by the fabric8 extension. */
    OpenShiftClient client;
    OpenShiftMockServer server;

    /**
     * A fresh client per call, from the mock server.
     *
     * <p>Deliberately not the shared injected client. The Connector closes its
     * client after every call, which is right in production where the factory
     * builds one per call — handing back a shared instance would leave it closed
     * for the next operation, and the test would be exercising something the
     * application never does.
     *
     * <p>Everything after the connection — listing, parsing, mapping — is the
     * production path.
     */
    private KubernetesDeploymentConnector connector() {
        return new KubernetesDeploymentConnector(config -> server.createOpenShiftClient());
    }

    private DeploymentLocator locator() {
        return new DeploymentLocator(client.getMasterUrl().toString(), NAMESPACE);
    }

    private void givenDeployment(String name, String image) {
        client.apps().deployments().inNamespace(NAMESPACE).resource(new DeploymentBuilder()
                .withNewMetadata()
                .withName(name).withNamespace(NAMESPACE)
                .withCreationTimestamp("2026-07-20T08:30:00Z")
                .endMetadata()
                .withNewSpec().withNewTemplate().withNewSpec()
                .addNewContainer().withName("app").withImage(image).endContainer()
                .endSpec().endTemplate().endSpec()
                .build()).create();
    }

    private void givenDeploymentConfig(String name, String image) {
        client.deploymentConfigs().inNamespace(NAMESPACE).resource(new DeploymentConfigBuilder()
                .withNewMetadata()
                .withName(name).withNamespace(NAMESPACE)
                .withCreationTimestamp("2026-07-21T09:00:00Z")
                .endMetadata()
                .withNewSpec().withNewTemplate().withNewSpec()
                .addNewContainer().withName("app").withImage(image).endContainer()
                .endSpec().endTemplate().endSpec()
                .build()).create();
    }

    @Test
    void reads_a_deployment_with_its_image_and_tag_separated() {
        givenDeployment("customer-api", "registry.example/acme/customer-api:2026.08.1");

        List<RunningWorkload> workloads = connector().readWorkloads(locator(), ConnectorCredential.none());

        assertThat(workloads).hasSize(1);
        assertThat(workloads.get(0).image()).isEqualTo("registry.example/acme/customer-api");
        assertThat(workloads.get(0).imageTag()).isEqualTo("2026.08.1");
        assertThat(workloads.get(0).hasVersionableTag()).isTrue();
    }

    @Test
    void preserves_the_platform_timestamp_rather_than_using_its_own_clock() {
        // The mock server stamps its own creationTimestamp on create, exactly as a
        // real API server does, so a fixed literal here would never survive.
        // Comparing against what the server actually stored is the real assertion:
        // a Connector calling Instant.now() at read time would differ from it.
        givenDeployment("customer-api", "acme/customer-api:1.0");
        String fromPlatform = client.apps().deployments().inNamespace(NAMESPACE)
                .withName("customer-api").get().getMetadata().getCreationTimestamp();

        var workload = connector().readWorkloads(locator(), ConnectorCredential.none()).get(0);

        assertThat(fromPlatform).isNotBlank();
        assertThat(workload.observedAt()).isEqualTo(Instant.parse(fromPlatform));
    }

    @Test
    void reads_an_openshift_deployment_config_too() {
        // A namespace using only DeploymentConfig would otherwise report as empty,
        // which under ADR-011 is indistinguishable from nothing being deployed.
        givenDeploymentConfig("legacy-api", "acme/legacy-api:3.2.0");

        List<RunningWorkload> workloads = connector().readWorkloads(locator(), ConnectorCredential.none());

        assertThat(workloads).extracting(RunningWorkload::name).contains("legacy-api/app");
    }

    @Test
    void reports_a_digest_pinned_workload_instead_of_failing_the_whole_run() {
        givenDeployment("customer-api", "acme/customer-api:2026.08.1");
        givenDeployment("orders-api",
                "image-registry.openshift-image-registry.svc:5000/acme/orders-api@sha256:abc123");

        List<RunningWorkload> workloads = connector().readWorkloads(locator(), ConnectorCredential.none());

        assertThat(workloads).hasSize(2);
        var pinned = workloads.stream()
                .filter(w -> w.name().startsWith("orders-api")).findFirst().orElseThrow();
        assertThat(pinned.hasVersionableTag()).isFalse();
        assertThat(pinned.digest()).isEqualTo("sha256:abc123");
        assertThat(pinned.imageReference()).endsWith("@sha256:abc123");
    }

    @Test
    void names_each_container_so_an_unrecognized_workload_can_be_pointed_at() {
        givenDeployment("customer-api", "acme/customer-api:1.0");

        assertThat(connector().readWorkloads(locator(), ConnectorCredential.none()))
                .extracting(RunningWorkload::name).containsExactly("customer-api/app");
    }

    @Test
    void returns_an_empty_list_for_a_namespace_holding_nothing() {
        assertThat(connector().readWorkloads(locator(), ConnectorCredential.none())).isEmpty();
    }

    @Test
    void reports_an_unreachable_platform_rather_than_returning_nothing() {
        // Returning an empty list here would look like an empty Environment and
        // make Tower report that a deployment had disappeared.
        var unreachable = new KubernetesDeploymentConnector();

        assertThatThrownBy(() -> unreachable.readWorkloads(
                new DeploymentLocator(UNREACHABLE, NAMESPACE),
                ConnectorCredential.bearerToken("token".toCharArray())))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("Could not read");
    }

    @Test
    void checks_a_reachable_connection_without_writing() {
        connector().checkConnection(locator(), ConnectorCredential.none());
    }

    @Test
    void reports_a_connection_it_cannot_make() {
        var unreachable = new KubernetesDeploymentConnector();

        assertThatThrownBy(() -> unreachable.checkConnection(
                new DeploymentLocator(UNREACHABLE, NAMESPACE), ConnectorCredential.none()))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("Could not connect");
    }

    @Test
    void identifies_itself_stably_because_stored_observations_refer_to_it() {
        assertThat(connector().connectorId()).isEqualTo("kubernetes");
    }

    /**
     * ADR-001, CM-01, FR-036, FR-039: Tower never modifies an External System.
     *
     * <p>The strongest evidence available without a cluster. The ArchUnit rules
     * check that no method is <em>named</em> for mutation, which is a heuristic;
     * this inspects the HTTP verbs actually put on the wire. A Connector that
     * issued a PATCH through a differently named method would pass those rules
     * and fail here.
     *
     * <p>Still not proof — only the cluster's own audit log shows what a real
     * run did, which is why the Milestone 2 exit criteria require reading it.
     */
    @Test
    void issues_only_read_verbs_against_the_platform() throws InterruptedException {
        givenDeployment("customer-api", "acme/customer-api:1.0");
        givenDeploymentConfig("legacy-api", "acme/legacy-api:2.0");

        // Discard the fixture writes above: they are this test's setup, not the
        // Connector's traffic.
        drainRecordedRequests();

        connector().readWorkloads(locator(), ConnectorCredential.none());
        connector().checkConnection(locator(), ConnectorCredential.none());

        List<String> methods = recordedMethods();
        assertThat(methods).isNotEmpty();
        assertThat(methods).containsOnly("GET");
    }

    /*
     * takeRequest() blocks forever once the queue is empty, and getRequestCount()
     * is a cumulative total that never decreases — so it cannot be used as a
     * "how many are left" guard. The timed variant returning null is the only
     * safe way to drain.
     */
    private void drainRecordedRequests() throws InterruptedException {
        while (server.takeRequest(100, TimeUnit.MILLISECONDS) != null) {
            // discard
        }
    }

    private List<String> recordedMethods() throws InterruptedException {
        var methods = new ArrayList<String>();
        RecordedRequest request;
        while ((request = server.takeRequest(100, TimeUnit.MILLISECONDS)) != null) {
            methods.add(request.getMethod());
        }
        return methods;
    }
}
