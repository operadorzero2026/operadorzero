package br.com.operadorzero.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisSessionRepository;

class RedisOAuthSessionConfigurationTest {
    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class, SessionAutoConfiguration.class))
        .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
        .withPropertyValues(
            "spring.session.timeout=10m",
            "spring.session.redis.namespace=operador-zero:oauth-session",
            "spring.session.redis.repository-type=default"
        );

    @Test
    void productionSessionUsesRedisRepository() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SessionRepository.class);
            assertThat(context.getBean(SessionRepository.class)).isInstanceOf(RedisSessionRepository.class);
        });
    }

    @Test
    void oauthAuthorizationRequestCanRoundTripThroughRedisSerializer() {
        OAuth2AuthorizationRequest request = OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
            .clientId("test-client")
            .redirectUri("https://api.example.test/login/oauth2/code/google")
            .state("random-state")
            .scopes(Set.of("openid"))
            .attributes(attributes -> attributes.put("code_verifier", "random-verifier"))
            .additionalParameters(parameters -> parameters.put("nonce", "random-nonce"))
            .build();
        JdkSerializationRedisSerializer serializer = new JdkSerializationRedisSerializer();

        Object restored = serializer.deserialize(serializer.serialize(request));

        assertThat(restored).isInstanceOf(OAuth2AuthorizationRequest.class);
        OAuth2AuthorizationRequest restoredRequest = (OAuth2AuthorizationRequest) restored;
        String restoredVerifier = restoredRequest.getAttribute("code_verifier");
        assertThat(restoredRequest.getState()).isEqualTo("random-state");
        assertThat(restoredVerifier).isEqualTo("random-verifier");
        assertThat(restoredRequest.getAdditionalParameters()).containsEntry("nonce", "random-nonce");
    }
}
