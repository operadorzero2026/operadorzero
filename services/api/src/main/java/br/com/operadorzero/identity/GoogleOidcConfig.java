package br.com.operadorzero.identity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

@Configuration
@ConditionalOnProperty(prefix = "app.auth.google", name = "enabled", havingValue = "true")
public class GoogleOidcConfig {
    @Bean
    ClientRegistrationRepository clientRegistrationRepository(AuthProperties properties) {
        AuthProperties.Google google = properties.google();
        if (google.clientId() == null || google.clientId().isBlank()
                || google.clientSecret() == null || google.clientSecret().isBlank()) {
            throw new IllegalStateException("Google OIDC habilitado sem client ID e client secret.");
        }
        ClientRegistration registration = ClientRegistration.withRegistrationId("google")
            .clientId(google.clientId())
            .clientSecret(google.clientSecret())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(google.redirectUri())
            .scope("openid", "profile", "email")
            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
            .tokenUri("https://oauth2.googleapis.com/token")
            .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
            .issuerUri("https://accounts.google.com")
            .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
            .userNameAttributeName(IdTokenClaimNames.SUB)
            .clientName("Google")
            .build();
        return new InMemoryClientRegistrationRepository(registration);
    }
}
