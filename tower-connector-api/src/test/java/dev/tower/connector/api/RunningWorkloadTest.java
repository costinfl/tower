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
            var workload = new RunningWorkload("customer-api", "registry.example/acme/customer-api",
                    "2026.08.1", SEEN_AT);

            assertThat(workload.image()).isEqualTo("registry.example/acme/customer-api");
            assertThat(workload.imageTag()).isEqualTo("2026.08.1");
            assertThat(workload.imageReference())
                    .isEqualTo("registry.example/acme/customer-api:2026.08.1");
        }

        @Test
        void preserves_the_platform_timestamp_rather_than_the_time_it_was_read() {
            var workload = new RunningWorkload("customer-api", "acme/customer-api", "1.0", SEEN_AT);

            assertThat(workload.observedAt()).isEqualTo(SEEN_AT);
        }
    }

    @Nested
    @DisplayName("refuses to exist without the facts that make it usable")
    class Validation {

        @Test
        void rejects_a_missing_timestamp_because_FR_021_requires_one() {
            assertThatThrownBy(() -> new RunningWorkload("api", "acme/api", "1.0", null))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("FR-021");
        }

        @Test
        void rejects_a_blank_image_tag_because_the_version_is_derived_from_it() {
            assertThatThrownBy(() -> new RunningWorkload("api", "acme/api", "  ", SEEN_AT))
                    .isInstanceOf(ConnectorException.class);
        }

        @Test
        void rejects_a_blank_name_because_an_unrecognized_workload_must_be_nameable() {
            assertThatThrownBy(() -> new RunningWorkload("", "acme/api", "1.0", SEEN_AT))
                    .isInstanceOf(ConnectorException.class);
        }

        @Test
        void trims_surrounding_whitespace_so_a_binding_match_is_not_defeated_by_it() {
            var workload = new RunningWorkload(" api ", " acme/api ", " 1.0 ", SEEN_AT);

            assertThat(workload.image()).isEqualTo("acme/api");
            assertThat(workload.imageTag()).isEqualTo("1.0");
        }
    }
}
