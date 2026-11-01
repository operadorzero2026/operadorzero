package br.com.operadorzero.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.operadorzero.identity.GoogleAuthService.Outcome;
import br.com.operadorzero.identity.GoogleFlowStore.Flow;
import br.com.operadorzero.identity.GoogleFlowStore.LinkIntent;
import br.com.operadorzero.identity.GoogleFlowStore.Mode;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

class GoogleAuthServiceTest {
    private static final String ISSUER = "https://accounts.google.com";
    private static final String SUBJECT = "109876543210987654321";

    private final IdentityRepository repository = mock(IdentityRepository.class);
    private final AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
    private final GoogleFlowStore store = mock(GoogleFlowStore.class);
    private final TokenSupport tokens = new TokenSupport();
    private final AuthProperties properties = new AuthProperties(true, URI.create("https://operadorzero.example"), "Operador Zero",
        Duration.ofDays(7), Duration.ofMinutes(30), new AuthProperties.Cookie("OZ_SESSION", true, "Lax", ""),
        new AuthProperties.Mail(true, "no-reply@example.test"));
    private final AuthCookieService cookies = new AuthCookieService(properties);
    private GoogleAuthService service;

    @BeforeEach
    void setup() {
        doNothing().when(rateLimiter).check(anyString(), any(), anyString(), anyInt(), any());
        AuthService auth = new AuthService(properties, repository, new Argon2PasswordEncoder(16, 32, 1, 19456, 2),
            new PasswordPolicy(), tokens, rateLimiter, cookies, mock(ApplicationEventPublisher.class));
        service = new GoogleAuthService(auth, new GoogleAuthProperties(true, "client", "secret", "https://x/cb"),
            repository, store, cookies, rateLimiter, tokens);
    }

    @Test
    void linkedGoogleIdentityStartsSession() {
        UserAccount account = account(10L, "op@example.com", "hash");
        when(repository.findByOidc(ISSUER, SUBJECT)).thenReturn(Optional.of(account));
        MockHttpServletResponse response = new MockHttpServletResponse();

        Outcome outcome = complete(Mode.LOGIN, false, "op@example.com", true, new MockHttpServletRequest(), response);

        assertThat(outcome.status()).isEqualTo("success");
        verify(repository).saveSession(eq(10L), anyString(), any(), anyString(), any(), any());
        assertThat(response.getHeaders("Set-Cookie")).anyMatch(value -> value.startsWith("OZ_SESSION=") && value.contains("HttpOnly"));
    }

