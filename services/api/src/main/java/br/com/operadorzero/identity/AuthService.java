package br.com.operadorzero.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    public static final String TERMS_VERSION = "2026-07-22";
    public static final String PRIVACY_VERSION = "2026-07-22";

    private final AuthProperties properties;
    private final IdentityRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final TokenSupport tokens;
    private final AuthRateLimiter rateLimiter;
    private final AuthCookieService cookies;
    private final ApplicationEventPublisher events;
    private final String dummyPasswordHash;

    public AuthService(AuthProperties properties, IdentityRepository repository, PasswordEncoder passwordEncoder,
                       PasswordPolicy passwordPolicy, TokenSupport tokens, AuthRateLimiter rateLimiter,
                       AuthCookieService cookies, ApplicationEventPublisher events) {
        this.properties = properties;
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.tokens = tokens;
        this.rateLimiter = rateLimiter;
        this.cookies = cookies;
        this.events = events;
        this.dummyPasswordHash = passwordEncoder.encode(tokens.newToken());
    }

    @Transactional
    public void register(String displayName, String email, String password, boolean termsAccepted, HttpServletRequest request) {
        ensureEnabled();
        if (!termsAccepted) {
            throw AuthException.termsRequired();
        }
        String normalizedEmail = normalizeEmail(email);
        rateLimiter.check("register", request, normalizedEmail, 5, Duration.ofHours(1));

        passwordPolicy.validate(password, normalizedEmail);
        String passwordHash = passwordEncoder.encode(password);

        Optional<UserAccount> existing = repository.findByEmail(normalizedEmail);
        if (existing.isPresent()) {
            throw AuthException.emailAlreadyRegistered();
        }

        Instant now = Instant.now();
        String safeDisplayName = displayName.strip();
        long userId = repository.createUser(normalizedEmail, uniqueUsername(safeDisplayName), passwordHash,
            "PENDING_EMAIL", safeDisplayName, callsign(safeDisplayName), TERMS_VERSION, PRIVACY_VERSION, now);
        if (userId == 0L) {
            throw AuthException.emailAlreadyRegistered();
        }
        UserAccount account = repository.findById(userId).orElseThrow();
        issueToken(account, "VERIFY_EMAIL");
        audit(account, "AUTH_REGISTER", null, ipHash(request), now);
    }

    @Transactional
    public void verifyEmail(String rawToken, HttpServletRequest request) {
        ensureEnabled();
        Instant now = Instant.now();
        long userId = repository.consumeAuthToken(tokens.hash(rawToken), "VERIFY_EMAIL", now)
            .orElseThrow(AuthException::invalidToken);
        repository.activateUser(userId, now);
        UserAccount account = repository.findById(userId).orElseThrow(AuthException::invalidToken);
        audit(account, "AUTH_EMAIL_VERIFIED", null, ipHash(request), now);
    }

    @Transactional
    public UserAccount login(String email, String password, HttpServletRequest request, HttpServletResponse response) {
        long totalStarted = System.nanoTime();
        ensureEnabled();
        String normalizedEmail = normalizeEmail(email);
        long rateStarted = System.nanoTime();
        rateLimiter.check("login", request, normalizedEmail, 10, Duration.ofMinutes(15));
        long redisMs = elapsedMs(rateStarted);
        long findStarted = System.nanoTime();
        Optional<UserAccount> candidate = repository.findByEmail(normalizedEmail);
        long findUserMs = elapsedMs(findStarted);
        String passwordHash = candidate.map(UserAccount::passwordHash).orElse(null);
        long passwordStarted = System.nanoTime();
        boolean passwordMatches = passwordEncoder.matches(password,
            passwordHash == null ? dummyPasswordHash : passwordHash);
        long passwordMs = elapsedMs(passwordStarted);
        UserAccount account = candidate.orElse(null);
        if (account == null) {
            logLoginDuration(redisMs, findUserMs, passwordMs, 0, 0, 0, totalStarted, "INVALID");
            throw AuthException.invalidCredentials();
        }
        if (passwordHash == null) {
            logLoginDuration(redisMs, findUserMs, passwordMs, 0, 0, 0, totalStarted, "PASSWORD_SETUP");
            throw AuthException.passwordSetupRequired();
        }
        if (!passwordMatches) {
            logLoginDuration(redisMs, findUserMs, passwordMs, 0, 0, 0, totalStarted, "INVALID");
            throw AuthException.invalidCredentials();
        }
        if ("PENDING_EMAIL".equals(account.status())) {
            logLoginDuration(redisMs, findUserMs, passwordMs, 0, 0, 0, totalStarted, "UNVERIFIED");
            throw AuthException.emailNotVerified();
        }
        if (!account.active()) {
            logLoginDuration(redisMs, findUserMs, passwordMs, 0, 0, 0, totalStarted, "INACTIVE");
            throw AuthException.invalidCredentials();
        }
        String raw = tokens.newToken();
        String sessionHash = tokens.hash(raw);
        Instant now = Instant.now();
        long sessionStarted = System.nanoTime();
        repository.saveSession(account.id(), sessionHash, headerHash(request, "User-Agent"), ipHash(request),
            now.plus(properties.sessionDuration()), now);
        long sessionMs = elapsedMs(sessionStarted);
        long cookieStarted = System.nanoTime();
        cookies.writeSession(response, raw);
        long cookieMs = elapsedMs(cookieStarted);
        long auditStarted = System.nanoTime();
        audit(account, "AUTH_LOGIN_PASSWORD", sessionHash, ipHash(request), now);
        long auditMs = elapsedMs(auditStarted);
        logLoginDuration(redisMs, findUserMs, passwordMs, sessionMs, cookieMs, auditMs, totalStarted, "SUCCESS");
        return account;
    }

    @Transactional
    public void requestPasswordRecovery(String email, HttpServletRequest request) {
        ensureEnabled();
        String normalizedEmail = normalizeEmail(email);
        rateLimiter.check("recovery", request, normalizedEmail, 5, Duration.ofHours(1));
        Optional<UserAccount> candidate = repository.findByEmail(normalizedEmail);
        equalizeEmailActionTiming();
        candidate
            .filter(UserAccount::active)
            .ifPresent(account -> issueToken(account, "RESET_PASSWORD"));
    }

    @Transactional
    public void resendVerification(String email, HttpServletRequest request) {
        ensureEnabled();
        String normalizedEmail = normalizeEmail(email);
        rateLimiter.check("verification", request, normalizedEmail, 5, Duration.ofHours(1));
        Optional<UserAccount> candidate = repository.findByEmail(normalizedEmail);
        equalizeEmailActionTiming();
        candidate
            .filter(account -> "PENDING_EMAIL".equals(account.status()))
            .ifPresent(account -> issueToken(account, "VERIFY_EMAIL"));
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword, HttpServletRequest request) {
        ensureEnabled();
        Instant now = Instant.now();
        long userId = repository.consumeAuthToken(tokens.hash(rawToken), "RESET_PASSWORD", now)
            .orElseThrow(AuthException::invalidToken);
        UserAccount account = repository.findById(userId).filter(UserAccount::active)
            .orElseThrow(AuthException::invalidToken);
        passwordPolicy.validate(newPassword, account.email());
        repository.updatePasswordAndRevokeSessions(userId, passwordEncoder.encode(newPassword), now);
        audit(account, "AUTH_PASSWORD_RESET", null, ipHash(request), now);
        events.publishEvent(new AuthMailRequested(account.email(), account.displayName(), "PASSWORD_CHANGED", tokens.newToken()));
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response, AuthenticatedUser principal) {
        cookies.sessionToken(request).ifPresent(raw -> {
            String sessionHash = tokens.hash(raw);
            Instant now = Instant.now();
            repository.revokeSession(sessionHash, now);
            if (principal != null) {
                repository.findById(principal.internalId()).ifPresent(account ->
                    audit(account, "AUTH_LOGOUT", sessionHash, ipHash(request), now));
            }
        });
        cookies.clearSession(response);
        cookies.clearLegacyAuthentication(response);
    }

    public void clearLegacyAuthentication(HttpServletResponse response) {
        cookies.clearLegacyAuthentication(response);
    }

    private UserAccount createSession(UserAccount account, HttpServletRequest request, HttpServletResponse response, String action) {
        String raw = tokens.newToken();
        String sessionHash = tokens.hash(raw);
        Instant now = Instant.now();
        repository.saveSession(account.id(), sessionHash, headerHash(request, "User-Agent"), ipHash(request),
            now.plus(properties.sessionDuration()), now);
        cookies.writeSession(response, raw);
        audit(account, action, sessionHash, ipHash(request), now);
        return account;
    }

    private void issueToken(UserAccount account, String purpose) {
        String raw = tokens.newToken();
        Instant now = Instant.now();
        repository.saveAuthToken(account.id(), purpose, tokens.hash(raw), now.plus(properties.tokenDuration()), now);
        events.publishEvent(new AuthMailRequested(account.email(), account.displayName(), purpose, raw));
    }

    private void equalizeEmailActionTiming() {
        passwordEncoder.matches(tokens.newToken(), dummyPasswordHash);
    }

    private void audit(UserAccount account, String action, String sessionHash, String ipHash, Instant now) {
        repository.recordAudit(account.id(), action, "APP_USER", account.publicId(),
            Objects.requireNonNullElse(MDC.get("correlationId"), UUID.randomUUID().toString()), sessionHash, ipHash, now);
    }

    private String uniqueUsername(String displayName) {
        String normalized = Normalizer.normalize(displayName, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9._-]", ".")
            .replaceAll("[._-]{2,}", ".")
            .replaceAll("^[._-]+|[._-]+$", "");
        if (normalized.length() < 3) {
            normalized = "operador";
        }
        normalized = normalized.substring(0, Math.min(normalized.length(), 20));
        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = normalized + "." + tokens.newToken().substring(0, 6).toLowerCase(Locale.ROOT);
            if (!repository.usernameExists(candidate)) {
                return candidate;
            }
        }
        throw AuthException.unavailable();
    }

    private String callsign(String displayName) {
        String value = displayName.strip().toUpperCase(Locale.ROOT);
        return value.substring(0, Math.min(value.length(), 40));
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private String ipHash(HttpServletRequest request) {
        return tokens.hash(request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr());
    }

    private String headerHash(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null ? null : tokens.hash(value);
    }

    private void ensureEnabled() {
        if (!properties.enabled()) {
            throw AuthException.unavailable();
        }
    }

    private static long elapsedMs(long started) {
        return Math.round((System.nanoTime() - started) / 1_000_000.0);
    }

    private void logLoginDuration(long redisMs, long findUserMs, long passwordMs, long sessionMs,
                                  long cookieMs, long auditMs, long totalStarted, String outcome) {
        log.info("auth_login_duration correlationId={} outcome={} redisMs={} findUserMs={} passwordMs={} sessionMs={} cookieMs={} auditMs={} totalMs={}",
            MDC.get("correlationId"), outcome, redisMs, findUserMs, passwordMs, sessionMs, cookieMs, auditMs,
            elapsedMs(totalStarted));
    }
}
