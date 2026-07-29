package dev.tower.application.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.tower.application.binding.ApplicationBinding;
import dev.tower.application.binding.EnvironmentBinding;
import dev.tower.application.port.in.ExternalBindingUseCases.BindApplication;
import dev.tower.application.port.in.ExternalBindingUseCases.BindEnvironment;
import dev.tower.application.port.out.ApplicationRepository;
import dev.tower.application.port.out.EnvironmentRepository;
import dev.tower.application.port.out.ExternalBindingRepository;
import dev.tower.domain.application.Application;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.environment.Stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("External Binding use cases")
class ExternalBindingServiceTest {

    private static final String CONNECTOR = "kubernetes";

    private ExternalBindingService service;
    private Environment environment;
    private Application application;

    @BeforeEach
    void setUp() {
        var environments = new InMemoryEnvironments();
        var applications = new InMemoryApplications();
        environment = environments.save(Environment.create("UAT", Stage.VALIDATION));
        application = applications.save(Application.create("Customer API", null));
        service = new ExternalBindingService(new InMemoryBindings(), environments, applications);
    }

    @Nested
    @DisplayName("refuse to bind something that does not exist")
    class Existence {

        @Test
        void rejects_an_unknown_environment() {
            var command = new BindEnvironment(EnvironmentId.newId(), CONNECTOR, "https://a:6443", "ns");

            assertThatThrownBy(() -> service.bindEnvironment(command))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void rejects_an_unknown_application() {
            var command = new BindApplication(ApplicationId.newId(), CONNECTOR, "acme/api", null);

            assertThatThrownBy(() -> service.bindApplication(command))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void binds_an_environment_that_exists() {
            var bound = service.bindEnvironment(
                    new BindEnvironment(environment.id(), CONNECTOR, "https://a:6443", "customer-uat"));

            assertThat(bound.scope()).isEqualTo("customer-uat");
            assertThat(service.findEnvironmentBinding(environment.id(), CONNECTOR)).isPresent();
        }
    }

    @Nested
    @DisplayName("preview a version pattern before it can do damage")
    class Preview {

        @Test
        void reports_the_version_a_pattern_would_produce() {
            var preview = service.previewVersion("^release-(.+)$", "release-2026.08.1");

            assertThat(preview.matched()).isTrue();
            assertThat(preview.version()).isEqualTo("2026.08.1");
        }

        @Test
        void reports_a_tag_the_pattern_does_not_recognise_without_inventing_a_version() {
            var preview = service.previewVersion("^release-(.+)$", "latest");

            assertThat(preview.matched()).isFalse();
            assertThat(preview.version()).isNull();
        }

        @Test
        void treats_an_omitted_pattern_as_the_whole_tag() {
            var preview = service.previewVersion(null, "2026.08.1");

            assertThat(preview.versionPattern()).isEqualTo(ApplicationBinding.WHOLE_TAG);
            assertThat(preview.version()).isEqualTo("2026.08.1");
        }

        @Test
        void rejects_a_pattern_that_does_not_compile_here_too() {
            assertThatThrownBy(() -> service.previewVersion("^(unclosed", "anything"))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        void requires_a_tag_to_try_the_pattern_against() {
            assertThatThrownBy(() -> service.previewVersion("^(.+)$", " "))
                    .isInstanceOf(InvalidRequestException.class);
        }
    }

    @Test
    @DisplayName("re-pointing an Environment replaces its binding rather than conflicting")
    void rebinding_replaces() {
        service.bindEnvironment(new BindEnvironment(environment.id(), CONNECTOR, "https://a:6443", "old"));
        service.bindEnvironment(new BindEnvironment(environment.id(), CONNECTOR, "https://a:6443", "new"));

        assertThat(service.listEnvironmentBindings()).hasSize(1);
        assertThat(service.findEnvironmentBinding(environment.id(), CONNECTOR))
                .hasValueSatisfying(b -> assertThat(b.scope()).isEqualTo("new"));
    }

    private static final class InMemoryBindings implements ExternalBindingRepository {

        private final Map<String, EnvironmentBinding> environmentBindings = new HashMap<>();
        private final Map<String, ApplicationBinding> applicationBindings = new HashMap<>();

        @Override
        public EnvironmentBinding save(EnvironmentBinding binding) {
            environmentBindings.put(binding.environmentId() + "@" + binding.connectorId(), binding);
            return binding;
        }

        @Override
        public Optional<EnvironmentBinding> findEnvironmentBinding(EnvironmentId id, String connectorId) {
            return Optional.ofNullable(environmentBindings.get(id + "@" + connectorId));
        }

        @Override
        public List<EnvironmentBinding> findAllEnvironmentBindings(String connectorId) {
            return environmentBindings.values().stream()
                    .filter(b -> b.connectorId().equals(connectorId)).toList();
        }

        @Override
        public List<EnvironmentBinding> findAllEnvironmentBindings() {
            return new ArrayList<>(environmentBindings.values());
        }

        @Override
        public void deleteEnvironmentBinding(EnvironmentId id, String connectorId) {
            environmentBindings.remove(id + "@" + connectorId);
        }

        @Override
        public ApplicationBinding save(ApplicationBinding binding) {
            applicationBindings.put(binding.applicationId() + "@" + binding.connectorId(), binding);
            return binding;
        }

        @Override
        public Optional<ApplicationBinding> findApplicationBinding(ApplicationId id, String connectorId) {
            return Optional.ofNullable(applicationBindings.get(id + "@" + connectorId));
        }

        @Override
        public List<ApplicationBinding> findAllApplicationBindings(String connectorId) {
            return applicationBindings.values().stream()
                    .filter(b -> b.connectorId().equals(connectorId)).toList();
        }

        @Override
        public List<ApplicationBinding> findAllApplicationBindings() {
            return new ArrayList<>(applicationBindings.values());
        }

        @Override
        public void deleteApplicationBinding(ApplicationId id, String connectorId) {
            applicationBindings.remove(id + "@" + connectorId);
        }
    }

    private static final class InMemoryEnvironments implements EnvironmentRepository {

        private final Map<EnvironmentId, Environment> stored = new HashMap<>();

        @Override
        public Environment save(Environment environment) {
            stored.put(environment.id(), environment);
            return environment;
        }

        @Override
        public Optional<Environment> findById(EnvironmentId id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public List<Environment> findAll() {
            return new ArrayList<>(stored.values());
        }

        @Override
        public List<Environment> findAllById(List<EnvironmentId> ids) {
            return ids.stream().map(stored::get).filter(java.util.Objects::nonNull).toList();
        }

        @Override
        public boolean existsByNameIgnoringCase(String name) {
            return stored.values().stream().anyMatch(e -> e.name().equalsIgnoreCase(name));
        }

        @Override
        public void deleteById(EnvironmentId id) {
            stored.remove(id);
        }
    }

    private static final class InMemoryApplications implements ApplicationRepository {

        private final Map<ApplicationId, Application> stored = new HashMap<>();

        @Override
        public Application save(Application application) {
            stored.put(application.id(), application);
            return application;
        }

        @Override
        public Optional<Application> findById(ApplicationId id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public List<Application> findAll() {
            return new ArrayList<>(stored.values());
        }

        @Override
        public boolean existsByNameIgnoringCase(String name) {
            return stored.values().stream().anyMatch(a -> a.name().equalsIgnoreCase(name));
        }

        @Override
        public void deleteById(ApplicationId id) {
            stored.remove(id);
        }
    }
}
