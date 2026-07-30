package dev.tower.api.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import dev.tower.application.port.in.ApplicationUseCases;
import dev.tower.application.port.in.EnvironmentUseCases;
import dev.tower.application.port.in.ObservationUseCases;
import dev.tower.application.port.in.ReleasePackUseCases;
import dev.tower.domain.application.Application;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.environment.Stage;
import dev.tower.domain.observation.EnvironmentState;
import dev.tower.domain.observation.Observation;
import dev.tower.domain.observation.ObservationSource;
import dev.tower.domain.observation.ReleasePackProgression;
import dev.tower.domain.observation.StateComparison;
import dev.tower.domain.releasepack.ReleasePack;
import dev.tower.domain.releasepack.ReleasePackId;
import dev.tower.domain.releasepack.ReleasePackState;

/**
 * Milestone 4 (ADR-017): the point-in-time, comparison and progression
 * endpoints.
 *
 * <p>The derivations are covered by the domain and service tests. What is
 * checked here is what only the HTTP layer decides: that the instant reaches
 * the use case at all, that a malformed one is the caller's mistake rather than
 * the server's, and that the view names each kind of difference rather than
 * leaving a client to infer it from a null.
 */
@DisplayName("Reading Environment state as it stood")
class PointInTimeApiTest {

    private static final Environment UAT = Environment.create("UAT", Stage.PRE_PRODUCTION);
    private static final Application CUSTOMER = Application.create("Customer API", "");
    private static final ApplicationVersion V240 =
            ApplicationVersion.create(CUSTOMER.id(), "2.4.0", null, null, null, null);
    private static final ApplicationVersion V250 =
            ApplicationVersion.create(CUSTOMER.id(), "2.5.0", null, null, null, null);

    private static final Instant MARCH = Instant.parse("2026-03-15T10:00:00Z");
    private static final Instant MAY = Instant.parse("2026-05-05T10:00:00Z");

    private RecordingUseCases observations;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        observations = new RecordingUseCases();

        // Mocked rather than hand-written: the controller uses one method of
        // ReleasePackUseCases and stubbing twenty others would say nothing.
        ReleasePackUseCases packs = mock(ReleasePackUseCases.class);
        when(packs.list()).thenReturn(List.of(
                ReleasePack.create("Release 2026.08", "")
                        .addApplicationVersion(CUSTOMER.id(), V250.id())));

        mvc = MockMvcBuilders
                .standaloneSetup(new ObservationController(
                        observations, new Environments(), new Applications(), packs))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void passes_the_requested_instant_through_to_the_derivation() throws Exception {
        mvc.perform(get("/api/environments/{id}/state", UAT.id()).param("at", MARCH.toString()))
                .andExpect(status().isOk());

        assertThat(observations.askedFor).isEqualTo(MARCH);
    }

    @Test
    void treats_a_missing_instant_as_now_rather_than_as_an_error() throws Exception {
        mvc.perform(get("/api/environments/{id}/state", UAT.id()))
                .andExpect(status().isOk());

        assertThat(observations.askedFor).isNull();
    }

