package dev.tower.connector.k8s;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Parsing a container image reference")
class ImageReferenceTest {

    @Test
    void splits_a_tagged_image() {
        var parsed = ImageReference.parse("registry.example/acme/customer-api:2026.08.1");

        assertThat(parsed.name()).isEqualTo("registry.example/acme/customer-api");
        assertThat(parsed.tag()).isEqualTo("2026.08.1");
        assertThat(parsed.digest()).isNull();
    }

    @Test
    void keeps_a_registry_port_out_of_the_tag() {
        // The reason this is not split(":"). A registry may carry a port, and
        // treating that colon as a tag separator would bind against the wrong image.
        var parsed = ImageReference.parse("registry.example:5000/acme/customer-api:2026.08.1");

        assertThat(parsed.name()).isEqualTo("registry.example:5000/acme/customer-api");
        assertThat(parsed.tag()).isEqualTo("2026.08.1");
    }

    @Test
    void recognises_a_registry_port_with_no_tag_at_all() {
        var parsed = ImageReference.parse("registry.example:5000/acme/customer-api");

        assertThat(parsed.name()).isEqualTo("registry.example:5000/acme/customer-api");
        assertThat(parsed.tag()).isEqualTo("latest");
    }

    @Test
    void separates_a_digest_and_reports_no_tag() {
        // OpenShift ImageStream triggers produce exactly this shape.
        var parsed = ImageReference.parse(
                "image-registry.openshift-image-registry.svc:5000/acme/api@sha256:abc123");

        assertThat(parsed.name())
                .isEqualTo("image-registry.openshift-image-registry.svc:5000/acme/api");
        assertThat(parsed.tag()).isNull();
        assertThat(parsed.digest()).isEqualTo("sha256:abc123");
    }

    @Test
    void treats_an_untagged_image_as_latest_because_that_is_what_kubernetes_runs() {
        var parsed = ImageReference.parse("acme/customer-api");

        assertThat(parsed.name()).isEqualTo("acme/customer-api");
        assertThat(parsed.tag()).isEqualTo("latest");
    }

    @Test
    void yields_an_empty_name_for_no_image_rather_than_failing() {
        assertThat(ImageReference.parse(null).name()).isEmpty();
        assertThat(ImageReference.parse("  ").name()).isEmpty();
    }
}
