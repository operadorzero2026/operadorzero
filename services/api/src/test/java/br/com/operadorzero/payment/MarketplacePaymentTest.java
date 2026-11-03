package br.com.operadorzero.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.notification.Notifier;
import br.com.operadorzero.operation.OperationRepository;
import br.com.operadorzero.payment.MercadoPagoClient.Preference;
import br.com.operadorzero.payment.MercadoPagoClient.PreferenceRequest;
import br.com.operadorzero.payment.MercadoPagoClient.RemotePayment;
import br.com.operadorzero.payment.MercadoPagoClient.TokenRejectedException;
import br.com.operadorzero.payment.PaymentRepository.Context;
import br.com.operadorzero.payment.PaymentRepository.Payment;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

/** Checkout Pro do marketplace (Split 1:1): cobranca na conta do organizador, comissao, notificacoes e reembolso. */
class MarketplacePaymentTest {
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final BigDecimal PRICE = new BigDecimal("80.00");
    private static final long ORGANIZER = 7L;
    private static final long ACCOUNT = 555L;
    private final PaymentRepository repository = mock(PaymentRepository.class);
    private final MercadoPagoClient gateway = mock(MercadoPagoClient.class);
    private final MarketplaceService marketplace = mock(MarketplaceService.class);
    private final Notifier notifier = mock(Notifier.class);
    private final MercadoPagoProperties properties = new MercadoPagoProperties(null, "segredo", null, null, null, "app-id", "app-secret",
        null, null, "test", BigDecimal.TEN, null);
    private final PaymentService service = new PaymentService(repository, mock(OperationRepository.class), gateway, properties, marketplace,
        mock(AuditEventRepository.class), notifier, URI.create("https://operadorzero.com.br/api/payments/mercadopago/webhook"),
        "https://operadorzero.com.br", Clock.fixed(NOW, ZoneOffset.UTC));
    private final AuthenticatedUser player = new AuthenticatedUser(20L, UUID.randomUUID(), "p@example.test", "p", "P", "P", List.of("USER"));
    private final AuthenticatedUser organizer = new AuthenticatedUser(ORGANIZER, UUID.randomUUID(), "o@example.test", "o", "O", "O", List.of("USER"));
    private final UUID operationId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();

    private Context ctx(String participantStatus, Instant paidAt) {
        return new Context(5L, operationId, ORGANIZER, "Operação Tempestade", PRICE, "PUBLISHED", NOW.plus(Duration.ofDays(1)),
            participantStatus == null ? null : 50L, participantStatus, paidAt, "p@example.test", "P");
    }

    private void given(Context context) {
        when(repository.context(eq(operationId), anyLong(), anyBoolean())).thenReturn(Optional.of(context));
        when(repository.contextByInternalId(anyLong(), anyLong())).thenReturn(Optional.of(context));
    }

    private Payment checkout(String status, String providerId, BigDecimal refunded) {
        return new Payment(9L, paymentId, 5L, 50L, 20L, PRICE, "CHECKOUT", status, providerId, null, null, null, null,
            NOW.plus(Duration.ofMinutes(20)), null, null, null, null, NOW, "MERCADO_PAGO_MARKETPLACE", ORGANIZER, ACCOUNT,
            "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=1", null, BigDecimal.TEN, new BigDecimal("8.00"), null, null, refunded, false);
    }

    private RemotePayment remote(String status, String detail, String reference, BigDecimal amount, Long collector) {
        return new RemotePayment("777", status, detail, reference, amount, null, null, null, null, collector, "credit_card",
            new BigDecimal("3.99"), new BigDecimal("8.00"), BigDecimal.ZERO, true);
    }

    private void connected() {
        when(marketplace.connected(ORGANIZER)).thenReturn(true);
        when(marketplace.accountId(ORGANIZER)).thenReturn(Optional.of(ACCOUNT));
        when(marketplace.sellerToken(ORGANIZER)).thenReturn("APP_USR-organizador");
    }

