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
        boolean passwordChanged = "PASSWORD_CHANGED".equals(event.kind());
        String action = "VERIFY_EMAIL".equals(event.kind()) ? "verify-email" : "reset-password";
        String subject = switch (event.kind()) {
            case "VERIFY_EMAIL" -> "Confirme sua conta no Operador Zero";
            case "RESET_PASSWORD" -> "Recupere sua conta no Operador Zero";
            default -> "Sua senha do Operador Zero foi alterada";
        };
        String link = passwordChanged ? properties.frontendBaseUrl().toString() : properties.frontendBaseUrl().resolve("/?action=" + action + "&token="
            + URLEncoder.encode(event.token(), StandardCharsets.UTF_8)).toString();
        String instruction = passwordChanged
            ? "Sua senha foi alterada e as sessoes anteriores foram encerradas. Se voce nao realizou esta alteracao, solicite uma nova senha imediatamente."
            : "Use o link abaixo. Ele expira em " + properties.tokenDuration().toMinutes() + " minutos e funciona uma unica vez:";
        String text = "Ola, " + event.displayName() + ".\n\n" + instruction + "\n\n" + link
            + "\n\nSe voce nao solicitou esta acao, ignore esta mensagem.";
        String html = "<div style=\"background:#10120f;color:#f2f1e9;padding:32px;font-family:Arial,sans-serif\">"
            + "<h1 style=\"color:#c9cb73\">Operador Zero</h1><p>Ola, " + htmlEscape(event.displayName()) + ".</p>"
            + "<p>" + htmlEscape(instruction) + "</p><p><a href=\"" + link + "\" style=\"background:#c9cb73;color:#10120f;padding:12px 18px;text-decoration:none;font-weight:bold\">"
            + (passwordChanged ? "Acessar Operador Zero" : "Continuar com seguranca") + "</a></p>"
            + "<p style=\"font-size:12px;color:#b6b9ae;word-break:break-all\">" + link + "</p>"
            + "<p style=\"font-size:12px;color:#b6b9ae\">Se voce nao solicitou esta acao, ignore esta mensagem.</p></div>";
        String idempotencyKey = "auth-" + event.kind().toLowerCase(Locale.ROOT).replace('_', '-') + "-"
            + (passwordChanged ? tokens.hash(event.recipient() + ":" + event.kind()) : tokens.hash(event.token()));
        try {
            mailGateway.send(new AuthMailGateway.Message(properties.mail().from(), event.recipient(), subject, text, html,
                event.kind(), idempotencyKey));
        } catch (AuthMailDeliveryException exception) {
            log.error("Falha no envio de e-mail de autenticacao; recipientHash={}", tokens.hash(event.recipient()));
        }
    }

    private static String htmlEscape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
