package br.com.operadorzero.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.payment.MarketplaceRepository.Attempt;
import br.com.operadorzero.payment.MarketplaceRepository.CommissionRow;
import br.com.operadorzero.payment.MarketplaceRepository.Connection;
import br.com.operadorzero.payment.MercadoPagoClient.OAuthException;
import br.com.operadorzero.payment.MercadoPagoClient.OAuthTokens;
import br.com.operadorzero.payment.MercadoPagoClient.PaymentGatewayException;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.web.BusinessException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** OAuth do organizador (PKCE S256, state de uso unico ligado a sessao), tokens cifrados, renovacao e comissoes. */
class MarketplaceServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private final MarketplaceRepository repository = mock(MarketplaceRepository.class);
    private final MercadoPagoClient gateway = mock(MercadoPagoClient.class);
    private final SecretBox box = new SecretBox(new byte[32]);
    private final MercadoPagoProperties properties = new MercadoPagoProperties(null, "segredo", null, null, null, "app-id", "app-secret",
        null, null, "test", BigDecimal.TEN, null);
    private final MarketplaceService service = new MarketplaceService(repository, gateway, properties, box, mock(AuditEventRepository.class),
        "https://operadorzero.com.br", Clock.fixed(NOW, ZoneOffset.UTC));
    private final AuthenticatedUser organizer = new AuthenticatedUser(7L, UUID.randomUUID(), "o@example.test", "o", "O", "O", List.of("USER"));
    private final AuthenticatedUser intruder = new AuthenticatedUser(8L, UUID.randomUUID(), "x@example.test", "x", "X", "X", List.of("USER"));
    private final URI callbackUri = URI.create("https://operadorzero.com.br/api/payments/mercadopago/callback");

    private Attempt attempt(long userId, String verifier, Instant expiresAt) {
        return new Attempt(userId, box.seal(verifier), expiresAt, NOW);
    }

    private OAuthTokens tokens(long account) {
        return new OAuthTokens("APP_USR-access", "TG-refresh", 15_552_000L, account, "APP_USR-public", "offline_access read write", false);
    }

    @Test
    void connectionStartsWithPkceS256AndAStateKeptOnlyAsHash() {
        when(gateway.authorizationUrl(anyString(), anyString(), any())).thenReturn(URI.create("https://auth.mercadopago.com/authorization?x"));
        service.startConnection(organizer);
        ArgumentCaptor<String> state = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> challenge = ArgumentCaptor.forClass(String.class);
        verify(gateway).authorizationUrl(state.capture(), challenge.capture(), eq(callbackUri));
        ArgumentCaptor<String> stateHash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> verifierEnc = ArgumentCaptor.forClass(String.class);
        verify(repository).saveAttempt(stateHash.capture(), eq(7L), verifierEnc.capture(), eq(NOW.plus(Duration.ofMinutes(10))), eq(NOW));
        assertThat(stateHash.getValue()).isEqualTo(MarketplaceService.sha256Hex(state.getValue())).isNotEqualTo(state.getValue());
        String verifier = box.open(verifierEnc.getValue());
        assertThat(verifier.length()).isBetween(43, 128);
        assertThat(challenge.getValue()).isEqualTo(MarketplaceService.codeChallenge(verifier)).doesNotContain("=");
    }

    @Test
    void pkceChallengeMatchesTheRfc7636Example() {
        assertThat(MarketplaceService.codeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
            .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    @Test
    void connectionIsUnavailableWithoutTheApplicationCredentials() {
        MarketplaceService off = new MarketplaceService(repository, gateway, new MercadoPagoProperties(null, null, null, null, null),
            box, mock(AuditEventRepository.class), "https://operadorzero.com.br", Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> off.startConnection(organizer))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("MARKETPLACE_UNAVAILABLE"));
    }

    @Test
    void unknownReusedOrExpiredStateIsRefused() {
        when(repository.consumeAttempt(anyString(), any())).thenReturn(Optional.empty());
        assertThat(service.callback(organizer, "code", "forjado", null)).endsWith("/financeiro?mercadopago=invalid_state");
        assertThat(service.callback(organizer, "code", null, null)).endsWith("mercadopago=invalid_state");
        when(repository.consumeAttempt(eq(MarketplaceService.sha256Hex("velho")), any())).thenReturn(Optional.of(attempt(7L, "v".repeat(64), NOW.minusSeconds(1))));
        assertThat(service.callback(organizer, "code", "velho", null)).endsWith("mercadopago=invalid_state");
        verify(gateway, never()).exchangeCode(anyString(), anyString(), any());
    }

    @Test
    void anotherSessionCannotFinishSomeoneElsesConnection() {
        when(repository.consumeAttempt(anyString(), any())).thenReturn(Optional.of(attempt(7L, "v".repeat(64), NOW.plusSeconds(300))));
        assertThat(service.callback(intruder, "code", "estado", null)).endsWith("mercadopago=session_mismatch");
        assertThat(service.callback(null, "code", "estado", null)).endsWith("mercadopago=session_mismatch");
        verify(gateway, never()).exchangeCode(anyString(), anyString(), any());
    }

    @Test
    void organizerCancellingOnMercadoPagoChangesNothing() {
        when(repository.consumeAttempt(anyString(), any())).thenReturn(Optional.of(attempt(7L, "v".repeat(64), NOW.plusSeconds(300))));
        assertThat(service.callback(organizer, null, "estado", "access_denied")).endsWith("mercadopago=cancelled");
        verify(repository, never()).saveConnected(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void expiredOrReusedCodeIsReported() {
        when(repository.consumeAttempt(anyString(), any())).thenReturn(Optional.of(attempt(7L, "v".repeat(64), NOW.plusSeconds(300))));
        when(gateway.exchangeCode(anyString(), anyString(), any())).thenThrow(new OAuthException("invalid_grant"));
        assertThat(service.callback(organizer, "code", "estado", null)).endsWith("mercadopago=code_expired");
    }

    @Test
    void successfulConnectionStoresOnlyEncryptedTokens() {
        String verifier = "v".repeat(64);
        when(repository.consumeAttempt(anyString(), any())).thenReturn(Optional.of(attempt(7L, verifier, NOW.plusSeconds(300))));
        when(gateway.exchangeCode("code", verifier, callbackUri)).thenReturn(tokens(555L));
        when(repository.connectedByAccount(555L)).thenReturn(Optional.empty());

        assertThat(service.callback(organizer, "code", "estado", null)).endsWith("mercadopago=connected");

        ArgumentCaptor<String> access = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> refresh = ArgumentCaptor.forClass(String.class);
        verify(repository).saveConnected(eq(7L), eq(555L), access.capture(), refresh.capture(), eq("APP_USR-public"), anyString(), eq(false),
            eq(NOW.plusSeconds(15_552_000L)), eq(NOW));
        assertThat(access.getValue()).doesNotContain("APP_USR-access");
        assertThat(box.open(access.getValue())).isEqualTo("APP_USR-access");
        assertThat(box.open(refresh.getValue())).isEqualTo("TG-refresh");
    }

    @Test
    void anAccountAlreadyReceivingForAnotherOrganizerIsNotLinkedTwice() {
        when(repository.consumeAttempt(anyString(), any())).thenReturn(Optional.of(attempt(7L, "v".repeat(64), NOW.plusSeconds(300))));
        when(gateway.exchangeCode(anyString(), anyString(), any())).thenReturn(tokens(555L));
        when(repository.connectedByAccount(555L)).thenReturn(Optional.of(new Connection(1L, 99L, 555L, "x", "y", null, false,
            NOW.plus(Duration.ofDays(100)), "CONNECTED", null, NOW, NOW)));
        assertThat(service.callback(organizer, "code", "estado", null)).endsWith("mercadopago=account_in_use");
        verify(repository, never()).saveConnected(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void tokenCloseToExpiryIsRenewedAndTheNewRefreshTokenIsKept() {
        when(repository.connection(7L, true)).thenReturn(Optional.of(new Connection(1L, 7L, 555L, box.seal("old"), box.seal("TG-old"), null,
            false, NOW.plus(Duration.ofDays(3)), "CONNECTED", null, NOW, NOW)));
        when(gateway.refresh("TG-old")).thenReturn(new OAuthTokens("APP_USR-new", "TG-new", 15_552_000L, 555L, null, null, false));
        assertThat(service.sellerToken(7L)).isEqualTo("APP_USR-new");
        ArgumentCaptor<String> refresh = ArgumentCaptor.forClass(String.class);
        verify(repository).saveRefreshed(eq(7L), anyString(), refresh.capture(), eq(NOW.plusSeconds(15_552_000L)), eq(NOW));
        assertThat(box.open(refresh.getValue())).isEqualTo("TG-new");
    }

    @Test
    void revokedAuthorizationMarksTheConnectionForReconnection() {
        when(repository.connection(7L, true)).thenReturn(Optional.of(new Connection(1L, 7L, 555L, box.seal("old"), box.seal("TG-old"), null,
            false, NOW.minusSeconds(10), "CONNECTED", null, NOW, NOW)));
        when(gateway.refresh("TG-old")).thenThrow(new OAuthException("invalid_grant"));
        assertThatThrownBy(() -> service.sellerToken(7L)).isInstanceOf(PaymentGatewayException.class).hasMessageContaining("reconectada");
        verify(repository).markStatus(eq(7L), eq("NEEDS_RECONNECT"), anyString(), eq(NOW));
    }

    @Test
    void disconnectedOrganizerHasNoToken() {
        when(repository.connection(7L, true)).thenReturn(Optional.of(new Connection(1L, 7L, 555L, null, null, null, false, null,
            "DISCONNECTED", null, NOW, NOW)));
        assertThatThrownBy(() -> service.sellerToken(7L)).isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void individualCommissionWinsOverTheDefaultAndNullFallsBack() {
        when(repository.defaultPercent(NOW)).thenReturn(Optional.of(new BigDecimal("10")));
        when(repository.overrideRow(7L, NOW)).thenReturn(Optional.of(new CommissionRow(7L, null, null, new BigDecimal("8"), NOW, null, null)));
        assertThat(service.commissionPercent(7L)).isEqualByComparingTo("8");
        when(repository.overrideRow(7L, NOW)).thenReturn(Optional.of(new CommissionRow(7L, null, null, null, NOW, null, null)));
        assertThat(service.commissionPercent(7L)).isEqualByComparingTo("10");
        when(repository.defaultPercent(NOW)).thenReturn(Optional.empty());
        when(repository.overrideRow(7L, NOW)).thenReturn(Optional.empty());
        assertThat(service.commissionPercent(7L)).isEqualByComparingTo("10"); // PLATFORM_DEFAULT_COMMISSION_PERCENT
    }

    @Test
    void invalidCommissionIsRefused() {
        assertThatThrownBy(() -> service.setDefaultCommission(organizer, new BigDecimal("60"), null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.setDefaultCommission(organizer, new BigDecimal("-1"), null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.setDefaultCommission(organizer, new BigDecimal("9.555"), null)).isInstanceOf(BusinessException.class);
    }
}
