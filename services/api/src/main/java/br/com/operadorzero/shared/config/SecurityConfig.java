package br.com.operadorzero.shared.config;

import br.com.operadorzero.identity.AuthProperties;
import br.com.operadorzero.identity.GoogleAuthSuccessHandler;
import br.com.operadorzero.identity.SessionAuthenticationFilter;
import br.com.operadorzero.shared.web.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {
    private final List<String> allowedOrigins;
    private final AuthProperties authProperties;
    private final SessionAuthenticationFilter sessionAuthenticationFilter;
    private final ClientRegistrationRepository clientRegistrations;
    private final GoogleAuthSuccessHandler googleHandler;
    private final ObjectMapper objectMapper;

    public SecurityConfig(@Value("${app.security.cors.allowed-origins}") String allowedOrigins,
                          AuthProperties authProperties,
                          SessionAuthenticationFilter sessionAuthenticationFilter,
                          ObjectProvider<ClientRegistrationRepository> clientRegistrations,
                          GoogleAuthSuccessHandler googleHandler,
                          ObjectMapper objectMapper) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isBlank())
            .toList();
        if (this.allowedOrigins.isEmpty() || this.allowedOrigins.contains("*")) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS deve conter origens explicitas.");
        }
        this.authProperties = authProperties;
        this.sessionAuthenticationFilter = sessionAuthenticationFilter;
        this.clientRegistrations = clientRegistrations.getIfAvailable();
        this.googleHandler = googleHandler;
        this.objectMapper = objectMapper;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/auth/csrf", "/api/auth/register", "/api/auth/verify-email",
                    "/api/auth/login", "/api/auth/password-recovery", "/api/auth/password-reset",
                    "/api/auth/resend-verification", "/api/auth/google/intent",
                    "/api/auth/session",
                    "/oauth2/**", "/login/oauth2/**").permitAll()
                .requestMatchers("/api/auth/logout").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/community/**").permitAll()
                .requestMatchers("/api/admin/community/**").hasAnyRole("ADMIN", "MODERATOR")
                .requestMatchers("/api/community/**").authenticated()
                .requestMatchers("/api/operators/**", "/api/teams/**", "/api/operations/**",
                    "/api/fields/**", "/api/maps/**", "/api/rankings/**", "/api/performance/**").authenticated()
                .anyRequest().denyAll())
            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable())
            .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                    writeError(response, 401, "AUTHENTICATION_REQUIRED", "Entre para continuar."))
                .accessDeniedHandler((request, response, exception) ->
                    writeError(response, 403, "ACCESS_DENIED", "Acesso negado.")))
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepository())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .addFilterBefore(sessionAuthenticationFilter, AnonymousAuthenticationFilter.class);

        if (clientRegistrations != null) {
            DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(clientRegistrations, "/oauth2/authorization");
            resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
            http.oauth2Login(oauth -> oauth
                .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(
                    new GatedOAuth2AuthorizationRequestResolver(resolver)))
                .successHandler(googleHandler)
                .failureHandler(googleHandler));
        }
        return http.build();
    }

    private CookieCsrfTokenRepository csrfRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName("OZ_CSRF");
        repository.setHeaderName("X-CSRF-TOKEN");
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> {
            cookie.secure(authProperties.cookie().secure()).sameSite(authProperties.cookie().sameSite());
            if (authProperties.cookie().domain() != null && !authProperties.cookie().domain().isBlank()) {
                cookie.domain(authProperties.cookie().domain());
            }
        });
        return repository;
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
            new ApiError(Instant.now(), status, code, message, MDC.get("correlationId"), List.of()));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Accept", "Content-Type", "X-CSRF-TOKEN", "X-Correlation-ID"));
        configuration.setExposedHeaders(List.of("X-Correlation-ID"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
