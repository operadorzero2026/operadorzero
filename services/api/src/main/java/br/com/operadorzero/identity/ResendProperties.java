package br.com.operadorzero.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth.mail.resend")
public record ResendProperties(String apiKey) {
    boolean configured() {
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        return apiKey.strip().chars().noneMatch(Character::isWhitespace);
    }

    String apiKeyValue() {
        if (!configured()) {
            throw new AuthMailDeliveryException("Provedor de e-mail nao configurado");
        }
        return apiKey.strip();
    }
}
