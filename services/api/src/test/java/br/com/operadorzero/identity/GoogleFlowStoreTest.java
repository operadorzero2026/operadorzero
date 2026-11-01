package br.com.operadorzero.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.operadorzero.identity.GoogleFlowStore.Flow;
import br.com.operadorzero.identity.GoogleFlowStore.LinkIntent;
import br.com.operadorzero.identity.GoogleFlowStore.Mode;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class GoogleFlowStoreTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final TokenSupport tokens = new TokenSupport();
    private GoogleFlowStore store;

    @BeforeEach
    void setup() {
        when(redis.opsForValue()).thenReturn(values);
        store = new GoogleFlowStore(redis, tokens);
    }

    @Test
    void authorizationRequestSurvivesRedisRoundTripWithStateNonceAndVerifier() {
        OAuth2AuthorizationRequest request = OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
            .clientId("client")
            .redirectUri("https://operadorzero.example/login/oauth2/code/google")
            .state("state-123")
            .attributes(Map.of("registration_id", "google", "nonce", "raw-nonce", "code_verifier", "verifier"))
            .additionalParameters(Map.of("nonce", "hashed-nonce", "code_challenge", "challenge", "code_challenge_method", "S256"))
            .build();
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);

        store.saveAuthorizationRequest("raw-flow", request);
        verify(values).set(eq("operador-zero:google:authz:" + tokens.hash("raw-flow")), stored.capture(), eq(Duration.ofMinutes(10)));
        when(values.getAndDelete("operador-zero:google:authz:" + tokens.hash("raw-flow"))).thenReturn(stored.getValue());

        OAuth2AuthorizationRequest restored = store.takeAuthorizationRequest("raw-flow").orElseThrow();
        assertThat(restored.getState()).isEqualTo("state-123");
        assertThat((String) restored.getAttribute("nonce")).isEqualTo("raw-nonce");
        assertThat((String) restored.getAttribute("code_verifier")).isEqualTo("verifier");
        assertThat(restored.getRedirectUri()).isEqualTo("https://operadorzero.example/login/oauth2/code/google");
    }

    @Test
    void foreignSerializedObjectsAreRejected() {
        when(values.get(anyString())).thenReturn(Base64.getEncoder().encodeToString(serialized(java.net.URI.create("https://evil.example"))));

        assertThat(store.loadAuthorizationRequest("raw-flow")).isEmpty();
    }

    @Test
    void flowAndLinkIntentAreKeyedByHashAndReadOnce() {
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        String raw = store.startFlow(new Flow(Mode.LINK, false, 42L));
        verify(values, org.mockito.Mockito.times(2)).set(key.capture(), value.capture(), any(Duration.class));
        assertThat(key.getAllValues()).allMatch(k -> k.endsWith(tokens.hash(raw)) && !k.contains(raw));

        when(values.getAndDelete("operador-zero:google:flow:" + tokens.hash(raw))).thenReturn(value.getAllValues().get(0));
        assertThat(store.takeFlow(raw)).contains(new Flow(Mode.LINK, false, 42L));

        String linkRaw = store.saveLinkIntent(new LinkIntent(7L, "https://accounts.google.com", "sub|with|pipes", "a@b.c"));
        ArgumentCaptor<String> linkValue = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("operador-zero:google:link:" + tokens.hash(linkRaw)), linkValue.capture(), eq(Duration.ofMinutes(15)));
        when(values.getAndDelete("operador-zero:google:link:" + tokens.hash(linkRaw))).thenReturn(linkValue.getValue());
        assertThat(store.takeLinkIntent(linkRaw)).contains(new LinkIntent(7L, "https://accounts.google.com", "sub|with|pipes", "a@b.c"));
    }

    private static byte[] serialized(Object value) {
        try (java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
             java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(bytes)) {
            out.writeObject(value);
            out.flush();
            return bytes.toByteArray();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
