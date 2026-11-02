package br.com.operadorzero.identity;

import br.com.operadorzero.identity.GoogleFlowStore.Flow;
import br.com.operadorzero.identity.GoogleFlowStore.LinkIntent;
import br.com.operadorzero.identity.GoogleFlowStore.SignupIntent;
import br.com.operadorzero.identity.GoogleFlowStore.Mode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras do login e cadastro com Google.
 *
 * A conta Google e reconhecida somente por (issuer, subject). Um e-mail igual ao de uma conta existente nunca
 * vincula automaticamente: a pessoa precisa provar a posse da conta entrando com a senha (ou ja estar logada).
 */
@Service
public class GoogleAuthService {
    static final String PROVIDER = "google";
    static final String AUTHORIZATION_PATH = "/oauth2/authorization/google";

    /** Resultado devolvido ao frontend em /?auth=STATUS&code=CODE. */
    public record Outcome(String status, String code) {
        static Outcome of(String status) {
            return new Outcome(status, null);
        }

        static Outcome error(String code) {
            return new Outcome("error", code);
        }
    }

    private final AuthService auth;
    private final GoogleAuthProperties google;
    private final IdentityRepository repository;
    private final GoogleFlowStore store;
    private final AuthCookieService cookies;
    private final AuthRateLimiter rateLimiter;
    private final TokenSupport tokens;

    public GoogleAuthService(AuthService auth, GoogleAuthProperties google, IdentityRepository repository,
                             GoogleFlowStore store, AuthCookieService cookies, AuthRateLimiter rateLimiter,
                             TokenSupport tokens) {
        this.auth = auth;
        this.google = google;
        this.repository = repository;
        this.store = store;
        this.cookies = cookies;
        this.rateLimiter = rateLimiter;
        this.tokens = tokens;
    }

    public String start(String rawMode, boolean termsAccepted, AuthenticatedUser principal,
                        HttpServletRequest request, HttpServletResponse response) {
        auth.ensureEnabled();
        if (!google.enabled()) {
            throw AuthException.googleUnavailable();
        }
        Mode mode = parseMode(rawMode);
        rateLimiter.check("google", request, remoteAddress(request), 10, Duration.ofMinutes(15));
        if (mode == Mode.REGISTER && !termsAccepted) {
            throw AuthException.termsRequired();
        }
        if (mode == Mode.LINK && principal == null) {
            throw AuthException.sessionRequired();
        }
        Long linkUserId = mode == Mode.LINK ? principal.internalId() : null;
        String raw = store.startFlow(new Flow(mode, termsAccepted, linkUserId));
        cookies.writeGoogleFlow(response, raw);
        return AUTHORIZATION_PATH;
    }

    @Transactional
    public Outcome complete(Flow flow, String issuer, String subject, String email, boolean emailVerified,
                            String displayName, HttpServletRequest request, HttpServletResponse response) {
        if (!google.enabled() || issuer == null || subject == null || subject.isBlank()) {
            return Outcome.error("GOOGLE_AUTH_FAILED");
        }
        if (!emailVerified || email == null || email.isBlank()) {
            return Outcome.error("GOOGLE_EMAIL_NOT_VERIFIED");
        }
        String normalizedEmail = auth.normalizeEmail(email);
        Optional<UserAccount> linked = repository.findByOidc(issuer, subject);
        Instant now = Instant.now();

        if (flow.mode() == Mode.LINK) {
            return linkToSignedInUser(flow, linked, issuer, subject, normalizedEmail, request, now);
        }

        if (linked.isPresent()) {
            UserAccount account = linked.get();
            if (!account.active()) {
                return Outcome.error("GOOGLE_ACCOUNT_UNAVAILABLE");
            }
            revokeCurrentSession(request, now);
            repository.touchOidcLogin(issuer, subject, now);
            auth.createSession(account, request, response, "AUTH_LOGIN_GOOGLE");
            return Outcome.of("success");
        }

        Optional<UserAccount> sameEmail = repository.findByEmail(normalizedEmail);
        if (sameEmail.isPresent()) {
            // Nunca vincula so pelo e-mail: guarda a intencao e exige prova de posse da conta (senha).
            String raw = store.saveLinkIntent(new LinkIntent(sameEmail.get().id(), issuer, subject, normalizedEmail));
            cookies.writeGoogleLink(response, raw);
            return Outcome.of("link-required");
        }

        if (flow.mode() != Mode.REGISTER || !flow.termsAccepted()) {
            // Quem tocou em "Entrar" sem ter conta: guarda a identidade ja confirmada pelo Google; basta aceitar
            // os Termos no site para concluir, sem voltar ao Google nem preencher o formulario de novo.
            cookies.writeGoogleSignup(response, store.saveSignupIntent(new SignupIntent(issuer, subject, normalizedEmail, displayName)));
            return Outcome.of("signup-required");
        }

        UserAccount account = createGoogleUser(issuer, subject, normalizedEmail, displayName, now);
        if (account == null) {
            return Outcome.error("GOOGLE_AUTH_FAILED");
        }
        auth.audit(account, "AUTH_REGISTER_GOOGLE", null, auth.ipHash(request), now);
        revokeCurrentSession(request, now);
        auth.createSession(account, request, response, "AUTH_LOGIN_GOOGLE");
        return Outcome.of("success");
    }

