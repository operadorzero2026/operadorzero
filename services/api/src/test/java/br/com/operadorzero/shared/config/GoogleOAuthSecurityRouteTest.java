package br.com.operadorzero.shared.config;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.operadorzero.identity.AuthController;
import br.com.operadorzero.identity.AuthCookieService;
import br.com.operadorzero.identity.CsrfRotation;
import br.com.operadorzero.identity.AuthProperties;
import br.com.operadorzero.identity.AuthService;
import br.com.operadorzero.identity.GoogleAuthHandler;
import br.com.operadorzero.identity.GoogleAuthProperties;
import br.com.operadorzero.identity.GoogleAuthService;
import br.com.operadorzero.identity.GoogleAuthorizationFallbackController;
import br.com.operadorzero.identity.GoogleAuthorizationRequestRepository;
import br.com.operadorzero.identity.GoogleFlowStore;
import br.com.operadorzero.identity.GoogleOidcConfig;
import br.com.operadorzero.identity.SessionAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = {AuthController.class, GoogleAuthorizationFallbackController.class},
    properties = {
        "app.auth.enabled=true",
        "app.auth.frontend-base-url=https://operadorzero.example",
        "app.auth.issuer-name=Operador Zero",
        "app.auth.hash-key=google-oauth-security-route-test-key-32-bytes",
        "app.auth.session-duration=7d",
        "app.auth.token-duration=30m",
        "app.auth.cookie.name=OZ_SESSION",
        "app.auth.cookie.secure=true",
        "app.auth.cookie.same-site=Lax",
        "app.auth.cookie.domain=",
        "app.auth.google.enabled=true",
        "app.auth.google.client-id=test-client.apps.googleusercontent.com",
        "app.auth.google.client-secret=test-client-secret",
        "app.auth.google.redirect-uri=https://operadorzero.example/login/oauth2/code/google",
        "app.security.cors.allowed-origins=https://operadorzero.example"
    }
)
// A configuracao aninhada substitui a busca pela aplicacao; por isso os controllers sao importados explicitamente.
@Import({SecurityConfig.class, CsrfRotation.class, GoogleOidcConfig.class, AuthCookieService.class, GoogleAuthorizationRequestRepository.class,
    AuthController.class, GoogleAuthorizationFallbackController.class, GoogleOAuthSecurityRouteTest.FilterConfiguration.class})
@EnableConfigurationProperties({AuthProperties.class, GoogleAuthProperties.class})
class GoogleOAuthSecurityRouteTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private GoogleAuthService googleAuthService;

    @MockitoBean
    private GoogleAuthHandler googleAuthHandler;

    @MockitoBean
    private GoogleFlowStore store;

    @MockitoBean
    private br.com.operadorzero.identity.MfaService mfaService;

    @Test
    void directGoogleAuthorizationRouteNeverReachesGoogle() throws Exception {
        when(googleAuthHandler.expiredRedirect()).thenReturn("https://operadorzero.example/?auth=google-error&code=GOOGLE_FLOW_EXPIRED");

        mockMvc.perform(get("/oauth2/authorization/google"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("https://operadorzero.example/?auth=google-error&code=GOOGLE_FLOW_EXPIRED"));
    }

    @Test
    void releasedFlowRedirectsOnceToGoogleWithOidcNonceAndPkce() throws Exception {
        when(store.consumeGate("raw-flow")).thenReturn(true, false);
        when(googleAuthHandler.expiredRedirect()).thenReturn("https://operadorzero.example/?auth=google-error&code=GOOGLE_FLOW_EXPIRED");

        mockMvc.perform(get("/oauth2/authorization/google").cookie(new Cookie("OZ_GOOGLE_FLOW", "raw-flow")))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", allOf(
                startsWith("https://accounts.google.com/o/oauth2/v2/auth?"),
                containsString("scope=openid%20profile%20email"),
                containsString("code_challenge_method=S256"),
                containsString("state="),
                containsString("nonce="),
                containsString("redirect_uri=https://operadorzero.example/login/oauth2/code/google"))));
        verify(store).saveAuthorizationRequest(eq("raw-flow"), any(OAuth2AuthorizationRequest.class));

        mockMvc.perform(get("/oauth2/authorization/google").cookie(new Cookie("OZ_GOOGLE_FLOW", "raw-flow")))
            .andExpect(redirectedUrl("https://operadorzero.example/?auth=google-error&code=GOOGLE_FLOW_EXPIRED"));
    }

    @Test
    void startRequiresCsrf() throws Exception {
        mockMvc.perform(post("/api/auth/google/start").contentType("application/json").content("{\"mode\":\"login\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void callbackWithoutStoredRequestIsRejectedByTheFailureHandler() throws Exception {
        mockMvc.perform(get("/login/oauth2/code/google").param("code", "fake").param("state", "forged"))
            .andExpect(status().isOk());
        verify(googleAuthHandler).onAuthenticationFailure(any(), any(), any());
    }

    @Test
    void unlinkRequiresSession() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/auth/google/link")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
            .andExpect(status().isUnauthorized());
    }

    @Configuration(proxyBeanMethods = false)
    static class FilterConfiguration {
        @Bean
        SessionAuthenticationFilter sessionAuthenticationFilter() {
            return new SessionAuthenticationFilter(null, null, null, null) {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                FilterChain filterChain) throws ServletException, IOException {
                    filterChain.doFilter(request, response);
                }
            };
        }
    }
}
