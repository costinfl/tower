package dev.tower.persistence.environment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.environment.Stage;
import dev.tower.persistence.support.PersistenceTestSupport;

class EnvironmentJdbcRepositoryTest {

    @TempDir
    Path tempDir;

    private EnvironmentJdbcRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        JdbcClient jdbcClient = PersistenceTestSupport.migratedJdbcClient(tempDir);
        repository = new EnvironmentJdbcRepository(jdbcClient);
    }

    @Test
    void savesAndReloadsAnEnvironmentByIdWithFieldsIntact() {
        Environment saved = repository.save(Environment.create("UAT", Stage.VALIDATION));

        Environment reloaded = repository.findById(saved.id()).orElseThrow();

        assertThat(reloaded).isEqualTo(saved);
        assertThat(reloaded.name()).isEqualTo("UAT");
        assertThat(reloaded.stage()).isEqualTo(Stage.VALIDATION);
    }

    @Test
    void findByIdReturnsEmptyForAnUnknownId() {
        assertThat(repository.findById(EnvironmentId.newId())).isEmpty();
    }

    @Test
    void savingAgainWithTheSameIdUpdatesRatherThanDuplicates() {
        Environment saved = repository.save(Environment.create("Production", Stage.PRODUCTION));

        Environment renamed = saved.rename("PROD").reclassify(Stage.PRODUCTION);
        repository.save(renamed);

        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findById(saved.id()).orElseThrow().name()).isEqualTo("PROD");
    }

    @Test
    void findAllReturnsEveryEnvironment() {
        repository.save(Environment.create("Dev1", Stage.DEVELOPMENT));
        repository.save(Environment.create("SIT1", Stage.VALIDATION));
        repository.save(Environment.create("Production", Stage.PRODUCTION));

        assertThat(repository.findAll()).extracting(Environment::name)
                .containsExactlyInAnyOrder("Dev1", "SIT1", "Production");
    }

    @Test
    void findAllByIdReturnsOnlyTheRequestedEnvironments() {
        Environment a = repository.save(Environment.create("Dev1", Stage.DEVELOPMENT));
        Environment b = repository.save(Environment.create("SIT1", Stage.VALIDATION));
        repository.save(Environment.create("Production", Stage.PRODUCTION));

        List<Environment> found = repository.findAllById(List.of(a.id(), b.id()));

        assertThat(found).extracting(Environment::id).containsExactlyInAnyOrder(a.id(), b.id());
    }

    @Test
    void findAllByIdOfAnEmptyListReturnsAnEmptyListWithoutQueryingTheDatabase() {
        assertThat(repository.findAllById(List.of())).isEmpty();
    }

    @Test
    void existsByNameIgnoringCaseIsCaseInsensitive() {
        repository.save(Environment.create("UAT", Stage.VALIDATION));

        assertThat(repository.existsByNameIgnoringCase("uat")).isTrue();
        assertThat(repository.existsByNameIgnoringCase("UAT")).isTrue();
        assertThat(repository.existsByNameIgnoringCase("Uat")).isTrue();
        assertThat(repository.existsByNameIgnoringCase("Production")).isFalse();
    }

    @Test
    void theDatabaseRejectsACaseInsensitiveDuplicateNameEvenIfTheApplicationLayerDidNotCatchIt() {
        repository.save(Environment.create("UAT", Stage.VALIDATION));

        Environment duplicate = Environment.create("uat", Stage.VALIDATION);
        assertThatDatabaseRejects(() -> repository.save(duplicate));
    }

    @Test
    void deleteByIdRemovesTheEnvironment() {
        Environment saved = repository.save(Environment.create("Dev1", Stage.DEVELOPMENT));

        repository.deleteById(saved.id());

        assertThat(repository.findById(saved.id())).isEmpty();
    }

    private static void assertThatDatabaseRejects(Runnable action) {
        org.assertj.core.api.Assertions.assertThatThrownBy(action::run)
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
