package dev.tower.api;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import dev.tower.application.port.in.EnvironmentUseCases;
import dev.tower.application.port.in.ExternalBindingUseCases;
import dev.tower.application.port.out.ConnectorCredentialsPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the whole application context.
 *
 * <p>This test exists because of a real defect it would have caught. Issue #46
 * added a second constructor to a {@code @Component}; Spring could no longer
 * choose between them and fell back to a no-argument constructor that does not
 * exist. Every unit test passed and {@code mvn verify} was green, because
 * nothing in the build had ever started the context — the failure appeared only
 * on {@code spring-boot:run}.
 *
 * <p>It asserts almost nothing on purpose. Context startup is the assertion:
 * every bean is constructed, every dependency resolved, every adapter found on
 * the runtime classpath. The beans checked below are the ones this milestone
 * added, so a missing wiring is named rather than merely implied.
 */
@SpringBootTest
@DisplayName("The application context")
class ApplicationContextLoadsTest {

    /*
     * ADR-009 resolves application data to ~/.tower, and TowerHome reads the
     * TOWER_HOME environment variable or the user.home system property rather
     * than a Spring property — so @DynamicPropertySource cannot redirect it.
     *
     * A static initializer is the only hook that runs before Spring builds the
     * context for this class. Without it the test would create, migrate and
     * write to the developer's own Tower directory. CI already sets TOWER_HOME,
     * which takes precedence and is itself a temporary path, so this only
     * matters on a developer machine.
     */
    static {
        if (System.getenv("TOWER_HOME") == null) {
            try {
                System.setProperty("user.home",
                        Files.createTempDirectory("tower-context-test").toString());
            } catch (IOException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    @Autowired
    private ConnectorCredentialsPort credentials;

    @Autowired
    private ExternalBindingUseCases bindings;

    @Autowired
    private EnvironmentUseCases environments;

    @Test
    void starts_with_every_bean_this_milestone_added() {
        assertThat(credentials).isNotNull();
        assertThat(bindings).isNotNull();
        assertThat(environments).isNotNull();
    }

    @Test
    void resolves_the_credential_store_through_its_port() {
        assertThat(credentials.status("kubernetes", "https://unused.example:6443").configured()).isFalse();
    }
}
