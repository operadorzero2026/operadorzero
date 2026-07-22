package br.com.operadorzero.identity;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthProperties(
    boolean enabled,
    URI frontendBaseUrl,
    String issuerName,
    Duration sessionDuration,
    Duration tokenDuration,
    Cookie cookie,
    Mail mail,
    Google google
) {
    public record Cookie(String name, boolean secure, String sameSite, String domain) {}
    public record Mail(boolean enabled, String from) {}
    public record Google(boolean enabled, String clientId, String clientSecret, String redirectUri) {}
}
