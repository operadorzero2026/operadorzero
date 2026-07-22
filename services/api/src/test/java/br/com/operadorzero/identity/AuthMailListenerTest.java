package br.com.operadorzero.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class AuthMailListenerTest {
    @Test
    void verificationMailUsesConfiguredFrontendAndOneTimeToken() {
        AuthProperties properties = new AuthProperties(true, URI.create("https://app.example.test"), "Operador Zero",
            Duration.ofDays(7), Duration.ofMinutes(30), new AuthProperties.Cookie("OZ_SESSION", true, "None", ""),
            new AuthProperties.Mail(true, "no-reply@example.test"), new AuthProperties.Google(false, "", "", ""));
        JavaMailSender sender = mock(JavaMailSender.class);
        AuthMailListener listener = new AuthMailListener(properties, sender, new TokenSupport());

        listener.send(new AuthMailRequested("operator@example.test", "Operator", "VERIFY_EMAIL", "one-time-token"));

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("operator@example.test");
        assertThat(message.getValue().getFrom()).isEqualTo("no-reply@example.test");
        assertThat(message.getValue().getText())
            .contains("https://app.example.test/?action=verify-email&token=one-time-token")
            .contains("funciona uma unica vez")
            .doesNotContain("password");
    }
}