    @Test
    void sameEmailNeverLinksAutomaticallyAndRequiresPasswordProof() {
        UserAccount existing = account(20L, "op@example.com", "hash");
        when(repository.findByOidc(ISSUER, SUBJECT)).thenReturn(Optional.empty());
        when(repository.findByEmail("op@example.com")).thenReturn(Optional.of(existing));
        when(store.saveLinkIntent(any())).thenReturn("raw-link-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Outcome outcome = complete(Mode.REGISTER, true, "OP@example.com", true, new MockHttpServletRequest(), response);

        assertThat(outcome.status()).isEqualTo("link-required");
        verify(repository, never()).linkOidc(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
        verify(repository, never()).saveSession(anyLong(), anyString(), any(), anyString(), any(), any());
        verify(store).saveLinkIntent(new LinkIntent(20L, ISSUER, SUBJECT, "op@example.com"));
        assertThat(response.getHeaders("Set-Cookie")).anyMatch(value -> value.startsWith("OZ_GOOGLE_LINK=raw-link-token") && value.contains("HttpOnly"));
    }

    @Test
    void unverifiedGoogleEmailIsRejectedBeforeAnyLookup() {
        Outcome outcome = complete(Mode.REGISTER, true, "op@example.com", false, new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(outcome).isEqualTo(new Outcome("error", "GOOGLE_EMAIL_NOT_VERIFIED"));
        verify(repository, never()).findByOidc(anyString(), anyString());
        verify(repository, never()).findByEmail(anyString());
    }

    @Test
    void unknownGoogleAccountOnLoginAsksForSignupWithoutCreatingUser() {
        when(repository.findByOidc(ISSUER, SUBJECT)).thenReturn(Optional.empty());
        when(repository.findByEmail("new@example.com")).thenReturn(Optional.empty());

        Outcome outcome = complete(Mode.LOGIN, false, "new@example.com", true, new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(outcome.status()).isEqualTo("signup-required");
        verify(repository, never()).createUser(anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void registrationWithTermsCreatesActiveAccountWithoutPasswordAndLinksSubject() {
        when(repository.findByOidc(ISSUER, SUBJECT)).thenReturn(Optional.empty());
        when(repository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(repository.usernameExists(anyString())).thenReturn(false);
        when(repository.createUser(eq("new@example.com"), anyString(), isNull(), eq("ACTIVE"), eq("Nova Pessoa"),
            eq("NOVA PESSOA"), anyString(), anyString(), any(Instant.class))).thenReturn(30L);
        when(repository.linkOidc(eq(30L), eq("google"), eq(ISSUER), eq(SUBJECT), eq("new@example.com"), any())).thenReturn(true);
        when(repository.findById(30L)).thenReturn(Optional.of(account(30L, "new@example.com", null)));

        Outcome outcome = complete(Mode.REGISTER, true, "new@example.com", true, new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(outcome.status()).isEqualTo("success");
        verify(repository).saveSession(eq(30L), anyString(), any(), anyString(), any(), any());
        verify(repository).recordAudit(eq(30L), eq("AUTH_REGISTER_GOOGLE"), anyString(), any(), anyString(), isNull(), anyString(), any());
    }

    @Test
    void passwordLoginCompletesOnlyTheIntentIssuedForThatAccount() {
        UserAccount account = account(20L, "op@example.com", "hash");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("OZ_GOOGLE_LINK", "raw-link-token"));
        when(store.takeLinkIntent("raw-link-token")).thenReturn(Optional.of(new LinkIntent(20L, ISSUER, SUBJECT, "op@example.com")));
        when(repository.linkOidc(eq(20L), eq("google"), eq(ISSUER), eq(SUBJECT), eq("op@example.com"), any())).thenReturn(true);

        assertThat(service.completePendingLink(account, request, new MockHttpServletResponse())).isTrue();
        verify(repository).recordAudit(eq(20L), eq("AUTH_GOOGLE_LINKED"), anyString(), any(), anyString(), isNull(), anyString(), any());
    }

    @Test
    void passwordLoginOfAnotherAccountDoesNotConsumeLinkForIt() {
        UserAccount other = account(99L, "other@example.com", "hash");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("OZ_GOOGLE_LINK", "raw-link-token"));
        when(store.takeLinkIntent("raw-link-token")).thenReturn(Optional.of(new LinkIntent(20L, ISSUER, SUBJECT, "op@example.com")));

        assertThat(service.completePendingLink(other, request, new MockHttpServletResponse())).isFalse();
        verify(repository, never()).linkOidc(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void signedInUserLinksGoogleButCannotTakeAnIdentityOwnedByAnotherAccount() {
        UserAccount current = account(40L, "me@example.com", "hash");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("OZ_SESSION", "raw-session"));
        when(repository.findBySessionHash(eq(tokens.hash("raw-session")), any())).thenReturn(Optional.of(current));
        when(repository.findByOidc(ISSUER, SUBJECT)).thenReturn(Optional.of(account(41L, "owner@example.com", "hash")));

        Outcome outcome = service.complete(new Flow(Mode.LINK, false, 40L), ISSUER, SUBJECT, "owner@example.com", true,
            "Owner", request, new MockHttpServletResponse());

        assertThat(outcome).isEqualTo(new Outcome("error", "GOOGLE_ALREADY_LINKED"));
        verify(repository, never()).linkOidc(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void linkModeRequiresTheSameSignedInSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("OZ_SESSION", "raw-session"));
        when(repository.findBySessionHash(eq(tokens.hash("raw-session")), any())).thenReturn(Optional.of(account(50L, "x@example.com", "h")));
        when(repository.findByOidc(ISSUER, SUBJECT)).thenReturn(Optional.empty());

        Outcome outcome = service.complete(new Flow(Mode.LINK, false, 40L), ISSUER, SUBJECT, "x@example.com", true,
            "X", request, new MockHttpServletResponse());

        assertThat(outcome).isEqualTo(new Outcome("error", "GOOGLE_LINK_SESSION_EXPIRED"));
    }

    @Test
    void startValidatesTermsSessionAndAvailability() {
        assertThatThrownBy(() -> service.start("register", false, null, new MockHttpServletRequest(), new MockHttpServletResponse()))
            .isInstanceOf(AuthException.class).extracting("code").isEqualTo("TERMS_REQUIRED");
        assertThatThrownBy(() -> service.start("link", false, null, new MockHttpServletRequest(), new MockHttpServletResponse()))
            .isInstanceOf(AuthException.class).extracting("code").isEqualTo("AUTHENTICATION_REQUIRED");
        assertThatThrownBy(() -> service.start("admin", true, null, new MockHttpServletRequest(), new MockHttpServletResponse()))
            .isInstanceOf(AuthException.class).extracting("code").isEqualTo("INVALID_GOOGLE_MODE");

        when(store.startFlow(new Flow(Mode.LOGIN, false, null))).thenReturn("raw-flow");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(service.start("login", false, null, new MockHttpServletRequest(), response)).isEqualTo("/oauth2/authorization/google");
        assertThat(response.getHeader("Set-Cookie")).startsWith("OZ_GOOGLE_FLOW=raw-flow").contains("HttpOnly", "Secure", "SameSite=Lax");
    }

    @Test
    void disabledGoogleIsReportedAsUnavailable() {
        AuthService auth = new AuthService(properties, repository, new Argon2PasswordEncoder(16, 32, 1, 19456, 2),
            new PasswordPolicy(), tokens, rateLimiter, cookies, mock(ApplicationEventPublisher.class));
        GoogleAuthService disabled = new GoogleAuthService(auth, new GoogleAuthProperties(false, "", "", ""),
            repository, store, cookies, rateLimiter, tokens);

        assertThatThrownBy(() -> disabled.start("login", false, null, new MockHttpServletRequest(), new MockHttpServletResponse()))
            .isInstanceOf(AuthException.class).extracting("code").isEqualTo("GOOGLE_UNAVAILABLE");
    }

    @Test
    void unlinkRequiresAPasswordSoTheAccountIsNotLockedOut() {
        AuthenticatedUser principal = AuthenticatedUser.from(account(60L, "g@example.com", null));
        when(repository.findById(60L)).thenReturn(Optional.of(account(60L, "g@example.com", null)));

        assertThatThrownBy(() -> service.unlink(principal, new MockHttpServletRequest()))
            .isInstanceOf(AuthException.class).extracting("code").isEqualTo("PASSWORD_REQUIRED");
        verify(repository, never()).unlinkOidc(anyLong(), anyString());
    }

    private Outcome complete(Mode mode, boolean terms, String email, boolean verified, MockHttpServletRequest request,
                             MockHttpServletResponse response) {
        return service.complete(new Flow(mode, terms, null), ISSUER, SUBJECT, email, verified, "Nova Pessoa", request, response);
    }

    private static UserAccount account(long id, String email, String passwordHash) {
        return new UserAccount(id, UUID.randomUUID(), email, "user" + id, passwordHash, "ACTIVE", "Nova Pessoa", "NOVA PESSOA", List.of("USER"));
    }
}
