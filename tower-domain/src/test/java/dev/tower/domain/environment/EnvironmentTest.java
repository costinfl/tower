package dev.tower.domain.environment;

import dev.tower.domain.shared.DomainException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvironmentTest {

    @Nested
    class StageClassification {

        /**
         * ADR-008. Stage is independent of the Environment name, because naming is
         * an organizational convention. Deriving state from names would break the
         * moment a team renamed SIT1 to QA2.
         */
        @Test
        void differently_named_environments_may_share_a_stage() {
            Environment sit = Environment.create("SIT1", Stage.VALIDATION);
            Environment uat = Environment.create("UAT", Stage.VALIDATION);
            Environment qa = Environment.create("QA2", Stage.VALIDATION);

            assertThat(sit.stage()).isEqualTo(uat.stage()).isEqualTo(qa.stage());
        }

        @Test
        void stage_ranking_is_explicit_rather_than_ordinal() {
            assertThat(Stage.DEVELOPMENT.rank()).isEqualTo(1);
            assertThat(Stage.VALIDATION.rank()).isEqualTo(2);
            assertThat(Stage.PRE_PRODUCTION.rank()).isEqualTo(3);
            assertThat(Stage.PRODUCTION.rank()).isEqualTo(4);
        }

        /**
         * ADR-008 derives Release Pack state as the highest Stage observed. The
         * Hotfix path in Scenarios.md has no pre-production Environment, so this
         * is what lets it reach Production without special handling — the defect
         * ADR-008 was written to fix.
         */
        @Test
        void a_path_without_pre_production_still_reaches_production() {
            Stage highest = Stage.DEVELOPMENT
                    .max(Stage.VALIDATION)
                    .max(Stage.PRODUCTION);

            assertThat(highest).isEqualTo(Stage.PRODUCTION);
            assertThat(highest.isAtLeast(Stage.PRE_PRODUCTION)).isTrue();
        }

        @Test
        void max_never_regresses() {
            assertThat(Stage.PRODUCTION.max(Stage.DEVELOPMENT)).isEqualTo(Stage.PRODUCTION);
            assertThat(Stage.VALIDATION.max(Stage.VALIDATION)).isEqualTo(Stage.VALIDATION);
            assertThat(Stage.VALIDATION.max(null)).isEqualTo(Stage.VALIDATION);
        }
    }

    @Nested
    class Validation {

        @Test
        void an_environment_requires_a_stage() {
            assertThatThrownBy(() -> Environment.create("UAT", null))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("ADR-008");
        }

        @Test
        void an_environment_requires_a_name() {
            assertThatThrownBy(() -> Environment.create("   ", Stage.VALIDATION))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("name is required");
        }

        @Test
        void names_are_trimmed() {
            assertThat(Environment.create("  UAT  ", Stage.VALIDATION).name()).isEqualTo("UAT");
        }

        @Test
        void a_name_may_not_exceed_the_maximum_length() {
            String tooLong = "e".repeat(Environment.NAME_MAX_LENGTH + 1);

            assertThatThrownBy(() -> Environment.create(tooLong, Stage.VALIDATION))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("at most");
        }
    }

    @Nested
    class Editing {

        @Test
        void renaming_and_reclassifying_preserve_identity() {
            Environment original = Environment.create("SIT1", Stage.VALIDATION);

            Environment renamed = original.rename("QA1");
            Environment reclassified = original.reclassify(Stage.PRE_PRODUCTION);

            assertThat(renamed.id()).isEqualTo(original.id());
            assertThat(reclassified.id()).isEqualTo(original.id());
            assertThat(renamed.stage()).isEqualTo(Stage.VALIDATION);
            assertThat(reclassified.name()).isEqualTo("SIT1");
        }

        @Test
        void identifiers_reject_malformed_input() {
            assertThatThrownBy(() -> EnvironmentId.of("not-a-uuid"))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("not a valid identifier");
        }
    }
}
