package br.com.operadorzero.payment;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.payment.MarketplaceRepository.CommissionRow;
import br.com.operadorzero.payment.MarketplaceRepository.Connection;
import br.com.operadorzero.payment.MercadoPagoClient.OAuthException;
import br.com.operadorzero.payment.MercadoPagoClient.OAuthTokens;
import br.com.operadorzero.payment.MercadoPagoClient.PaymentGatewayException;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.web.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marketplace Mercado Pago (Split 1:1): cada organizador conecta a propria conta por OAuth (Authorization Code + PKCE S256,
 * state de uso unico ligado a sessao), os tokens ficam cifrados e sao renovados antes de vencer. Tambem guarda a comissao
 * da plataforma (padrao e individual), que so o administrador altera.
 */
@Service
public class MarketplaceService {
    private static final Logger log = LoggerFactory.getLogger(MarketplaceService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration ATTEMPT_TTL = Duration.ofMinutes(10);
    /** Renova quando faltar menos que isto (o token vale 180 dias). */
    private static final Duration REFRESH_BEFORE = Duration.ofDays(30);

    public record ConnectionView(boolean available, String status, Long accountId, Boolean liveMode, Instant connectedAt,
                                 Instant expiresAt, String lastError, BigDecimal commissionPercent, boolean testEnvironment) {}

    public record CommissionSettings(BigDecimal defaultPercent, List<CommissionRow> organizers, List<CommissionRow> history) {}

    private final MarketplaceRepository repository;
    private final MercadoPagoClient gateway;
    private final MercadoPagoProperties properties;
    private final SecretBox box;
    private final AuditEventRepository audit;
    private final URI redirectUri;
    private final String frontendBaseUrl;
    private final Clock clock;

    @Autowired
    public MarketplaceService(MarketplaceRepository repository, MercadoPagoClient gateway, MercadoPagoProperties properties, SecretBox box,
                              AuditEventRepository audit, @Value("${app.auth.frontend-base-url:}") String frontendBaseUrl) {
        this(repository, gateway, properties, box, audit, frontendBaseUrl, Clock.systemUTC());
    }

    MarketplaceService(MarketplaceRepository repository, MercadoPagoClient gateway, MercadoPagoProperties properties, SecretBox box,
                       AuditEventRepository audit, String frontendBaseUrl, Clock clock) {
        this.repository = repository;
        this.gateway = gateway;
        this.properties = properties;
        this.box = box;
        this.audit = audit;
        this.frontendBaseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.replaceAll("/+$", "");
        this.redirectUri = properties.redirectUri() != null ? properties.redirectUri()
            : URI.create(this.frontendBaseUrl + "/api/payments/mercadopago/callback");
        this.clock = clock;
    }

    // ---- conexao do organizador ----

    @Transactional(readOnly = true)
    public ConnectionView status(AuthenticatedUser user) {
        Optional<Connection> connection = repository.connection(user.internalId(), false);
        String status = connection.map(Connection::status).orElse("NOT_CONNECTED");
        return new ConnectionView(properties.marketplaceEnabled(), status, connection.map(Connection::mpUserId).orElse(null),
            connection.map(Connection::liveMode).orElse(null), connection.map(Connection::connectedAt).orElse(null),
            connection.map(Connection::expiresAt).orElse(null), connection.map(Connection::lastError).orElse(null),
            commissionPercent(user.internalId()), properties.testEnvironment());
    }

    /** Inicia o OAuth: devolve a URL do Mercado Pago. O state e o code_verifier ficam no servidor (hash / cifrado). */
    @Transactional
    public URI startConnection(AuthenticatedUser user) {
        if (!properties.marketplaceEnabled()) {
            throw BusinessException.conflict("MARKETPLACE_UNAVAILABLE", "A conexão com o Mercado Pago ainda não está disponível.");
        }
        Instant now = clock.instant();
        if (repository.recentAttempts(user.internalId(), now.minus(Duration.ofHours(1))) >= 10) {
            throw BusinessException.tooManyRequests("Muitas tentativas de conexão. Aguarde alguns minutos.");
        }
        repository.purgeAttempts(now.minus(Duration.ofDays(1)));
        String state = random(32);
        String verifier = random(48); // 64 caracteres (PKCE exige de 43 a 128)
        repository.saveAttempt(sha256Hex(state), user.internalId(), box.seal(verifier), now.plus(ATTEMPT_TTL), now);
        return gateway.authorizationUrl(state, codeChallenge(verifier), redirectUri);
    }

    /**
     * Retorno do Mercado Pago. Confere o state (uso unico, 10 min) e se quem voltou e a mesma pessoa que iniciou; troca o
     * codigo com o code_verifier e vincula a conta. Devolve o endereco do site para onde redirecionar.
     */
    @Transactional(noRollbackFor = RuntimeException.class)
    public String callback(AuthenticatedUser sessionUser, String code, String state, String error) {
        Instant now = clock.instant();
        if (state == null || state.isBlank() || state.length() > 200) return result("invalid_state");
        Optional<MarketplaceRepository.Attempt> attempt = repository.consumeAttempt(sha256Hex(state), now);
        if (attempt.isEmpty() || attempt.get().expiresAt().isBefore(now)) return result("invalid_state");
        long userId = attempt.get().userId();
        if (sessionUser == null || sessionUser.internalId() != userId) {
            log.warn("Retorno do OAuth do Mercado Pago com sessao diferente de quem iniciou; conexao recusada");
            return result("session_mismatch");
        }
        if (error != null && !error.isBlank()) return result("cancelled");
        if (code == null || code.isBlank() || code.length() > 300) return result("invalid_code");
        OAuthTokens tokens;
        try {
            tokens = gateway.exchangeCode(code, box.open(attempt.get().codeVerifierEnc()), redirectUri);
        } catch (OAuthException rejected) {
            markError(userId, "Autorização recusada (" + rejected.code() + ")", now);
            return result("invalid_grant".equals(rejected.code()) ? "code_expired" : "authorization_error");
        } catch (PaymentGatewayException unavailable) {
            return result("unavailable");
        }
        if (tokens.userId() == null || tokens.refreshToken() == null) {
            markError(userId, "Resposta do Mercado Pago sem conta ou sem acesso offline", now);
            return result("authorization_error");
        }
        Optional<Connection> other = repository.connectedByAccount(tokens.userId());
        if (other.isPresent() && other.get().userId() != userId) return result("account_in_use");
        repository.saveConnected(userId, tokens.userId(), box.seal(tokens.accessToken()), box.seal(tokens.refreshToken()),
            tokens.publicKey(), tokens.scope(), tokens.liveMode(), now.plusSeconds(tokens.expiresIn()), now);
        audit.record(userId, "MERCADOPAGO_CONNECTED", "USER", sessionUser.publicId(), null, now);
        return result("connected");
    }

    @Transactional
    public void disconnect(AuthenticatedUser user) {
        Instant now = clock.instant();
        Connection connection = repository.connection(user.internalId(), true)
            .orElseThrow(() -> BusinessException.notFound("Nenhuma conta Mercado Pago conectada."));
        if ("DISCONNECTED".equals(connection.status())) return;
        repository.markStatus(user.internalId(), "DISCONNECTED", null, now);
        audit.record(user.internalId(), "MERCADOPAGO_DISCONNECTED", "USER", user.publicId(), null, now);
    }

    public boolean connected(long organizerUserId) {
        return properties.marketplaceEnabled()
            && repository.connection(organizerUserId, false).map(c -> "CONNECTED".equals(c.status())).orElse(false);
    }

    public Optional<Long> accountId(long organizerUserId) {
        return repository.connection(organizerUserId, false).filter(c -> "CONNECTED".equals(c.status())).map(Connection::mpUserId);
    }

    public Optional<Long> organizerByAccount(long mpUserId) {
        return repository.connectedByAccount(mpUserId).map(Connection::userId);
    }

    /**
     * Token do organizador para criar a cobranca ou consultar pagamentos; renova antes de vencer. Sem token valido a conexao
     * passa a "Necessita reconexao" e a cobranca e recusada (nunca usa o token da plataforma no lugar).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = RuntimeException.class)
    public String sellerToken(long organizerUserId) {
        Instant now = clock.instant();
        Connection connection = repository.connection(organizerUserId, true)
            .filter(c -> "CONNECTED".equals(c.status()) && c.accessTokenEnc() != null)
            .orElseThrow(() -> new PaymentGatewayException("O organizador ainda não conectou a conta Mercado Pago."));
        if (connection.expiresAt() == null || connection.expiresAt().isBefore(now.plus(REFRESH_BEFORE))) {
            return refresh(connection, now);
        }
        return box.open(connection.accessTokenEnc());
    }

    /** O Mercado Pago recusou o token: o organizador precisa reconectar. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void tokenRejected(long organizerUserId) {
        repository.markStatus(organizerUserId, "NEEDS_RECONNECT", "O Mercado Pago recusou o acesso. Conecte a conta novamente.", clock.instant());
    }

    /** Renovacao preventiva diaria dos tokens que vencem em ate 30 dias. */
    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT24H")
    public void refreshExpiring() {
        if (!properties.marketplaceEnabled()) return;
        for (Long userId : repository.expiring(clock.instant().plus(REFRESH_BEFORE))) {
            try {
                sellerToken(userId);
            } catch (RuntimeException failure) {
                log.warn("Renovacao do token Mercado Pago falhou para um organizador: {}", failure.getClass().getSimpleName());
            }
        }
    }

    private String refresh(Connection connection, Instant now) {
        try {
            OAuthTokens tokens = gateway.refresh(box.open(connection.refreshTokenEnc()));
            repository.saveRefreshed(connection.userId(), box.seal(tokens.accessToken()),
                tokens.refreshToken() == null ? null : box.seal(tokens.refreshToken()), now.plusSeconds(tokens.expiresIn()), now);
            return tokens.accessToken();
        } catch (OAuthException rejected) {
            repository.markStatus(connection.userId(), "NEEDS_RECONNECT", "A autorização expirou ou foi revogada. Conecte a conta novamente.", now);
            throw new PaymentGatewayException("A conta Mercado Pago do organizador precisa ser reconectada.");
        }
    }

    private void markError(long userId, String message, Instant now) {
        if (repository.connection(userId, false).isPresent()) repository.markStatus(userId, "ERROR", message, now);
    }

    private String result(String code) {
        return frontendBaseUrl + "/financeiro?mercadopago=" + code;
    }

    // ---- comissao ----

    /** Percentual vigente para um organizador (individual, senao o padrao). */
    public BigDecimal commissionPercent(long organizerUserId) {
        Instant now = clock.instant();
        Optional<CommissionRow> individual = repository.overrideRow(organizerUserId, now);
        return individual.isPresent() && individual.get().percent() != null ? individual.get().percent() : defaultPercent(now);
    }

    private BigDecimal defaultPercent(Instant now) {
        return repository.defaultPercent(now).orElse(properties.defaultCommissionPercent());
    }

    /**
     * Comissao em reais sobre o valor bruto da inscricao (o marketplace_fee do Mercado Pago e um valor fixo definido ao criar a
     * cobranca, antes de saber o meio de pagamento e a tarifa). Arredonda para baixo e nunca chega ao valor total.
     */
    public static BigDecimal commissionAmount(BigDecimal price, BigDecimal percent) {
        BigDecimal value = price.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
        return value.compareTo(price) >= 0 ? price.subtract(new BigDecimal("0.01")).max(BigDecimal.ZERO) : value;
    }

    @Transactional(readOnly = true)
    public CommissionSettings commissionSettings() {
        Instant now = clock.instant();
        return new CommissionSettings(defaultPercent(now), repository.overrides(now), repository.history(30));
    }

    @Transactional
    public void setDefaultCommission(AuthenticatedUser admin, BigDecimal percent, String note) {
        validPercent(percent);
        Instant now = clock.instant();
        repository.insertCommission(null, percent, admin.internalId(), note, now);
        audit.record(admin.internalId(), "PLATFORM_COMMISSION_DEFAULT_CHANGED", "PLATFORM", null, percent.toPlainString() + "%", now);
    }

    /** percent nulo = o organizador volta a seguir o padrao. */
    @Transactional
    public void setOrganizerCommission(AuthenticatedUser admin, String username, BigDecimal percent, String note) {
        if (percent != null) validPercent(percent);
        long userId = repository.userIdByUsername(username == null ? "" : username.strip().replaceFirst("^@", ""))
            .orElseThrow(() -> BusinessException.notFound("Organizador não encontrado."));
        Instant now = clock.instant();
        repository.insertCommission(userId, percent, admin.internalId(), note, now);
        audit.record(admin.internalId(), "PLATFORM_COMMISSION_ORGANIZER_CHANGED", "USER", null,
            username + ": " + (percent == null ? "padrão" : percent.toPlainString() + "%"), now);
    }

    private static void validPercent(BigDecimal percent) {
        if (percent == null || percent.signum() < 0 || percent.compareTo(BigDecimal.valueOf(50)) > 0 || percent.scale() > 2) {
            throw BusinessException.badRequest("INVALID_COMMISSION", "Informe um percentual entre 0 e 50, com até duas casas decimais.");
        }
    }

    // ---- PKCE ----

    static String codeChallenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String random(int bytes) {
        byte[] data = new byte[bytes];
        RANDOM.nextBytes(data);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
