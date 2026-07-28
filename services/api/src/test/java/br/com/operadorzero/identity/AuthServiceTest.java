package br.com.operadorzero.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

class AuthServiceTest {
    private final IdentityRepository repository = mock(IdentityRepository.class);
    private final AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final TokenSupport tokens = new TokenSupport();
    private final AuthProperties properties = new AuthProperties(true, URI.create("http://localhost:4174"), "Operador Zero",
        Duration.ofDays(7), Duration.ofMinutes(30), new AuthProperties.Cookie("OZ_SESSION", false, "Lax", ""),
        new AuthProperties.Mail(true, "no-reply@example.test"), new AuthProperties.Google(false, "", "", ""));
    private final AuthCookieService cookies = new AuthCookieService(properties);
    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    private AuthService service;

    @BeforeEach
    void setup() {
        doNothing().when(rateLimiter).check(anyString(), any(), anyString(), anyInt(), any());
        service = new AuthService(properties, repository, encoder, new PasswordPolicy(), tokens, rateLimiter, cookies, events);
    }

    @Test
    void registrationStoresOnlyHashAndPublishesRawTokenAfterCreatingPendingAccount() {
        UserAccount account = account("PENDING_EMAIL", null);
        when(repository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(repository.usernameExists(anyString())).thenReturn(false);
        when(repository.createUser(eq("new@example.com"), anyString(), anyString(), eq("PENDING_EMAIL"),
            eq("New Operator"), eq("NEW OPERATOR"), anyString(), anyString(), any(Instant.class))).thenReturn(7L);
        when(repository.findById(7L)).thenReturn(Optional.of(account));

        service.register("New Operator", "NEW@example.com", "CampoSeguro2026!", true, request());

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repository).saveAuthToken(eq(account.id()), eq("VERIFY_EMAIL"), hash.capture(), any(), any());
        ArgumentCaptor<AuthMailRequested> event = ArgumentCaptor.forClass(AuthMailRequested.class);
        verify(events).publishEvent(event.capture());
        assertThat(hash.getValue()).hasSize(64).isNotEqualTo(event.getValue().token());
        assertThat(event.getValue().recipient()).isEqualTo("new@example.com");
    }

