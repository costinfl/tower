package dev.tower.api.credential;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import dev.tower.application.port.out.ConnectorCredentialsPort;
import dev.tower.application.port.out.CredentialStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #46. The property under test is that no response ever carries a stored
 * credential, which is the one mistake this API must not be able to make.
 */
@DisplayName("The credential API")
class CredentialControllerTest {

    private static final String CONNECTOR = "kubernetes";
    private static final String TARGET = "https://api.cluster.example:6443";
    private static final String TOKEN = "sha256~a-real-looking-bearer-token";

    private RecordingCredentials credentials;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        credentials = new RecordingCredentials();
        mvc = MockMvcBuilders.standaloneSetup(new CredentialController(credentials)).build();
    }

    private String body(String secret) {
        return """
                {"connectorId":"%s","target":"%s","secret":"%s"}
                """.formatted(CONNECTOR, TARGET, secret);
    }

    @Test
    void stores_a_submitted_credential() throws Exception {
        mvc.perform(put("/api/credentials").contentType("application/json").content(body(TOKEN)))
                .andExpect(status().isNoContent());

        assertThat(credentials.stored).containsEntry(CONNECTOR + "@" + TARGET, TOKEN);
    }

    @Test
    void reports_that_a_credential_is_configured_without_returning_it() throws Exception {
        credentials.stored.put(CONNECTOR + "@" + TARGET, TOKEN);

        String response = mvc.perform(get("/api/credentials")
                        .param("connectorId", CONNECTOR).param("target", TARGET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(TOKEN);
    }

    @Test
    void reports_an_unconfigured_credential_without_failing() throws Exception {
        mvc.perform(get("/api/credentials").param("connectorId", CONNECTOR).param("target", TARGET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    void forgets_a_credential_on_request() throws Exception {
        credentials.stored.put(CONNECTOR + "@" + TARGET, TOKEN);

        mvc.perform(delete("/api/credentials")
                        .param("connectorId", CONNECTOR).param("target", TARGET))
                .andExpect(status().isNoContent());

        assertThat(credentials.stored).isEmpty();
    }

    @Test
    void rejects_a_request_with_no_secret() throws Exception {
        mvc.perform(put("/api/credentials").contentType("application/json").content(body("")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void never_renders_the_secret_when_the_request_is_printed() {
        var request = new CredentialRequest(CONNECTOR, TARGET, TOKEN);

        assertThat(request.toString()).doesNotContain(TOKEN).contains("****");
    }

    /** Stands in for tower-config, which is runtime scope and unreachable from here. */
    private static final class RecordingCredentials implements ConnectorCredentialsPort {

        private final Map<String, String> stored = new HashMap<>();

        private static String key(String connectorId, String target) {
            return connectorId + "@" + target;
        }

        @Override
        public void store(String connectorId, String target, char[] secret) {
            stored.put(key(connectorId, target), new String(secret));
        }

        @Override
        public Optional<char[]> secretFor(String connectorId, String target) {
            return Optional.ofNullable(stored.get(key(connectorId, target))).map(String::toCharArray);
        }

        @Override
        public CredentialStatus status(String connectorId, String target) {
            return stored.containsKey(key(connectorId, target))
                    ? CredentialStatus.configuredAt(connectorId, target, Instant.parse("2026-07-29T10:00:00Z"))
                    : CredentialStatus.absent(connectorId, target);
        }

        @Override
        public void forget(String connectorId, String target) {
            stored.remove(key(connectorId, target));
        }
    }
}