    @Test
    void an_unreadable_instant_is_the_callers_mistake_not_the_servers() throws Exception {
        // 400, not the 500 an unhandled conversion failure would give: a caller
        // told "an unexpected error occurred" has no way to know they can fix it.
        mvc.perform(get("/api/environments/{id}/state", UAT.id()).param("at", "yesterday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("ISO-8601")));
    }

    @Test
    void names_each_kind_of_difference_rather_than_leaving_it_to_be_inferred() throws Exception {
        mvc.perform(get("/api/environments/{id}/state/comparison", UAT.id())
                        .param("at", MARCH.toString()).param("againstAt", MAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identical").value(false))
                .andExpect(jsonPath("$.differences[0].kind").value("CHANGED"))
                .andExpect(jsonPath("$.differences[0].leftVersion").value("2.4.0"))
                .andExpect(jsonPath("$.differences[0].rightVersion").value("2.5.0"))
                // Milestone 4 asks which Release Pack a change came with. Named
                // for the version that arrived, not the one it replaced.
                .andExpect(jsonPath("$.differences[0].releasePacks[0]").value("Release 2026.08"))
                // Resolved to the version string a reader recognises, not the id.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(V240.id().toString()))));
    }

    @Test
    void compares_against_another_environment_when_asked() throws Exception {
        EnvironmentId other = EnvironmentId.newId();

        mvc.perform(get("/api/environments/{id}/state/comparison", UAT.id())
                        .param("against", other.toString()))
                .andExpect(status().isOk());

        assertThat(observations.comparedRight).isEqualTo(other);
    }

    @Test
    void compares_an_environment_with_itself_when_no_other_is_named() throws Exception {
        mvc.perform(get("/api/environments/{id}/state/comparison", UAT.id())
                        .param("againstAt", MAY.toString()))
                .andExpect(status().isOk());

        assertThat(observations.comparedRight).isEqualTo(UAT.id());
    }

    @Test
    void reports_a_partly_arrived_release_with_what_it_is_waiting_for() throws Exception {
        mvc.perform(get("/api/release-packs/{id}/progression", ReleasePackId.newId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observed").value(true))
                .andExpect(jsonPath("$.arrivals[0].environmentName").value("UAT"))
                .andExpect(jsonPath("$.arrivals[0].stage").value("PRE_PRODUCTION"))
                .andExpect(jsonPath("$.arrivals[0].complete").value(false))
                .andExpect(jsonPath("$.arrivals[0].completeAt").doesNotExist())
                // Named, not only counted.
                .andExpect(jsonPath("$.arrivals[0].missing[0].applicationName").value("Customer API"))
                .andExpect(jsonPath("$.arrivals[0].missing[0].version").value("2.5.0"));
    }

    // --- fakes ---------------------------------------------------------------

    /** Records what the controller asked for, and answers with a fixed derivation. */
    private static final class RecordingUseCases implements ObservationUseCases {

        private Instant askedFor;
        private EnvironmentId comparedRight;

        @Override
        public Observation recordManual(RecordManualObservation command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EnvironmentState environmentState(EnvironmentId environmentId) {
            return environmentStateAt(environmentId, null);
        }

        @Override
        public EnvironmentState environmentStateAt(EnvironmentId environmentId, Instant at) {
            askedFor = at;
            return EnvironmentState.from(environmentId, List.of(observation(V250, MAY)));
        }

        @Override
        public StateComparison compareStates(
                EnvironmentId leftEnvironment, Instant leftAt,
                EnvironmentId rightEnvironment, Instant rightAt) {
            comparedRight = rightEnvironment;
            return StateComparison.between(
                    EnvironmentState.from(leftEnvironment, List.of(observation(V240, MARCH))),
                    EnvironmentState.from(rightEnvironment, List.of(observation(V250, MAY))));
        }

        @Override
        public List<Observation> historyOf(EnvironmentId environmentId) {
            return List.of();
        }

        @Override
        public ReleasePackState stateOf(ReleasePackId releasePackId) {
            return ReleasePackState.PLANNED;
        }

        @Override
        public List<PackSighting> sightingsOf(ReleasePackId releasePackId) {
            return List.of();
        }

        @Override
        public ReleasePackProgression progressionOf(ReleasePackId releasePackId) {
            return new ReleasePackProgression(releasePackId, List.of(
                    new ReleasePackProgression.EnvironmentArrival(
                            UAT.id(), MARCH, null, 1, 2, List.of(V250.id()))));
        }

        private static Observation observation(ApplicationVersion version, Instant at) {
            return Observation.record(UAT.id(), version.applicationId(), version.id(), at,
                    ObservationSource.manual("costin"));
        }
    }

    private static final class Environments implements EnvironmentUseCases {

        @Override
        public Environment register(RegisterEnvironment command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Environment update(UpdateEnvironment command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Environment> list() {
            return List.of(UAT);
        }

        @Override
        public Environment get(EnvironmentId id) {
            return UAT;
        }

        @Override
        public void delete(EnvironmentId id) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class Applications implements ApplicationUseCases {

        @Override
        public Application register(RegisterApplication command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Application update(UpdateApplication command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Application> list() {
            return List.of(CUSTOMER);
        }

        @Override
        public Application get(ApplicationId id) {
            return CUSTOMER;
        }

        @Override
        public void delete(ApplicationId id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ApplicationVersion registerVersion(RegisterVersion command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ApplicationVersion> listVersions() {
            return List.of(V240, V250);
        }

        @Override
        public List<ApplicationVersion> listVersionsOf(ApplicationId applicationId) {
            return listVersions();
        }

        @Override
        public ApplicationVersion getVersion(ApplicationVersionId id) {
            return V250.id().equals(id) ? V250 : V240;
        }

        @Override
        public void deleteVersion(ApplicationVersionId id) {
            throw new UnsupportedOperationException();
        }
    }
}