    @Test
    void loginCreatesOpaqueHttpOnlyCookieAndPersistsOnlySessionHash() {
        String password = "CampoSeguro2026!";
        UserAccount account = account("ACTIVE", encoder.encode(password));
        when(repository.findByEmail(account.email())).thenReturn(Optional.of(account));
        MockHttpServletResponse response = new MockHttpServletResponse();

        UserAccount result = service.login(account.email(), password, request(), response);

        assertThat(result.publicId()).isEqualTo(account.publicId());
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repository).saveSession(eq(account.id()), hash.capture(), any(), anyString(), any(), any());
        assertThat(hash.getValue()).hasSize(64);
        assertThat(response.getHeader("Set-Cookie")).contains("OZ_SESSION=", "HttpOnly", "SameSite=Lax");
    }

    @Test
    void loginWithUnknownEmailStillPerformsArgon2Verification() {
        when(repository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        long startedAt = System.nanoTime();
        assertThatThrownBy(() -> service.login("missing@example.com", "CampoSeguro2026!", request(), new MockHttpServletResponse()))
            .isInstanceOf(AuthException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isGreaterThan(Duration.ofMillis(5));
    }

    @Test
    void registrationForExistingAccountStillPerformsPasswordValidationAndArgon2Hashing() {
        when(repository.findByEmail("new@example.com")).thenReturn(Optional.of(account("ACTIVE", encoder.encode("OutraSenha2026!"))));

        long startedAt = System.nanoTime();
        service.register("New Operator", "new@example.com", "CampoSeguro2026!", true, request());

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isGreaterThan(Duration.ofMillis(5));
        verify(repository, never()).createUser(anyString(), anyString(), anyString(), anyString(), anyString(),
            anyString(), anyString(), anyString(), any(Instant.class));
    }

    @Test
    void passwordRecoveryForUnknownEmailStillPerformsArgon2Work() {
        when(repository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        long startedAt = System.nanoTime();
        service.requestPasswordRecovery("missing@example.com", request());

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isGreaterThan(Duration.ofMillis(5));
        verify(repository, never()).saveAuthToken(anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    void verificationResendForUnknownEmailStillPerformsArgon2Work() {
        when(repository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        long startedAt = System.nanoTime();
        service.resendVerification("missing@example.com", request());

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isGreaterThan(Duration.ofMillis(5));
        verify(repository, never()).saveAuthToken(anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    void googlePreparationCreatesOneTimeLaunchGateAndCleansStaleRegistrationIntents() {
        AuthProperties googleProperties = new AuthProperties(true, URI.create("http://localhost:4174"), "Operador Zero",
            Duration.ofDays(7), Duration.ofMinutes(30), new AuthProperties.Cookie("OZ_SESSION", false, "Lax", ""),
            new AuthProperties.Mail(true, "no-reply@example.test"), new AuthProperties.Google(true, "client", "secret", "callback"));
        AuthService googleService = new AuthService(googleProperties, repository, encoder, new PasswordPolicy(), tokens,
            rateLimiter, new AuthCookieService(googleProperties), events);
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(googleService.prepareGoogle(true, request, response)).isEqualTo("/oauth2/authorization/google");

        verify(repository).deleteStaleGoogleIntents(any(Instant.class));
        verify(repository).saveGoogleIntent(anyString(), eq(AuthService.TERMS_VERSION), eq(AuthService.PRIVACY_VERSION),
            any(Instant.class), any(Instant.class));
        assertThat(GoogleAuthorizationRequestGate.consume(request)).isTrue();
        assertThat(GoogleAuthorizationRequestGate.consume(request)).isFalse();
    }

    @Test
    void emailVerificationConsumesHashedOneTimeTokenAndActivatesAccount() {
        String rawToken = tokens.newToken();
        UserAccount account = account("ACTIVE", encoder.encode("CampoSeguro2026!"));
        when(repository.consumeAuthToken(eq(tokens.hash(rawToken)), eq("VERIFY_EMAIL"), any(Instant.class)))
            .thenReturn(Optional.of(account.id()));
        when(repository.findById(account.id())).thenReturn(Optional.of(account));

        service.verifyEmail(rawToken, request());

        verify(repository).activateUser(eq(account.id()), any(Instant.class));
        verify(repository).recordAudit(eq(account.id()), eq("AUTH_EMAIL_VERIFIED"), eq("APP_USER"),
            eq(account.publicId()), anyString(), eq(null), anyString(), any(Instant.class));
    }

    @Test
    void passwordResetConsumesOneTimeTokenAndRevokesExistingSessions() {
        String rawToken = tokens.newToken();
        UserAccount account = account("ACTIVE", encoder.encode("CampoSeguro2026!"));
        when(repository.consumeAuthToken(eq(tokens.hash(rawToken)), eq("RESET_PASSWORD"), any(Instant.class)))
            .thenReturn(Optional.of(account.id()));
        when(repository.findById(account.id())).thenReturn(Optional.of(account));

        service.resetPassword(rawToken, "SenhaNovaSegura2026!", request());

        ArgumentCaptor<String> passwordHash = ArgumentCaptor.forClass(String.class);
        verify(repository).updatePasswordAndRevokeSessions(eq(account.id()), passwordHash.capture(), any(Instant.class));
        assertThat(passwordHash.getValue()).startsWith("$argon2id$").doesNotContain("SenhaNovaSegura2026!");
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "auth-test");
        return request;
    }

    private UserAccount account(String status, String passwordHash) {
        return new UserAccount(7L, UUID.fromString("40c93ca5-5fe6-4e57-962a-04e6e92ffad9"), "new@example.com",
            "new.operator", passwordHash, status, "New Operator", "NEW OPERATOR", List.of("USER"));
    }
}
