package br.com.operadorzero.payment;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciais do Mercado Pago (so no servidor, nunca no site). Sem o access token o Pix fica desligado e o organizador
 * continua podendo confirmar pagamentos manualmente.
 *
 * @param accessToken      MERCADOPAGO_ACCESS_TOKEN (credencial de producao ou de teste da conta que recebe)
 * @param webhookSecret    MERCADOPAGO_WEBHOOK_SECRET (assinatura secreta das notificacoes, em "Webhooks" no painel)
 * @param notificationUrl  endereco publico do webhook; vazio = FRONTEND_BASE_URL + /api/payments/mercadopago/webhook
 * @param apiBaseUrl       https://api.mercadopago.com (trocado so em teste local)
 * @param pixExpiration    validade do codigo Pix
 */
@ConfigurationProperties("app.payments.mercadopago")
public record MercadoPagoProperties(String accessToken, String webhookSecret, URI notificationUrl, URI apiBaseUrl,
                                    Duration pixExpiration) {
    public MercadoPagoProperties {
        if (apiBaseUrl == null) apiBaseUrl = URI.create("https://api.mercadopago.com");
        if (pixExpiration == null || pixExpiration.isNegative() || pixExpiration.isZero()) pixExpiration = Duration.ofMinutes(30);
    }

    public boolean enabled() {
        return accessToken != null && !accessToken.isBlank() && accessToken.strip().chars().noneMatch(Character::isWhitespace);
    }

    public boolean webhookSigned() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }
}
