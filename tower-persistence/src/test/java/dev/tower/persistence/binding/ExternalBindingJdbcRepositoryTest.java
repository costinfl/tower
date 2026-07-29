package dev.tower.persistence.binding;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.tower.application.binding.ApplicationBinding;
import dev.tower.application.binding.EnvironmentBinding;
import dev.tower.domain.application.Application;
import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.environment.Stage;
import dev.tower.persistence.application.ApplicationJdbcRepository;
import dev.tower.persistence.environment.EnvironmentJdbcRepository;
import dev.tower.persistence.support.PersistenceTestSupport;

@DisplayName("The External Binding repository")
class ExternalBindingJdbcRepositoryTest {

    private static final String CONNECTOR = "kubernetes";

    @TempDir
    Path tempDir;

    private ExternalBindingJdbcRepository repository;
    private EnvironmentJdbcRepository environments;
    private ApplicationJdbcRepository applications;

    @BeforeEach
    void setUp() throws Exception {
        JdbcClient jdbcClient = PersistenceTestSupport.migratedJdbcClient(tempDir);
        repository = new ExternalBindingJdbcRepository(jdbcClient);
        environments = new EnvironmentJdbcRepository(jdbcClient);
        applications = new ApplicationJdbcRepository(jdbcClient);
    }

    private Environment anEnvironment(String name) {
        return environments.save(Environment.create(name, Stage.VALIDATION));
    }

    private Application anApplication(String name) {
        return applications.save(Application.create(name, null));
    }

    @Nested
    @DisplayName("stores Environment bindings")
    class EnvironmentBindings {

        @Test
        void saves_and_reloads_a_binding_with_its_locator_intact() {
            var environment = anEnvironment("UAT");
            var binding = new EnvironmentBinding(
                    environment.id(), CONNECTOR, "https://api.cluster.example:6443", "customer-uat");

            repository.save(binding);

            assertThat(repository.findEnvironmentBinding(environment.id(), CONNECTOR))
                    .contains(binding);
        }

        @Test
        void replaces_rather_than_duplicates_when_an_environment_is_repointed() {
            var environment = anEnvironment("UAT");
            repository.save(new EnvironmentBinding(
                    environment.id(), CONNECTOR, "https://old.example:6443", "old-namespace"));

            repository.save(new EnvironmentBinding(
                    environment.id(), CONNECTOR, "https://new.example:6443", "new-namespace"));

            assertThat(repository.findAllEnvironmentBindings()).hasSize(1);
            assertThat(repository.findEnvironmentBinding(environment.id(), CONNECTOR))
                    .hasValueSatisfying(b -> assertThat(b.scope()).isEqualTo("new-namespace"));
        }

        @Test
        void keeps_bindings_for_different_connectors_apart() {
            var environment = anEnvironment("UAT");
            repository.save(new EnvironmentBinding(environment.id(), CONNECTOR, "https://a:6443", "ns-a"));
            repository.save(new EnvironmentBinding(environment.id(), "openshift", "https://b:6443", "ns-b"));

            assertThat(repository.findAllEnvironmentBindings(CONNECTOR)).hasSize(1);
            assertThat(repository.findAllEnvironmentBindings()).hasSize(2);
        }

        @Test
        void is_empty_for_an_unbound_environment() {
            assertThat(repository.findEnvironmentBinding(EnvironmentId.newId(), CONNECTOR)).isEmpty();
        }

        @Test
        void deletes_a_binding_on_request() {
            var environment = anEnvironment("UAT");
            repository.save(new EnvironmentBinding(environment.id(), CONNECTOR, "https://a:6443", "ns"));

            repository.deleteEnvironmentBinding(environment.id(), CONNECTOR);

            assertThat(repository.findEnvironmentBinding(environment.id(), CONNECTOR)).isEmpty();
        }
    }

    @Nested
    @DisplayName("stores Application bindings")
    class ApplicationBindings {

        @Test
        void saves_and_reloads_a_binding_with_its_version_pattern_intact() {
            var application = anApplication("Customer API");
            var binding = new ApplicationBinding(
                    application.id(), CONNECTOR, "registry.example/acme/customer-api", "^release-(.+)$");

            repository.save(binding);

            assertThat(repository.findApplicationBinding(application.id(), CONNECTOR)).contains(binding);
        }

        @Test
        void stores_the_default_pattern_explicitly_rather_than_as_null() {
            var application = anApplication("Customer API");
            repository.save(new ApplicationBinding(
                    application.id(), CONNECTOR, "acme/customer-api", null));

            assertThat(repository.findApplicationBinding(application.id(), CONNECTOR))
                    .hasValueSatisfying(b ->
                            assertThat(b.versionPattern()).isEqualTo(ApplicationBinding.WHOLE_TAG));
        }

        @Test
        void replaces_rather_than_duplicates_when_a_pattern_is_corrected() {
            var application = anApplication("Customer API");
            repository.save(new ApplicationBinding(application.id(), CONNECTOR, "acme/api", "^(.+)$"));

            repository.save(new ApplicationBinding(application.id(), CONNECTOR, "acme/api", "^v(.+)$"));

            assertThat(repository.findAllApplicationBindings()).hasSize(1);
            assertThat(repository.findApplicationBinding(application.id(), CONNECTOR))
                    .hasValueSatisfying(b -> assertThat(b.versionPattern()).isEqualTo("^v(.+)$"));
        }

        @Test
        void deletes_a_binding_on_request() {
            var application = anApplication("Customer API");
            repository.save(new ApplicationBinding(application.id(), CONNECTOR, "acme/api", null));

            repository.deleteApplicationBinding(application.id(), CONNECTOR);

            assertThat(repository.findApplicationBinding(application.id(), CONNECTOR)).isEmpty();
        }
    }

    @Test
    @DisplayName("loses a binding when its Environment is deleted, because it then describes nothing")
    void cascades_when_the_environment_goes_away() {
        var environment = anEnvironment("Scratch");
        repository.save(new EnvironmentBinding(environment.id(), CONNECTOR, "https://a:6443", "ns"));

        environments.deleteById(environment.id());

        assertThat(repository.findEnvironmentBinding(environment.id(), CONNECTOR)).isEmpty();
    }
}
