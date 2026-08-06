package dev.tower.api.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import dev.tower.api.error.ApiExceptionHandler;
import dev.tower.application.port.in.DashboardUseCases;
import dev.tower.domain.convergence.EnvironmentConvergence;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.environment.Stage;
import dev.tower.domain.releasepack.ReleasePackId;
import dev.tower.domain.releasepack.ReleasePackState;

/**
 * Milestone 5, issue #7: the dashboard API.
 *
 * <p>The composition is covered by DashboardServiceTest. What is checked here
 * is the shape a client sees, and the constraint that matters most: this
 * resource offers a read and nothing else.
 */
@DisplayName("The dashboard API")
class DashboardControllerTest {

    private static final EnvironmentId UAT = EnvironmentId.newId();
    private static final ReleasePackId RELEASE_A = ReleasePackId.newId();
    private static final ReleasePackId RELEASE_B = ReleasePackId.newId();
    private static final Instant MON = Instant.parse("2026-08-03T09:00:00Z");

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        DashboardUseCases dashboard = () -> new DashboardUseCases.Overview(
                List.of(new DashboardUseCases.EnvironmentSummary(
                        UAT, "UAT", Stage.PRE_PRODUCTION, true, MON, 3,
                        new EnvironmentConvergence(UAT, List.of(
                                new EnvironmentConvergence.ConvergingPack(
                                        RELEASE_A, "Release A",
                                        EnvironmentConvergence.Standing.PARTLY_OBSERVED,
                                        MON, null, 1, 2),
                                new EnvironmentConvergence.ConvergingPack(
                                        RELEASE_B, "Release B",
                                        EnvironmentConvergence.Standing.NOT_OBSERVED_HERE,
                                        null, null, 0, 1))))),
                List.of(new DashboardUseCases.ReleasePackSummary(
                        RELEASE_A, "Release A", ReleasePackState.VALIDATION,
                        "Regular", 2, 1, List.of("SIT"), MON)),
                new DashboardUseCases.Summary(2, 1, 1, 1, 0),
                new DashboardUseCases.SetupState(List.of(
                        new DashboardUseCases.SetupStep("environments", "Define your Environments",
                                "The places software runs.", true, false),
                        new DashboardUseCases.SetupStep("connectors", "Connect a system",
                                "Optional.", false, true)),
                        true));

        mvc = MockMvcBuilders.standaloneSetup(new DashboardController(dashboard))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void carries_the_setup_path_with_its_optional_flag() throws Exception {
        // The flag is what tells a reader "you can use Tower without this".
        // Dropping it in transit would silently turn an optional step into a
        // required one on the only screen that shows it.
        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setup.complete").value(true))
                .andExpect(jsonPath("$.setup.steps[0].id").value("environments"))
                .andExpect(jsonPath("$.setup.steps[0].done").value(true))
                .andExpect(jsonPath("$.setup.steps[0].optional").value(false))
                .andExpect(jsonPath("$.setup.steps[1].id").value("connectors"))
                .andExpect(jsonPath("$.setup.steps[1].optional").value(true));
    }

    @Test
    void reports_which_releases_are_heading_for_an_environment() throws Exception {
        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environments[0].name").value("UAT"))
                // Stated by the server so every client marks the same thing.
                .andExpect(jsonPath("$.environments[0].contested").value(true))
                .andExpect(jsonPath("$.environments[0].converging[0].name").value("Release A"))
                .andExpect(jsonPath("$.environments[0].converging[1].name").value("Release B"));
    }

    @Test
    void names_each_standing_rather_than_leaving_it_to_be_inferred() throws Exception {
        // A client that had to work out "not reported" from a null instant would
        // sooner or later render it as "not deployed".
        mvc.perform(get("/api/dashboard"))
                .andExpect(jsonPath("$.environments[0].converging[0].standing").value("PARTLY_OBSERVED"))
                .andExpect(jsonPath("$.environments[0].converging[1].standing").value("NOT_OBSERVED_HERE"))
                .andExpect(jsonPath("$.environments[0].converging[1].observedCount").value(0))
                .andExpect(jsonPath("$.environments[0].converging[1].packedCount").value(1));
    }

    @Test
    void carries_the_summary_counts() throws Exception {
        mvc.perform(get("/api/dashboard"))
                .andExpect(jsonPath("$.summary.activeReleasePacks").value(2))
                .andExpect(jsonPath("$.summary.contestedEnvironments").value(1))
                .andExpect(jsonPath("$.summary.packsNotObservedAnywhere").value(1))
                .andExpect(jsonPath("$.releasePacks[0].state").value("VALIDATION"))
                .andExpect(jsonPath("$.releasePacks[0].furthestEnvironments[0]").value("SIT"));
    }

    @Test
    void offers_a_read_and_nothing_else() throws Exception {
        // The dashboard is where a promote button would feel most natural and
        // would do the most damage to what Tower is (ADR-001, Guardrails.md).
        mvc.perform(post("/api/dashboard")).andExpect(status().is4xxClientError());
        mvc.perform(put("/api/dashboard").contentType("application/json").content("{}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(delete("/api/dashboard")).andExpect(status().is4xxClientError());
    }
}
