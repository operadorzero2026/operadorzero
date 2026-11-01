package br.com.operadorzero.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

class MfaServiceTest {
    private final IdentityRepository repository = mock(IdentityRepository.class);
    private final AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
    private final TokenSupport tokens = new TokenSupport();
    private final AuthProperties properties = new AuthProperties(true, URI.create("https://operadorzero.example"), "Operador Zero",
        Duration.ofDays(7), Duration.ofMinutes(30), new AuthProperties.Cookie("OZ_SESSION", true, "Lax", ""),
        new AuthProperties.Mail(true, "no-reply@example.test"));
    private final AuthCookieService cookies = new AuthCookieService(properties);
    private final AuthenticatedUser admin = new AuthenticatedUser(1L, UUID.randomUUID(), "admin@example.test", "admin",
        "Admin", "ADMIN", List.of("USER"));
    private MfaService service;

    @BeforeEach
    void setup() {
        doNothing().when(rateLimiter).check(anyString(), any(), anyString(), anyInt(), any());
        AuthService auth = new AuthService(properties, repository, new Argon2PasswordEncoder(16, 32, 1, 19456, 2),
            new PasswordPolicy(), tokens, rateLimiter, cookies, mock(ApplicationEventPublisher.class));
        service = new MfaService(repository, tokens, cookies, rateLimiter, auth, properties);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void totpMatchesRfc6238ReferenceVector() {
        byte[] secret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        // RFC 6238, T = 59 s, SHA1: 94287082 -> 6 digitos: 287082.
        assertThat(Totp.code(secret, 59 / 30)).isEqualTo("287082");
        assertThat(Totp.verify(secret, "287082", Instant.ofEpochSecond(59))).hasValue(1);
        assertThat(Totp.verify(secret, "287083", Instant.ofEpochSecond(59))).isEmpty();
        assertThat(Totp.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII)))
            .isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
    }

    @Test
    void setupStoresOnlyEncryptedSecretAndReturnsAuthenticatorUri() {
        when(repository.findMfa(1L)).thenReturn(Optional.empty());
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);

        MfaService.Setup setup = service.setup(admin);

        verify(repository).savePendingMfa(eq(1L), stored.capture(), any());
        assertThat(stored.getValue()).doesNotContain(setup.secret());
        assertThat(Totp.base32(service.decrypt(stored.getValue()))).isEqualTo(setup.secret());
        assertThat(setup.otpauthUri()).startsWith("otpauth://totp/Operador%20Zero%3Aadmin%40example.test?secret=")
            .contains("issuer=Operador%20Zero", "digits=6", "period=30");
    }

    @Test
    void enablingWithCurrentCodeVerifiesTheCurrentSession() {
        byte[] secret = Totp.newSecret();
        when(repository.findMfa(1L)).thenReturn(Optional.of(new IdentityRepository.MfaRow(service.encrypt(secret), false, 0)));
        when(repository.enableMfa(eq(1L), anyLong(), any())).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("OZ_SESSION", "raw-session"));

        service.enable(admin, Totp.code(secret, Instant.now().getEpochSecond() / 30), request);

        verify(repository).markSessionMfaVerified(eq(tokens.hash("raw-session")), any());
    }

    @Test
    void wrongOrReusedCodeIsRejected() {
        byte[] secret = Totp.newSecret();
        long step = Instant.now().getEpochSecond() / 30;
        when(repository.findMfa(1L)).thenReturn(Optional.of(new IdentityRepository.MfaRow(service.encrypt(secret), true, step + 1)));

        assertThatThrownBy(() -> service.verify(admin, "000000".equals(Totp.code(secret, step)) ? "111111" : "000000", new MockHttpServletRequest()))
            .isInstanceOf(AuthException.class).extracting("code").isEqualTo("INVALID_MFA_CODE");
        assertThatThrownBy(() -> service.verify(admin, Totp.code(secret, step), new MockHttpServletRequest()))
            .isInstanceOf(AuthException.class).extracting("code").isEqualTo("INVALID_MFA_CODE");
        verify(repository, never()).markSessionMfaVerified(anyString(), any());
    }

    @Test
    void privilegedRolesAreDroppedFromSessionsWithoutSecondFactor() throws Exception {
        UserAccount moderator = new UserAccount(1L, UUID.randomUUID(), "m@example.test", "m", "hash", "ACTIVE", "M", "M",
            List.of("MODERATOR", "USER"));
        when(repository.findBySessionHash(eq(tokens.hash("raw-session")), any())).thenReturn(Optional.of(moderator));
        when(repository.sessionMfaVerified(tokens.hash("raw-session"))).thenReturn(false, true);
        SessionAuthenticationFilter filter = new SessionAuthenticationFilter(properties, cookies, tokens, repository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("OZ_SESSION", "raw-session"));

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        assertThat(((AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).roles()).containsExactly("USER");

        SecurityContextHolder.clearContext();
        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        assertThat(((AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).roles())
            .containsExactly("MODERATOR", "USER");
    }
}
