package br.com.operadorzero.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

class ResendMailHealthIndicatorTest {
    @Test
    void healthIsDownWhenMailIsEnabledWithoutApiKey() {
        ResendMailHealthIndicator indicator = new ResendMailHealthIndicator(properties(true), new ResendProperties(""));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void healthIsUpWhenProviderIsConfigured() {
        ResendMailHealthIndicator indicator = new ResendMailHealthIndicator(properties(true),
            new ResendProperties("re_test_key"));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void disabledMailDoesNotMarkApplicationDown() {
        ResendMailHealthIndicator indicator = new ResendMailHealthIndicator(properties(false),
            new ResendProperties(""));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    private AuthProperties properties(boolean mailEnabled) {
        return new AuthProperties(true, URI.create("https://app.example.test"), "Operador Zero",
            Duration.ofDays(7), Duration.ofMinutes(30), new AuthProperties.Cookie("OZ_SESSION", true, "None", ""),
            new AuthProperties.Mail(mailEnabled, "Operador Zero <acesso@example.test>"),
            new AuthProperties.Google(false, "", "", ""));
    }
}