    /**
     * Conclui o cadastro guardado em {@link #complete} depois que a pessoa aceita os Termos no site.
     * A intencao vale uma vez, por 10 minutos, e so neste navegador (cookie HttpOnly).
     */
    @Transactional
    public UserAccount completeSignup(boolean termsAccepted, HttpServletRequest request, HttpServletResponse response) {
        auth.ensureEnabled();
        if (!google.enabled()) {
            throw AuthException.googleUnavailable();
        }
        if (!termsAccepted) {
            throw AuthException.termsRequired();
        }
        String raw = cookies.googleSignup(request).orElseThrow(AuthException::googleSignupExpired);
        cookies.clearGoogleSignup(response);
        SignupIntent intent = store.takeSignupIntent(raw).orElseThrow(AuthException::googleSignupExpired);
        Instant now = Instant.now();
        // Algo mudou nesses minutos (cadastro em outra aba): nunca cria duplicado nem vincula pelo e-mail.
        Optional<UserAccount> linked = repository.findByOidc(intent.issuer(), intent.subject());
        UserAccount account;
        if (linked.isPresent()) {
            account = linked.get();
            if (!account.active()) throw AuthException.googleSignupExpired();
        } else {
            if (repository.findByEmail(intent.email()).isPresent()) throw AuthException.googleSignupExpired();
            account = createGoogleUser(intent.issuer(), intent.subject(), intent.email(), intent.displayName(), now);
            if (account == null) throw AuthException.googleSignupExpired();
            auth.audit(account, "AUTH_REGISTER_GOOGLE", null, auth.ipHash(request), now);
        }
        revokeCurrentSession(request, now);
        auth.createSession(account, request, response, "AUTH_LOGIN_GOOGLE");
        return account;
    }

    private UserAccount createGoogleUser(String issuer, String subject, String email, String displayName, Instant now) {
        String safeDisplayName = displayName == null || displayName.isBlank() ? "Operador" : displayName.strip();
        safeDisplayName = safeDisplayName.substring(0, Math.min(safeDisplayName.length(), 80));
        long userId = repository.createUser(email, auth.uniqueUsername(safeDisplayName), null, "ACTIVE",
            safeDisplayName, auth.callsign(safeDisplayName), AuthService.TERMS_VERSION, AuthService.PRIVACY_VERSION, now);
        if (userId == 0L || !repository.linkOidc(userId, PROVIDER, issuer, subject, email, now)) {
            return null;
        }
        return repository.findById(userId).orElseThrow(AuthException::unavailable);
    }

    /**
     * Chamado depois de um login por senha bem-sucedido. Se o navegador trouxer uma intencao de vinculo valida
     * para esta mesma conta, conclui o vinculo. Retorna true se vinculou.
     */
    @Transactional
    public boolean completePendingLink(UserAccount account, HttpServletRequest request, HttpServletResponse response) {
        Optional<String> raw = cookies.googleLink(request);
        if (raw.isEmpty()) {
            return false;
        }
        cookies.clearGoogleLink(response);
        Optional<LinkIntent> intent = store.takeLinkIntent(raw.get());
        if (intent.isEmpty() || intent.get().userId() != account.id()) {
            return false;
        }
        Instant now = Instant.now();
        LinkIntent value = intent.get();
        if (!repository.linkOidc(account.id(), PROVIDER, value.issuer(), value.subject(), value.email(), now)) {
            return false;
        }
        auth.audit(account, "AUTH_GOOGLE_LINKED", null, auth.ipHash(request), now);
        return true;
    }

    @Transactional
    public void unlink(AuthenticatedUser principal, HttpServletRequest request) {
        auth.ensureEnabled();
        UserAccount account = repository.findById(principal.internalId()).orElseThrow(AuthException::sessionRequired);
        if (account.passwordHash() == null) {
            throw AuthException.passwordRequiredToUnlink();
        }
        if (repository.unlinkOidc(account.id(), PROVIDER)) {
            auth.audit(account, "AUTH_GOOGLE_UNLINKED", null, auth.ipHash(request), Instant.now());
        }
    }

    public boolean isLinked(long userId) {
        return repository.hasOidc(userId, PROVIDER);
    }

    private Outcome linkToSignedInUser(Flow flow, Optional<UserAccount> linked, String issuer, String subject,
                                       String email, HttpServletRequest request, Instant now) {
        Optional<UserAccount> current = cookies.sessionToken(request)
            .flatMap(raw -> repository.findBySessionHash(tokens.hash(raw), now))
            .filter(account -> flow.linkUserId() != null && account.id() == flow.linkUserId());
        if (current.isEmpty()) {
            return Outcome.error("GOOGLE_LINK_SESSION_EXPIRED");
        }
        UserAccount account = current.get();
        if (linked.isPresent()) {
            return linked.get().id() == account.id() ? Outcome.of("linked") : Outcome.error("GOOGLE_ALREADY_LINKED");
        }
        if (!repository.linkOidc(account.id(), PROVIDER, issuer, subject, email, now)) {
            return Outcome.error("GOOGLE_ACCOUNT_HAS_LINK");
        }
        auth.audit(account, "AUTH_GOOGLE_LINKED", null, auth.ipHash(request), now);
        return Outcome.of("linked");
    }

    private void revokeCurrentSession(HttpServletRequest request, Instant now) {
        cookies.sessionToken(request).ifPresent(raw -> repository.revokeSession(tokens.hash(raw), now));
    }

    private Mode parseMode(String rawMode) {
        try {
            return Mode.valueOf(rawMode.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw AuthException.invalidGoogleMode();
        }
    }

    private String remoteAddress(HttpServletRequest request) {
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
