package dev.tower.persistence.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.tower.domain.application.Application;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.persistence.support.PersistenceTestSupport;

class ApplicationVersionJdbcRepositoryTest {

    @TempDir
    Path tempDir;

    private ApplicationJdbcRepository applications;
    private ApplicationVersionJdbcRepository versions;

    private Application payments;

    @BeforeEach
    void setUp() throws Exception {
        JdbcClient jdbcClient = PersistenceTestSupport.migratedJdbcClient(tempDir);
        applications = new ApplicationJdbcRepository(jdbcClient);
        versions = new ApplicationVersionJdbcRepository(jdbcClient);

        payments = applications.save(Application.create("Payments", ""));
    }

    @Test
    void savesAndReloadsAVersionWithAllFieldsIntact() {
        ApplicationVersion saved = versions.save(ApplicationVersion.create(
                payments.id(), "1.2.3", "main", "v1.2.3", "abc123", "build-42"));

        ApplicationVersion reloaded = versions.findById(saved.id()).orElseThrow();

        assertThat(reloaded).isEqualTo(saved);
        assertThat(reloaded.applicationId()).isEqualTo(payments.id());
        assertThat(reloaded.version()).isEqualTo("1.2.3");
        assertThat(reloaded.branch()).isEqualTo("main");
        assertThat(reloaded.tag()).isEqualTo("v1.2.3");
        assertThat(reloaded.commit()).isEqualTo("abc123");
        assertThat(reloaded.buildIdentifier()).isEqualTo("build-42");
    }

    @Test
    void optionalFieldsPersistAsNullWhenNotSupplied() {
        ApplicationVersion saved = versions.save(
                ApplicationVersion.create(payments.id(), "1.0.0", null, null, null, null));

        ApplicationVersion reloaded = versions.findById(saved.id()).orElseThrow();

        assertThat(reloaded.branch()).isNull();
        assertThat(reloaded.tag()).isNull();
        assertThat(reloaded.commit()).isNull();
        assertThat(reloaded.buildIdentifier()).isNull();
    }

    @Test
    void findByIdReturnsEmptyForAnUnknownId() {
        assertThat(versions.findById(ApplicationVersionId.newId())).isEmpty();
    }

    @Test
    void findAllByApplicationReturnsOnlyThatApplicationsVersions() {
        Application billing = applications.save(Application.create("Billing", ""));
        ApplicationVersion v1 = versions.save(ApplicationVersion.create(payments.id(), "1.0.0", null, null, null, null));
        versions.save(ApplicationVersion.create(billing.id(), "9.9.9", null, null, null, null));

        List<ApplicationVersion> found = versions.findAllByApplication(payments.id());

        assertThat(found).extracting(ApplicationVersion::id).containsExactly(v1.id());
    }

    @Test
    void findAllByIdReturnsOnlyTheRequestedVersions() {
        ApplicationVersion a = versions.save(ApplicationVersion.create(payments.id(), "1.0.0", null, null, null, null));
        ApplicationVersion b = versions.save(ApplicationVersion.create(payments.id(), "1.1.0", null, null, null, null));
        versions.save(ApplicationVersion.create(payments.id(), "1.2.0", null, null, null, null));

        List<ApplicationVersion> found = versions.findAllById(List.of(a.id(), b.id()));

        assertThat(found).extracting(ApplicationVersion::id).containsExactlyInAnyOrder(a.id(), b.id());
    }

    @Test
    void findAllByIdOfAnEmptyListReturnsAnEmptyListWithoutQueryingTheDatabase() {
        assertThat(versions.findAllById(List.of())).isEmpty();
    }

    @Test
    void existsByApplicationAndVersionDistinguishesBetweenApplications() {
        Application billing = applications.save(Application.create("Billing", ""));
        versions.save(ApplicationVersion.create(payments.id(), "1.0.0", null, null, null, null));

        assertThat(versions.existsByApplicationAndVersion(payments.id(), "1.0.0")).isTrue();
        assertThat(versions.existsByApplicationAndVersion(billing.id(), "1.0.0")).isFalse();
    }

    @Test
    void theDatabaseRejectsADuplicateVersionForTheSameApplication() {
        versions.save(ApplicationVersion.create(payments.id(), "1.0.0", null, null, null, null));

        ApplicationVersion duplicate = ApplicationVersion.create(payments.id(), "1.0.0", null, null, null, null);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> versions.save(duplicate))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void deleteByIdRemovesTheVersion() {
        ApplicationVersion saved = versions.save(
                ApplicationVersion.create(payments.id(), "1.0.0", null, null, null, null));

        versions.deleteById(saved.id());

        assertThat(versions.findById(saved.id())).isEmpty();
    }

    @Test
    void deletingTheOwningApplicationCascadesToItsVersions() {
        ApplicationVersion saved = versions.save(
                ApplicationVersion.create(payments.id(), "1.0.0", null, null, null, null));

        applications.deleteById(payments.id());

        assertThat(versions.findById(saved.id())).isEmpty();
    }
}
