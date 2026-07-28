package br.com.operadorzero.shared.config;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.operadorzero.identity.AuthController;
import br.com.operadorzero.identity.AuthProperties;
import br.com.operadorzero.identity.AuthService;
import br.com.operadorzero.identity.GoogleAuthSuccessHandler;
import br.com.operadorzero.identity.GoogleAuthorizationRequestGate;
import br.com.operadorzero.identity.GoogleOidcConfig;
import br.com.operadorzero.identity.SessionAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = AuthController.class,
    properties = {
        "app.auth.enabled=true",
        "app.auth.frontend-base-url=https://operadorzero.vercel.app",
        "app.auth.issuer-name=Operador Zero",
        "app.auth.hash-key=google-oauth-security-route-test-key-32-bytes",
        "app.auth.session-duration=7d",
        "app.auth.token-duration=30m",
        "app.auth.cookie.name=OZ_SESSION",
        "app.auth.cookie.secure=true",
        "app.auth.cookie.same-site=None",
        "app.auth.cookie.domain=",
        "app.auth.google.enabled=true",
        "app.auth.google.client-id=test-client.apps.googleusercontent.com",
        "app.auth.google.client-secret=test-client-secret",
        "app.auth.google.redirect-uri=https://api.example.test/login/oauth2/code/google",
        "app.security.cors.allowed-origins=https://operadorzero.vercel.app"
    }
)
@Import({SecurityConfig.class, GoogleOidcConfig.class, GoogleOAuthSecurityRouteTest.FilterConfiguration.class})
@EnableConfigurationProperties(AuthProperties.class)
class GoogleOAuthSecurityRouteTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private GoogleAuthSuccessHandler googleAuthSuccessHandler;

    @Test
    void directGoogleAuthorizationRouteDoesNotAllocateAnOAuthRequest() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
            .andExpect(status().isNotFound());
    }

    @Test
    void preparedGoogleAuthorizationRouteRedirectsOnceWithOidcAndPkce() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest preparation = new MockHttpServletRequest();
        preparation.setSession(session);
        GoogleAuthorizationRequestGate.allowNext(preparation);

        mockMvc.perform(get("/oauth2/authorization/google").session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", allOf(
                startsWith("https://accounts.google.com/o/oauth2/v2/auth?"),
                containsString("code_challenge="),
                containsString("nonce="))));

        mockMvc.perform(get("/oauth2/authorization/google").session(session))
            .andExpect(status().isNotFound());
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