    @Test
    void organizerWithoutMercadoPagoCannotReceiveThroughTheMarketplace() {
        given(ctx("APPROVED", null));
        when(marketplace.connected(ORGANIZER)).thenReturn(false);
        assertThatThrownBy(() -> service.createCheckout(player, operationId))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getMessage()).contains("ainda não conectou"));
        verify(gateway, never()).createPreference(anyString(), any());
    }

    @Test
    void checkoutIsCreatedWithTheOrganizerTokenAndServerSideCommission() {
        given(ctx("APPROVED", null));
        connected();
        when(marketplace.commissionPercent(ORGANIZER)).thenReturn(BigDecimal.TEN);
        when(repository.pending(50L, true)).thenReturn(Optional.empty());
        when(repository.insertCheckout(5L, 50L, 20L, PRICE, BigDecimal.TEN, new BigDecimal("8.00"), ACCOUNT, NOW)).thenReturn(9L);
        when(repository.byId(9L, true)).thenReturn(Optional.of(checkout("PENDING", null, null)));
        when(gateway.createPreference(eq("APP_USR-organizador"), any())).thenReturn(new Preference("pref-1",
            "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=pref-1", "https://sandbox.mercadopago.com.br/checkout/v1/redirect?pref_id=pref-1", ACCOUNT));

        var checkout = service.createCheckout(player, operationId);

        ArgumentCaptor<PreferenceRequest> request = ArgumentCaptor.forClass(PreferenceRequest.class);
        verify(gateway).createPreference(eq("APP_USR-organizador"), request.capture());
        assertThat(request.getValue().amount()).isEqualByComparingTo(PRICE);
        assertThat(request.getValue().marketplaceFee()).isEqualByComparingTo("8.00");
        assertThat(request.getValue().externalReference()).isEqualTo(paymentId.toString());
        assertThat(request.getValue().notificationUrl().toString()).endsWith("/api/payments/mercadopago/webhook");
        assertThat(request.getValue().backUrl().toString()).contains("/convocacao/" + operationId);
        assertThat(checkout.url()).contains("sandbox"); // ambiente de teste
        verify(repository).attachCheckout(eq(9L), eq("pref-1"), anyString(), eq(NOW.plus(Duration.ofMinutes(30))), eq(NOW));
    }

    @Test
    void customCommissionIsAppliedToNewPayments() {
        given(ctx("APPROVED", null));
        connected();
        when(marketplace.commissionPercent(ORGANIZER)).thenReturn(new BigDecimal("5"));
        when(repository.pending(50L, true)).thenReturn(Optional.empty());
        when(repository.insertCheckout(5L, 50L, 20L, PRICE, new BigDecimal("5"), new BigDecimal("4.00"), ACCOUNT, NOW)).thenReturn(9L);
        when(repository.byId(9L, true)).thenReturn(Optional.of(checkout("PENDING", null, null)));
        when(gateway.createPreference(anyString(), any())).thenReturn(new Preference("pref-1", "https://x", null, ACCOUNT));
        service.createCheckout(player, operationId);
        ArgumentCaptor<PreferenceRequest> request = ArgumentCaptor.forClass(PreferenceRequest.class);
        verify(gateway).createPreference(anyString(), request.capture());
        assertThat(request.getValue().marketplaceFee()).isEqualByComparingTo("4.00");
    }

    @Test
    void rejectedOrganizerTokenAsksForReconnectionAndNeverFallsBackToThePlatform() {
        given(ctx("APPROVED", null));
        connected();
        when(marketplace.commissionPercent(ORGANIZER)).thenReturn(BigDecimal.TEN);
        when(repository.pending(50L, true)).thenReturn(Optional.empty());
        when(repository.insertCheckout(anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any())).thenReturn(9L);
        when(repository.byId(9L, true)).thenReturn(Optional.of(checkout("PENDING", null, null)));
        when(gateway.createPreference(anyString(), any())).thenThrow(new TokenRejectedException());
        assertThatThrownBy(() -> service.createCheckout(player, operationId)).hasMessageContaining("reconectar");
        verify(marketplace).tokenRejected(ORGANIZER);
        verify(gateway, never()).createPix(any());
    }

    @Test
    void openCheckoutIsReused() {
        given(ctx("APPROVED", null));
        connected();
        when(repository.pending(50L, true)).thenReturn(Optional.of(checkout("PENDING", null, null)));
        assertThat(service.createCheckout(player, operationId).url()).contains("pref_id=1");
        verify(gateway, never()).createPreference(anyString(), any());
    }

    @Test
    void directPixIsNotOfferedWhenTheOrganizerReceivesThroughTheMarketplace() {
        given(ctx("APPROVED", null));
        connected();
        assertThatThrownBy(() -> service.createPix(player, operationId)).hasMessageContaining("Mercado Pago do organizador");
    }

    @Test
    void notificationFromAConnectedOrganizerFindsThePaymentByReferenceAndConfirms() {
        given(ctx("APPROVED", null));
        when(repository.byProviderId("777", true)).thenReturn(Optional.empty());
        when(marketplace.organizerByAccount(ACCOUNT)).thenReturn(Optional.of(ORGANIZER));
        when(marketplace.sellerToken(ORGANIZER)).thenReturn("APP_USR-organizador");
        when(gateway.find("APP_USR-organizador", "777")).thenReturn(Optional.of(remote("approved", "accredited", paymentId.toString(), PRICE, ACCOUNT)));
        when(repository.byPublicId(paymentId, true)).thenReturn(Optional.of(checkout("PENDING", null, null)));

        assertThat(service.notification("payment", "777", ACCOUNT)).contains(9L);

        verify(repository).attachProviderId(9L, "777", NOW);
        verify(repository).updateStatus(9L, "CONFIRMED", "approved", "accredited", null, NOW);
        verify(repository).markParticipantPaid(50L, NOW);
        // tarifa e comissao efetivas: 80 - 3,99 - 8,00 = 68,01
        verify(repository).updateFinancials(9L, "credit_card", ACCOUNT, new BigDecimal("3.99"), new BigDecimal("8.00"), BigDecimal.ZERO,
            new BigDecimal("68.01"), true, NOW);
    }

    @Test
    void notificationFromAnUnknownAccountDoesNotCallMercadoPago() {
        when(repository.byProviderId("777", true)).thenReturn(Optional.empty());
        when(marketplace.organizerByAccount(999L)).thenReturn(Optional.empty());
        assertThat(service.notification("payment", "777", 999L)).isEmpty();
        verify(gateway, never()).find(anyString(), anyString());
    }

    @Test
    void paymentToAnotherAccountIsIgnored() {
        given(ctx("APPROVED", null));
        service.apply(checkout("PENDING", "777", null), remote("approved", null, paymentId.toString(), PRICE, 123L), "WEBHOOK");
        verify(repository, never()).markParticipantPaid(anyLong(), any());
    }

    @Test
    void divergentAmountIsIgnored() {
        service.apply(checkout("PENDING", "777", null), remote("approved", null, paymentId.toString(), new BigDecimal("8.00"), ACCOUNT), "WEBHOOK");
        verify(repository, never()).markParticipantPaid(anyLong(), any());
    }

    @Test
    void partialRefundKeepsThePlayerPaidAndChargebackRemovesIt() {
        given(ctx("APPROVED", NOW));
        service.apply(checkout("CONFIRMED", "777", null), remote("approved", "partially_refunded", paymentId.toString(), PRICE, ACCOUNT), "WEBHOOK");
        verify(repository).updateStatus(9L, "PARTIALLY_REFUNDED", "approved", "partially_refunded", null, NOW);
        verify(repository, never()).clearParticipantPaidIfNoneLeft(anyLong(), any());
        service.apply(checkout("PARTIALLY_REFUNDED", "777", null), remote("charged_back", "settled", paymentId.toString(), PRICE, ACCOUNT), "WEBHOOK");
        verify(repository).updateStatus(9L, "CHARGEBACK", "charged_back", "settled", null, NOW);
        verify(repository).clearParticipantPaidIfNoneLeft(50L, NOW);
        verify(notifier).toUserAt(eq(ORGANIZER), eq("PAYMENT_CHARGEBACK"), anyString(), anyString(), anyString(), anyString());
    }

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource({"PENDING,CONFIRMED,true", "EXPIRED,CONFIRMED,true", "CONFIRMED,CANCELLED,false", "CONFIRMED,REFUNDED,true",
        "CONFIRMED,PARTIALLY_REFUNDED,true", "REFUNDED,CONFIRMED,false", "CHARGEBACK,CONFIRMED,false", "PARTIALLY_REFUNDED,REFUNDED,true"})
    void statusTransitions(String from, String to, boolean allowed) {
        assertThat(PaymentService.allowed(from, to)).isEqualTo(allowed);
    }

    @Test
    void organizerRefundGoesToMercadoPagoWithTheOrganizerToken() {
        given(ctx(null, null));
        UUID operator = UUID.randomUUID();
        when(repository.participantByOperator(5L, operator)).thenReturn(Optional.of(new long[] {50L, 20L}));
        when(repository.latestConfirmed(50L, true)).thenReturn(Optional.of(checkout("CONFIRMED", "777", null)));
        when(marketplace.sellerToken(ORGANIZER)).thenReturn("APP_USR-organizador");
        when(gateway.find("APP_USR-organizador", "777")).thenReturn(Optional.of(remote("refunded", "refunded", paymentId.toString(), PRICE, ACCOUNT)));

        service.refundViaMercadoPago(organizer, operationId, operator, null, "Jogo cancelado");

        verify(gateway).refund(eq("APP_USR-organizador"), eq("777"), isNull(), anyString());
        verify(repository).updateStatus(9L, "REFUNDED", "refunded", "refunded", null, NOW);
    }

    @Test
    void refundAboveWhatIsLeftIsRejected() {
        given(ctx(null, null));
        UUID operator = UUID.randomUUID();
        when(repository.participantByOperator(5L, operator)).thenReturn(Optional.of(new long[] {50L, 20L}));
        when(repository.latestConfirmed(50L, true)).thenReturn(Optional.of(checkout("PARTIALLY_REFUNDED", "777", new BigDecimal("50.00"))));
        assertThatThrownBy(() -> service.refundViaMercadoPago(organizer, operationId, operator, new BigDecimal("40.00"), "Devolver"))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("INVALID_REFUND_AMOUNT"));
        verify(gateway, never()).refund(anyString(), anyString(), any(), anyString());
    }

    @Test
    void marketplacePaymentCannotBeMarkedRefundedByHand() {
        given(ctx(null, null));
        UUID operator = UUID.randomUUID();
        when(repository.participantByOperator(5L, operator)).thenReturn(Optional.of(new long[] {50L, 20L}));
        when(repository.latestConfirmed(50L, true)).thenReturn(Optional.of(checkout("CONFIRMED", "777", null)));
        assertThatThrownBy(() -> service.registerRefund(organizer, operationId, operator, "Devolvi"))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("USE_MERCADO_PAGO_REFUND"));
    }

    @ParameterizedTest(name = "{0} x {1}% = {2}")
    @CsvSource({"100.00,10,10.00", "80.00,10,8.00", "75.76,10,7.57", "50.00,8,4.00", "33.33,5,1.66", "10.00,0,0.00", "0.01,50,0.00"})
    void commissionIsAFixedAmountOnTheGrossValueRoundedDown(String price, String percent, String expected) {
        assertThat(MarketplaceService.commissionAmount(new BigDecimal(price), new BigDecimal(percent))).isEqualByComparingTo(expected);
    }
}
