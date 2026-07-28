package br.com.operadorzero.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TokenSupportTest {
    private final TokenSupport tokens = new TokenSupport();

    @Test
    void createsOpaqueUniqueTokensAndStableNonReversibleHashes() {
        String first = tokens.newToken();
        String second = tokens.newToken();
        assertThat(first).hasSizeGreaterThanOrEqualTo(40).isNotEqualTo(second);
        assertThat(tokens.hash(first)).hasSize(64).isEqualTo(tokens.hash(first)).doesNotContain(first);
    }

    @Test
    void keyedHashesCannotBeCorrelatedAcrossIndependentEnvironments() {
        TokenSupport firstEnvironment = new TokenSupport("first-environment-key-with-at-least-32-bytes");
        TokenSupport secondEnvironment = new TokenSupport("second-environment-key-with-at-least-32-bytes");

        assertThat(firstEnvironment.hash("same@example.com"))
            .isNotEqualTo(secondEnvironment.hash("same@example.com"));
    }

    @Test
    void enabledAuthenticationRequiresAStableServerSideHashKey() {
        AuthProperties properties = new AuthProperties(true, URI.create("https://app.example.test"), "Operador Zero",
            Duration.ofDays(7), Duration.ofMinutes(30), new AuthProperties.Cookie("OZ_SESSION", true, "None", ""),
            new AuthProperties.Mail(false, "no-reply@example.test"), new AuthProperties.Google(false, "", "", ""));

        assertThatThrownBy(() -> new TokenSupport(properties, ""))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("AUTH_HASH_KEY");
    }
}
