package br.com.operadorzero.identity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AuthMailListener {
    private static final Logger log = LoggerFactory.getLogger(AuthMailListener.class);

    private final AuthProperties properties;
    private final AuthMailGateway mailGateway;
    private final TokenSupport tokens;

    public AuthMailListener(AuthProperties properties, AuthMailGateway mailGateway, TokenSupport tokens) {
        this.properties = properties;
        this.mailGateway = mailGateway;
        this.tokens = tokens;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(AuthMailRequested event) {
        if (!properties.mail().enabled()) {
            log.warn("E-mail de autenticacao nao enviado porque MAIL_ENABLED=false; recipientHash={}", tokens.hash(event.recipient()));
            return;
        }
        String action = "VERIFY_EMAIL".equals(event.kind()) ? "verify-email" : "reset-password";
        String subject = "VERIFY_EMAIL".equals(event.kind()) ? "Confirme sua conta no Operador Zero" : "Recupere sua conta no Operador Zero";
        String link = properties.frontendBaseUrl().resolve("/?action=" + action + "&token="
            + URLEncoder.encode(event.token(), StandardCharsets.UTF_8)).toString();

        String text = "Ola, " + event.displayName() + ".\n\nUse o link abaixo. Ele expira em "
            + properties.tokenDuration().toMinutes() + " minutos e funciona uma unica vez:\n\n" + link
            + "\n\nSe voce nao solicitou esta acao, ignore esta mensagem.";
        String idempotencyKey = "auth-" + event.kind().toLowerCase(Locale.ROOT).replace('_', '-') + "-"
            + tokens.hash(event.token());
        try {
            mailGateway.send(new AuthMailGateway.Message(properties.mail().from(), event.recipient(), subject, text,
                event.kind(), idempotencyKey));
        } catch (AuthMailDeliveryException exception) {
            log.error("Falha no envio de e-mail de autenticacao; recipientHash={}", tokens.hash(event.recipient()));
        }
    }
}
