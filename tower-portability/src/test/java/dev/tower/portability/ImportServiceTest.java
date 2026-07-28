package dev.tower.portability;

import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.observation.Observation;
import dev.tower.domain.observation.ObservationId;
import dev.tower.domain.observation.ObservationSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportServiceTest {

    private static final Instant SEEN = Instant.parse("2026-07-20T09:00:00Z");
    private static final Instant EXPORTED = Instant.parse("2026-07-21T09:00:00Z");

    private InMemoryRepositories repositories;
    private ImportService importService;
    private ExportService exportService;

    @BeforeEach
    void setUp() {
        repositories = new InMemoryRepositories();
        importService = repositories.importService();
        exportService = repositories.exportService();
    }

    private TowerExport exportWithObservation(String origin) {
        return new TowerExport(
                TowerExport.CURRENT_SCHEMA_VERSION, "alice-laptop", EXPORTED,
                List.of(new TowerExport.EnvironmentRecord(
                        "11111111-1111-1111-1111-111111111111", "UAT", "VALIDATION")),
                List.of(new TowerExport.ApplicationRecord(
                        "22222222-2222-2222-2222-222222222222", "Customer API", "")),
                List.of(new TowerExport.ApplicationVersionRecord(
                        "33333333-3333-3333-3333-333333333333",
                        "22222222-2222-2222-2222-222222222222", "2.5.0", null, null, null, null)),
                List.of(), List.of(),
                List.of(new TowerExport.ObservationRecord(
                        "44444444-4444-4444-4444-444444444444",
                        "11111111-1111-1111-1111-111111111111",
                        "22222222-2222-2222-2222-222222222222",
                        "33333333-3333-3333-3333-333333333333",
                        SEEN, "manual", "alice", origin)));
    }

    @Nested
    class ProvenanceSurvivesTransfer {

        /**
         * The heart of ADR-010. Import transfers an existing immutable fact; it
         * never mints a new one. The receiving instance must report the fact as
         * something alice-laptop saw, not as something it saw itself.
         */
        @Test
        void an_imported_observation_still_names_the_instance_that_saw_it() {
            importService.apply(exportWithObservation("alice-laptop"), ConflictStrategy.SKIP);

            Observation imported = repositories.observations()
                    .findById(ObservationId.of("44444444-4444-4444-4444-444444444444"))
                    .orElseThrow();

            assertThat(imported.source().originInstance()).isEqualTo("alice-laptop");
            assertThat(imported.source().isImported()).isTrue();
            assertThat(imported.source().collector()).isEqualTo("manual");
            assertThat(imported.source().actor()).isEqualTo("alice");
            assertThat(imported.observedAt())
                    .as("the original observation time, not the import time")
                    .isEqualTo(SEEN);
            assertThat(imported.id().toString()).isEqualTo("44444444-4444-4444-4444-444444444444");
        }

        /**
         * A fact that has already travelled keeps naming its first observer.
         * Re-exporting from the second instance must not rewrite history to
         * credit the middleman.
         */
        @Test
        void re_exporting_an_imported_observation_preserves_the_original_origin() {
            importService.apply(exportWithObservation("alice-laptop"), ConflictStrategy.SKIP);

            TowerExport reExported = exportService.export("bob-laptop", true);

            assertThat(reExported.observations()).hasSize(1);
            assertThat(reExported.observations().get(0).originInstance())
                    .as("still alice, who actually saw it")
                    .isEqualTo("alice-laptop");
        }

        /** An instance's own Observations are stamped on the way out. */
        @Test
        void a_locally_observed_fact_is_stamped_with_this_instance_on_export() {
            repositories.observations().append(new Observation(
                    ObservationId.newId(),
                    EnvironmentId.of("11111111-1111-1111-1111-111111111111"),
                    ApplicationId.of("22222222-2222-2222-2222-222222222222"),
                    ApplicationVersionId.of("33333333-3333-3333-3333-333333333333"),
                    SEEN, ObservationSource.manual("bob")));

            TowerExport export = exportService.export("bob-laptop", true);

            assertThat(export.observations().get(0).originInstance()).isEqualTo("bob-laptop");
        }

        @Test
        void observations_can_be_left_out_of_an_export_entirely() {
            repositories.observations().append(new Observation(
                    ObservationId.newId(),
                    EnvironmentId.of("11111111-1111-1111-1111-111111111111"),
                    ApplicationId.of("22222222-2222-2222-2222-222222222222"),
                    ApplicationVersionId.of("33333333-3333-3333-3333-333333333333"),
                    SEEN, ObservationSource.manual("bob")));

            assertThat(exportService.export("bob-laptop", false).observations()).isEmpty();
        }
    }

    @Nested
    class ObservationsCannotConflict {

        /** Same id means the same immutable fact, so a second import is a no-op. */
        @Test
        void importing_the_same_file_twice_creates_no_duplicates() {
            TowerExport export = exportWithObservation("alice-laptop");

            importService.apply(export, ConflictStrategy.SKIP);
            ImportReport second = importService.apply(export, ConflictStrategy.SKIP);

            assertThat(repositories.observations().findAll()).hasSize(1);
            assertThat(second.entries())
                    .filteredOn(e -> e.type().equals("Observation"))
                    .allMatch(e -> e.outcome() == ImportReport.Outcome.UNCHANGED);
        }
    }

    @Nested
    class ConflictResolution {

        @Test
        void preview_changes_nothing() {
            ImportReport preview = importService.preview(
                    exportWithObservation("alice-laptop"), ConflictStrategy.REPLACE);

            assertThat(preview.applied()).isFalse();
            assertThat(preview.entries()).isNotEmpty();
            assertThat(repositories.environments().findAll()).isEmpty();
            assertThat(repositories.observations().findAll()).isEmpty();
        }

        @Test
        void skip_keeps_the_local_record() {
            TowerExport export = exportWithObservation("alice-laptop");
            importService.apply(export, ConflictStrategy.SKIP);

            TowerExport renamed = withEnvironmentNamed(export, "UAT-renamed-elsewhere");
            ImportReport report = importService.apply(renamed, ConflictStrategy.SKIP);

            assertThat(repositories.environments()
                    .findById(EnvironmentId.of("11111111-1111-1111-1111-111111111111"))
                    .orElseThrow().name()).isEqualTo("UAT");
            assertThat(report.count(ImportReport.Outcome.SKIPPED)).isPositive();
        }

        @Test
        void replace_takes_the_incoming_record() {
            TowerExport export = exportWithObservation("alice-laptop");
            importService.apply(export, ConflictStrategy.SKIP);

            importService.apply(withEnvironmentNamed(export, "UAT-renamed-elsewhere"),
                    ConflictStrategy.REPLACE);

            assertThat(repositories.environments()
                    .findById(EnvironmentId.of("11111111-1111-1111-1111-111111111111"))
                    .orElseThrow().name()).isEqualTo("UAT-renamed-elsewhere");
        }

        @Test
        void duplicate_keeps_both_under_distinguishable_names() {
            TowerExport export = exportWithObservation("alice-laptop");
            importService.apply(export, ConflictStrategy.SKIP);

            importService.apply(export, ConflictStrategy.DUPLICATE);

            assertThat(repositories.environments().findAll()).hasSize(2);
            assertThat(repositories.environments().findAll())
                    .extracting(e -> e.name())
                    .contains("UAT", "UAT (imported from alice-laptop)");
        }
    }

    @Nested
    class SchemaVersioning {

        @Test
        void a_newer_schema_is_refused_with_an_explanation_rather_than_misread() {
            TowerExport future = new TowerExport(
                    TowerExport.CURRENT_SCHEMA_VERSION + 1, "alice-laptop", EXPORTED,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

            assertThatThrownBy(() -> importService.preview(future, ConflictStrategy.SKIP))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot read")
                    .hasMessageContaining("understands up to version");
        }
    }

    @Nested
    class ExportContainsNoSecrets {

        /** ADR-010 and NFR-026: an export is meant to be shared. */
        @Test
        void the_export_record_has_no_field_that_could_carry_a_credential() {
            List<String> fieldNames = java.util.Arrays.stream(TowerExport.class.getRecordComponents())
                    .map(java.lang.reflect.RecordComponent::getName)
                    .toList();

            assertThat(fieldNames)
                    .noneMatch(name -> name.toLowerCase().contains("credential")
                            || name.toLowerCase().contains("secret")
                            || name.toLowerCase().contains("token")
                            || name.toLowerCase().contains("password")
                            || name.toLowerCase().contains("config"));
        }
    }

    private TowerExport withEnvironmentNamed(TowerExport export, String name) {
        return new TowerExport(
                export.schemaVersion(), export.exportedFrom(), export.exportedAt(),
                List.of(new TowerExport.EnvironmentRecord(
                        export.environments().get(0).id(), name, export.environments().get(0).stage())),
                export.applications(), export.applicationVersions(),
                export.promotionPaths(), export.releasePacks(), export.observations());
    }
}
