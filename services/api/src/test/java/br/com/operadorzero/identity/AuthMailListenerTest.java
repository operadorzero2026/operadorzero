package br.com.operadorzero.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuthMailListenerTest {
    @Test
    void verificationMailUsesConfiguredFrontendAndOneTimeToken() {
        AuthProperties properties = new AuthProperties(true, URI.create("https://app.example.test"), "Operador Zero",
            Duration.ofDays(7), Duration.ofMinutes(30), new AuthProperties.Cookie("OZ_SESSION", true, "None", ""),
            new AuthProperties.Mail(true, "no-reply@example.test"), new AuthProperties.Google(false, "", "", ""));
        AuthMailGateway gateway = mock(AuthMailGateway.class);
        AuthMailListener listener = new AuthMailListener(properties, gateway, new TokenSupport());

        listener.send(new AuthMailRequested("operator@example.test", "Operator", "VERIFY_EMAIL", "one-time-token"));

        ArgumentCaptor<AuthMailGateway.Message> message = ArgumentCaptor.forClass(AuthMailGateway.Message.class);
        verify(gateway).send(message.capture());
        assertThat(message.getValue().to()).isEqualTo("operator@example.test");
        assertThat(message.getValue().from()).isEqualTo("no-reply@example.test");
        assertThat(message.getValue().idempotencyKey()).matches("auth-verify-email-[a-f0-9]{64}");
        assertThat(message.getValue().text())
            .contains("https://app.example.test/?action=verify-email&token=one-time-token")
            .contains("funciona uma unica vez")
            .doesNotContain("password");
    }

    @Test
    void disabledMailDoesNotCallProvider() {
        AuthProperties properties = new AuthProperties(true, URI.create("https://app.example.test"), "Operador Zero",
            Duration.ofDays(7), Duration.ofMinutes(30), new AuthProperties.Cookie("OZ_SESSION", true, "None", ""),
            new AuthProperties.Mail(false, "no-reply@example.test"), new AuthProperties.Google(false, "", "", ""));
        AuthMailGateway gateway = mock(AuthMailGateway.class);

        new AuthMailListener(properties, gateway, new TokenSupport())
            .send(new AuthMailRequested("operator@example.test", "Operator", "VERIFY_EMAIL", "one-time-token"));

        verify(gateway, never()).send(org.mockito.ArgumentMatchers.any());
    }
}
