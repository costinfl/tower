package dev.tower.connector.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("A Connector credential")
class ConnectorCredentialTest {

    @Test
    void carries_a_bearer_token_for_the_connector_to_present() {
        var credential = ConnectorCredential.bearerToken("sha256~a-token".toCharArray());

        assertThat(credential.isPresent()).isTrue();
        assertThat(credential.token()).containsExactly("sha256~a-token".toCharArray());
    }

    @Test
    void copies_the_token_so_clearing_the_callers_array_does_not_empty_it_mid_request() {
        char[] supplied = "sha256~a-token".toCharArray();
        var credential = ConnectorCredential.bearerToken(supplied);

        java.util.Arrays.fill(supplied, '\0');

        assertThat(credential.token()).containsExactly("sha256~a-token".toCharArray());
    }

    @Test
    void hands_out_copies_so_a_caller_clearing_what_it_received_does_not_disarm_the_credential() {
        var credential = ConnectorCredential.bearerToken("token".toCharArray());

        java.util.Arrays.fill(credential.token(), '\0');

        assertThat(credential.token()).containsExactly("token".toCharArray());
    }

    @Test
    void can_be_cleared_deliberately() {
        var credential = ConnectorCredential.bearerToken("token".toCharArray());

        credential.clear();

        assertThat(credential.isPresent()).isFalse();
    }

    @Test
    void refuses_to_hand_out_a_cleared_token_rather_than_returning_nul_bytes() {
        // Returning the zeroed array would let a Connector present a row of NUL
        // bytes as a bearer token, and the cluster would answer 401 — reporting an
        // authentication failure instead of the programming error behind it.
        var credential = ConnectorCredential.bearerToken("token".toCharArray());
        credential.clear();

        assertThatThrownBy(credential::token)
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("cleared");
    }

    @Test
    void distinguishes_no_credential_needed_from_a_missing_one() {
        assertThat(ConnectorCredential.none().isPresent()).isFalse();
    }

    @Test
    void rejects_an_empty_bearer_token_rather_than_presenting_nothing() {
        assertThatThrownBy(() -> ConnectorCredential.bearerToken(new char[0]))
                .isInstanceOf(ConnectorException.class);
        assertThatThrownBy(() -> ConnectorCredential.bearerToken(null))
                .isInstanceOf(ConnectorException.class);
    }

    @Test
    void never_renders_the_token_when_printed() {
        var credential = ConnectorCredential.bearerToken("sensitive-token".toCharArray());

        assertThat(credential.toString())
                .doesNotContain("sensitive-token")
                .isEqualTo("ConnectorCredential[present]");
    }
}
