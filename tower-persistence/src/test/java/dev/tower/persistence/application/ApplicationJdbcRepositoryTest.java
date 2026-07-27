package dev.tower.persistence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.tower.domain.application.Application;
import dev.tower.domain.application.ApplicationId;
import dev.tower.persistence.support.PersistenceTestSupport;

class ApplicationJdbcRepositoryTest {

    @TempDir
    Path tempDir;

    private ApplicationJdbcRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        JdbcClient jdbcClient = PersistenceTestSupport.migratedJdbcClient(tempDir);
        repository = new ApplicationJdbcRepository(jdbcClient);
    }

    @Test
    void savesAndReloadsAnApplicationByIdWithFieldsIntact() {
        Application saved = repository.save(Application.create("Payments", "Handles payment processing"));

        Application reloaded = repository.findById(saved.id()).orElseThrow();

        assertThat(reloaded).isEqualTo(saved);
        assertThat(reloaded.name()).isEqualTo("Payments");
        assertThat(reloaded.description()).isEqualTo("Handles payment processing");
    }

    @Test
    void findByIdReturnsEmptyForAnUnknownId() {
        assertThat(repository.findById(ApplicationId.newId())).isEmpty();
    }

    @Test
    void savingAgainWithTheSameIdUpdatesRatherThanDuplicates() {
        Application saved = repository.save(Application.create("Payments", "Original"));

        repository.save(saved.update("Payments API", "Updated description"));

        assertThat(repository.findAll()).hasSize(1);
        Application reloaded = repository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.name()).isEqualTo("Payments API");
        assertThat(reloaded.description()).isEqualTo("Updated description");
    }

    @Test
    void findAllOrdersByName() {
        repository.save(Application.create("Zephyr", ""));
        repository.save(Application.create("Alpha", ""));

        assertThat(repository.findAll()).extracting(Application::name).containsExactly("Alpha", "Zephyr");
    }

    @Test
    void existsByNameIgnoringCaseIsCaseInsensitive() {
        repository.save(Application.create("Payments", ""));

        assertThat(repository.existsByNameIgnoringCase("payments")).isTrue();
        assertThat(repository.existsByNameIgnoringCase("PAYMENTS")).isTrue();
        assertThat(repository.existsByNameIgnoringCase("Billing")).isFalse();
    }

    @Test
    void theDatabaseRejectsACaseInsensitiveDuplicateNameEvenIfTheApplicationLayerDidNotCatchIt() {
        repository.save(Application.create("Payments", ""));

        Application duplicate = Application.create("payments", "");
        assertThatThrownBy(() -> repository.save(duplicate)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void deleteByIdRemovesTheApplication() {
        Application saved = repository.save(Application.create("Payments", ""));

        repository.deleteById(saved.id());

        assertThat(repository.findById(saved.id())).isEmpty();
    }
}
