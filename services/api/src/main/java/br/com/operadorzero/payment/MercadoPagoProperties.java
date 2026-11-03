package br.com.operadorzero.payment;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Configuracao do Mercado Pago (so no servidor, nunca no site).
 *
 * <p>Credenciais da PLATAFORMA (conta Operador Zero):
 * <ul>
 *   <li>accessToken (MERCADOPAGO_ACCESS_TOKEN): Pix direto da Etapa F, que cai na conta da plataforma e nao tem divisao.</li>
 *   <li>clientId / clientSecret (MERCADOPAGO_CLIENT_ID / MERCADOPAGO_CLIENT_SECRET): aplicacao do marketplace, usada no OAuth
 *       dos organizadores. Os tokens de cada organizador ficam cifrados no banco, nunca em variavel de ambiente.</li>
 *   <li>webhookSecret (MERCADOPAGO_WEBHOOK_SECRET): assinatura secreta das notificacoes da aplicacao.</li>
 * </ul>
 *
 * @param environment             "test" (contas e credenciais de teste) ou "production"
 * @param redirectUri             callback do OAuth; vazio = FRONTEND_BASE_URL + /api/payments/mercadopago/callback
 * @param authBaseUrl             https://auth.mercadopago.com (trocado so em teste local)
 * @param defaultCommissionPercent comissao padrao quando o administrador ainda nao gravou uma (PLATFORM_DEFAULT_COMMISSION_PERCENT)
 */
@ConfigurationProperties("app.payments.mercadopago")
public record MercadoPagoProperties(String accessToken, String webhookSecret, URI notificationUrl, URI apiBaseUrl,
                                    Duration pixExpiration, String clientId, String clientSecret, URI redirectUri,
                                    URI authBaseUrl, String environment, BigDecimal defaultCommissionPercent,
                                    Duration checkoutExpiration) {
    @ConstructorBinding
    public MercadoPagoProperties {
        if (apiBaseUrl == null) apiBaseUrl = URI.create("https://api.mercadopago.com");
        if (authBaseUrl == null) authBaseUrl = URI.create("https://auth.mercadopago.com");
        if (pixExpiration == null || pixExpiration.isNegative() || pixExpiration.isZero()) pixExpiration = Duration.ofMinutes(30);
        if (checkoutExpiration == null || checkoutExpiration.isNegative() || checkoutExpiration.isZero()) checkoutExpiration = Duration.ofMinutes(30);
        if (defaultCommissionPercent == null) defaultCommissionPercent = BigDecimal.valueOf(7);
        if (environment == null || environment.isBlank()) environment = "test";
    }

    /** Construtor da Etapa F (so Pix direto). */
    public MercadoPagoProperties(String accessToken, String webhookSecret, URI notificationUrl, URI apiBaseUrl, Duration pixExpiration) {
        this(accessToken, webhookSecret, notificationUrl, apiBaseUrl, pixExpiration, null, null, null, null, null, null, null);
    }

    /** Pix direto (Etapa F): conta da plataforma, sem divisao. */
    public boolean enabled() {
        return usable(accessToken);
    }

    /** Marketplace: aplicacao configurada para o OAuth dos organizadores. */
    public boolean marketplaceEnabled() {
        return usable(clientId) && usable(clientSecret);
    }

    /** Ha alguma integracao com o Mercado Pago (direta ou marketplace): o webhook pode ser aceito. */
    public boolean anyEnabled() {
        return enabled() || marketplaceEnabled();
    }

    public boolean testEnvironment() {
        return !"production".equalsIgnoreCase(environment.strip());
    }

    public boolean webhookSigned() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    private static boolean usable(String value) {
        return value != null && !value.isBlank() && value.strip().chars().noneMatch(Character::isWhitespace);
    }
}
