package dev.tower.connector.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("A running workload reported by a Deployment Platform")
class RunningWorkloadTest {

    private static final Instant SEEN_AT = Instant.parse("2026-07-28T09:15:00Z");

    @Nested
    @DisplayName("carries what the Collector needs and nothing it must not have")
    class Shape {

        @Test
        void keeps_the_image_and_tag_separate_so_a_binding_can_match_the_image_alone() {
            var workload = RunningWorkload.tagged("customer-api",
                    "registry.example/acme/customer-api", "2026.08.1", SEEN_AT);

            assertThat(workload.image()).isEqualTo("registry.example/acme/customer-api");
            assertThat(workload.imageTag()).isEqualTo("2026.08.1");
            assertThat(workload.imageReference())
                    .isEqualTo("registry.example/acme/customer-api:2026.08.1");
        }

        @Test
        void preserves_the_platform_timestamp_rather_than_the_time_it_was_read() {
            var workload = RunningWorkload.tagged("customer-api", "acme/customer-api", "1.0", SEEN_AT);

            assertThat(workload.observedAt()).isEqualTo(SEEN_AT);
        }
    }

    @Nested
    @DisplayName("tolerates a digest-pinned image, because OpenShift produces them")
    class DigestPinned {

        @Test
        void exists_rather_than_throwing_when_there_is_no_tag() {
            // An ImageStream trigger rewrites the image to a digest. Throwing here
            // would fail the entire run and hide every workload Tower could read.
            var workload = RunningWorkload.digestPinned("orders-api",
                    "image-registry.openshift-image-registry.svc:5000/acme/orders-api",
                    "sha256:abc123", SEEN_AT);

            assertThat(workload.imageTag()).isNull();
            assertThat(workload.digest()).isEqualTo("sha256:abc123");
        }

        @Test
        void says_it_cannot_yield_a_version_so_the_collector_reports_it_unrecognized() {
            var workload = RunningWorkload.digestPinned("orders-api", "acme/orders-api",
                    "sha256:abc123", SEEN_AT);

            assertThat(workload.hasVersionableTag()).isFalse();
        }

        @Test
        void renders_the_reference_the_platform_actually_gave() {
            var workload = RunningWorkload.digestPinned("orders-api", "acme/orders-api",
                    "sha256:abc123", SEEN_AT);

            assertThat(workload.imageReference()).isEqualTo("acme/orders-api@sha256:abc123");
        }

        @Test
        void a_tagged_workload_can_yield_a_version() {
            assertThat(RunningWorkload.tagged("api", "acme/api", "1.0", SEEN_AT).hasVersionableTag())
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("refuses to exist without the facts that make it usable")
    class Validation {

        @Test
        void rejects_a_missing_timestamp_because_FR_021_requires_one() {
            assertThatThrownBy(() -> RunningWorkload.tagged("api", "acme/api", "1.0", null))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("FR-021");
        }

        @Test
        void rejects_a_blank_name_because_an_unrecognized_workload_must_be_nameable() {
            assertThatThrownBy(() -> RunningWorkload.tagged("", "acme/api", "1.0", SEEN_AT))
                    .isInstanceOf(ConnectorException.class);
        }

        @Test
        void rejects_a_blank_image_because_a_binding_matches_on_it() {
            assertThatThrownBy(() -> RunningWorkload.tagged("api", " ", "1.0", SEEN_AT))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("image");
        }

        @Test
        void trims_surrounding_whitespace_so_a_binding_match_is_not_defeated_by_it() {
            var workload = RunningWorkload.tagged(" api ", " acme/api ", " 1.0 ", SEEN_AT);

            assertThat(workload.image()).isEqualTo("acme/api");
            assertThat(workload.imageTag()).isEqualTo("1.0");
        }

        @Test
        void treats_a_blank_tag_as_no_tag_rather_than_an_empty_version() {
            var workload = new RunningWorkload("api", "acme/api", "  ", null, SEEN_AT);

            assertThat(workload.imageTag()).isNull();
            assertThat(workload.hasVersionableTag()).isFalse();
        }
    }
}
