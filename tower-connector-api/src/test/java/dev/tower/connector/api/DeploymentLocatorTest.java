package dev.tower.connector.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("A deployment locator")
class DeploymentLocatorTest {

    @Test
    void names_a_target_and_a_scope_without_naming_a_vendor() {
        var locator = new DeploymentLocator("prod-cluster", "customer-prod");

        assertThat(locator.target()).isEqualTo("prod-cluster");
        assertThat(locator.scope()).isEqualTo("customer-prod");
    }

    @Test
    void renders_readably_because_it_appears_in_failure_messages_and_run_records() {
        assertThat(new DeploymentLocator("prod-cluster", "customer-prod"))
                .hasToString("prod-cluster/customer-prod");
    }

    @Test
    void two_locators_for_the_same_place_are_equal_so_runs_can_be_grouped_by_it() {
        assertThat(new DeploymentLocator("c", "n")).isEqualTo(new DeploymentLocator("c", "n"));
    }

    @Test
    void rejects_a_missing_scope_rather_than_reading_an_entire_platform() {
        assertThatThrownBy(() -> new DeploymentLocator("prod-cluster", null))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("scope");
    }

    @Test
    void rejects_a_missing_target() {
        assertThatThrownBy(() -> new DeploymentLocator(" ", "customer-prod"))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("endpoint");
    }
}
