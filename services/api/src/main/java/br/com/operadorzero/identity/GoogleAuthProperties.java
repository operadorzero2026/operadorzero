package br.com.operadorzero.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth.google")
public record GoogleAuthProperties(boolean enabled, String clientId, String clientSecret, String redirectUri) {}
