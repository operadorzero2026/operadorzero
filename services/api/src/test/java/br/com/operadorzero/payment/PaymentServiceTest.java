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
import br.com.operadorzero.payment.MercadoPagoClient.PixRequest;
import br.com.operadorzero.payment.MercadoPagoClient.RemotePayment;
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

class PaymentServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final BigDecimal PRICE = new BigDecimal("80.00");
    private final PaymentRepository repository = mock(PaymentRepository.class);
    private final OperationRepository operations = mock(OperationRepository.class);
    private final MercadoPagoClient gateway = mock(MercadoPagoClient.class);
    private final Notifier notifier = mock(Notifier.class);
    private final MercadoPagoProperties enabled = new MercadoPagoProperties("APP_USR-token", "segredo", null, null, Duration.ofMinutes(30));
    private final PaymentService service = service(enabled);
    private final AuthenticatedUser player = new AuthenticatedUser(20L, UUID.randomUUID(), "p@example.test", "p", "P", "P", List.of("USER"));
    private final AuthenticatedUser organizer = new AuthenticatedUser(7L, UUID.randomUUID(), "o@example.test", "o", "O", "O", List.of("USER"));
    private final UUID operationId = UUID.randomUUID();
    private final UUID paymentPublicId = UUID.randomUUID();

    private PaymentService service(MercadoPagoProperties properties) {
        return new PaymentService(repository, operations, gateway, properties, mock(AuditEventRepository.class), notifier,
            URI.create("https://operadorzero.com.br/api/payments/mercadopago/webhook"), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Context ctx(String participantStatus, Instant paidAt) {
        return new Context(5L, operationId, 7L, "Operação Aurora", PRICE, "PUBLISHED", NOW.plus(Duration.ofDays(1)),
            participantStatus == null ? null : 50L, participantStatus, paidAt, "p@example.test", "P");
    }

    private void given(Context context) {
        when(repository.context(eq(operationId), anyLong(), anyBoolean())).thenReturn(Optional.of(context));
        when(repository.contextByInternalId(anyLong(), anyLong())).thenReturn(Optional.of(context));
    }

    private Payment pix(String status, BigDecimal amount) {
        return new Payment(9L, paymentPublicId, 5L, 50L, 20L, amount, "PIX", status, "123456", null, "000201...", "qr", null,
            NOW.plus(Duration.ofMinutes(20)), null, null, null, null, NOW);
    }

    private RemotePayment remote(String status, String detail, String reference, BigDecimal amount) {
        return new RemotePayment("123456", status, detail, reference, amount, "000201...", "qr", null, null);
    }

    @ParameterizedTest(name = "{0}/{1} -> {2}")
    @CsvSource({"approved,accredited,CONFIRMED", "pending,pending_waiting_transfer,PENDING", "in_process,,PENDING",
        "rejected,cc_rejected_other_reason,REJECTED", "cancelled,expired,EXPIRED", "cancelled,by_collector,CANCELLED",
        "refunded,,REFUNDED", "charged_back,,REFUNDED"})
    void mapsMercadoPagoStatus(String status, String detail, String expected) {
        assertThat(PaymentService.map(remote(status, detail, "x", PRICE))).isEqualTo(expected);
    }

    @Test
    void registeredButNotApprovedCannotPay() {
        given(ctx("REQUESTED", null));
        assertThatThrownBy(() -> service.createPix(player, operationId))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getMessage()).contains("aprovação"));
        verify(gateway, never()).createPix(any());
    }

    @Test
    void notRegisteredCannotPayAndBeingRegisteredIsNotBeingPaid() {
        given(ctx(null, null));
        assertThatThrownBy(() -> service.createPix(player, operationId)).isInstanceOf(BusinessException.class);
        given(ctx("APPROVED", null));
        when(repository.latest(50L)).thenReturn(Optional.empty());
        assertThat(service.mine(player, operationId).status()).isEqualTo("UNPAID");
    }

    @Test
    void withoutCredentialsPixIsOffButStatusStillShows() {
        PaymentService off = service(new MercadoPagoProperties("", "", null, null, null));
        given(ctx("APPROVED", null));
        assertThatThrownBy(() -> off.createPix(player, operationId))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getMessage()).contains("indisponível"));
        when(repository.latest(50L)).thenReturn(Optional.empty());
        assertThat(off.mine(player, operationId).onlineEnabled()).isFalse();
    }

    @Test
    void approvedPlayerGetsPixWithIdempotencyKeyAndPrice() {
        given(ctx("APPROVED", null));
        when(repository.pending(50L, true)).thenReturn(Optional.empty());
        when(repository.insert(eq(5L), eq(50L), eq(20L), eq(PRICE), eq("PIX"), eq("PENDING"), isNull(), isNull(), eq(NOW))).thenReturn(9L);
        when(repository.byId(9L, true)).thenReturn(Optional.of(pix("PENDING", PRICE)));
        when(gateway.createPix(any())).thenReturn(remote("pending", "pending_waiting_transfer", paymentPublicId.toString(), PRICE));
        when(repository.latest(50L)).thenReturn(Optional.of(pix("PENDING", PRICE)));

        var mine = service.createPix(player, operationId);

        ArgumentCaptor<PixRequest> request = ArgumentCaptor.forClass(PixRequest.class);
        verify(gateway).createPix(request.capture());
        assertThat(request.getValue().idempotencyKey()).isEqualTo(paymentPublicId.toString());
        assertThat(request.getValue().externalReference()).isEqualTo(paymentPublicId.toString());
        assertThat(request.getValue().amount()).isEqualByComparingTo(PRICE);
        assertThat(request.getValue().expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        verify(repository).attachPix(eq(9L), eq("123456"), eq("pending"), anyString(), eq("000201..."), eq("qr"), isNull(), any(), eq(NOW));
        assertThat(mine.status()).isEqualTo("PENDING");
        assertThat(mine.pix().code()).isEqualTo("000201...");
    }

    @Test
    void openPixIsReusedInsteadOfChargingTwice() {
        given(ctx("APPROVED", null));
        when(repository.pending(50L, true)).thenReturn(Optional.of(pix("PENDING", PRICE)));
        when(repository.latest(50L)).thenReturn(Optional.of(pix("PENDING", PRICE)));
        service.createPix(player, operationId);
        verify(gateway, never()).createPix(any());
        verify(repository, never()).insert(anyLong(), anyLong(), anyLong(), any(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void approvalFromMercadoPagoConfirmsAndReleasesDocuments() {
        given(ctx("APPROVED", null));
        service.apply(pix("PENDING", PRICE), remote("approved", "accredited", paymentPublicId.toString(), PRICE), "WEBHOOK");
        verify(repository).updateStatus(9L, "CONFIRMED", "approved", "accredited", null, NOW);
        verify(repository).markParticipantPaid(50L, NOW);
        verify(notifier).toUserAt(eq(20L), eq("PAYMENT_CONFIRMED"), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void foreignReferenceOrDifferentAmountIsIgnored() {
        service.apply(pix("PENDING", PRICE), remote("approved", null, UUID.randomUUID().toString(), PRICE), "WEBHOOK");
        service.apply(pix("PENDING", PRICE), remote("approved", null, paymentPublicId.toString(), new BigDecimal("1.00")), "WEBHOOK");
        verify(repository, never()).markParticipantPaid(anyLong(), any());
        verify(repository, never()).updateStatus(anyLong(), anyString(), any(), any(), any(), any());
    }

    @Test
    void confirmedPaymentOnlyLeavesByRefund() {
        given(ctx("APPROVED", NOW));
        service.apply(pix("CONFIRMED", PRICE), remote("cancelled", "by_collector", paymentPublicId.toString(), PRICE), "SYNC");
        verify(repository, never()).updateStatus(anyLong(), anyString(), any(), any(), any(), any());
        service.apply(pix("CONFIRMED", PRICE), remote("refunded", null, paymentPublicId.toString(), PRICE), "WEBHOOK");
        verify(repository).updateStatus(9L, "REFUNDED", "refunded", null, null, NOW);
        verify(repository).clearParticipantPaidIfNoneLeft(50L, NOW);
    }

    @Test
    void lateApprovalOfExpiredPixStillCountsAsPaid() {
        given(ctx("APPROVED", null));
        service.apply(pix("EXPIRED", PRICE), remote("approved", "accredited", paymentPublicId.toString(), PRICE), "WEBHOOK");
        verify(repository).markParticipantPaid(50L, NOW);
    }

    @Test
    void webhookForUnknownPaymentDoesNotCallMercadoPago() {
        when(repository.byProviderId("999", true)).thenReturn(Optional.empty());
        service.notification("payment", "999");
        verify(gateway, never()).find(anyString());
    }

    @Test
    void onlyOrganizerConfirmsManually() {
        given(ctx("APPROVED", null));
        when(operations.isOperationAdmin(5L, 20L)).thenReturn(false);
        assertThatThrownBy(() -> service.confirmManually(player, operationId, UUID.randomUUID(), "Pago em dinheiro"))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("ACCESS_DENIED"));
    }

    @Test
    void organizerConfirmsCashPaymentAndCancelsOpenPix() {
        given(ctx(null, null));
        UUID operator = UUID.randomUUID();
        when(repository.participantByOperator(5L, operator)).thenReturn(Optional.of(new long[] {50L, 20L}));
        when(repository.pending(50L, true)).thenReturn(Optional.of(pix("PENDING", PRICE)));
        when(gateway.find("123456")).thenReturn(Optional.of(remote("pending", null, paymentPublicId.toString(), PRICE)));
        when(repository.participantPaymentConfirmedAt(50L)).thenReturn(Optional.empty());
        when(repository.insert(eq(5L), eq(50L), eq(20L), eq(PRICE), eq("MANUAL"), eq("CONFIRMED"), eq(7L), eq("Pago em dinheiro"), eq(NOW))).thenReturn(10L);

        service.confirmManually(organizer, operationId, operator, "Pago em dinheiro");

        verify(repository).updateStatus(9L, "CANCELLED", null, null, null, NOW);
        verify(gateway).cancel("123456");
        verify(repository).markParticipantPaid(50L, NOW);
    }

    @Test
    void organizerCannotConfirmTwice() {
        given(ctx(null, null));
        UUID operator = UUID.randomUUID();
        when(repository.participantByOperator(5L, operator)).thenReturn(Optional.of(new long[] {50L, 20L}));
        when(repository.pending(50L, true)).thenReturn(Optional.empty());
        when(repository.participantPaymentConfirmedAt(50L)).thenReturn(Optional.of(NOW));
        assertThatThrownBy(() -> service.confirmManually(organizer, operationId, operator, "De novo"))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("ALREADY_PAID"));
    }

    @Test
    void refundIsOnlyRecordedNeverSentAutomatically() {
        given(ctx(null, null));
        UUID operator = UUID.randomUUID();
        when(repository.participantByOperator(5L, operator)).thenReturn(Optional.of(new long[] {50L, 20L}));
        when(repository.latestConfirmed(50L, true)).thenReturn(Optional.of(pix("CONFIRMED", PRICE)));
        service.registerRefund(organizer, operationId, operator, "Devolvido pelo painel do Mercado Pago");
        verify(repository).updateStatus(9L, "REFUNDED", null, null, "Devolvido pelo painel do Mercado Pago", NOW);
        verify(repository).clearParticipantPaidIfNoneLeft(50L, NOW);
        verify(gateway, never()).cancel(anyString());
    }

    @Test
    void webhookSignatureFollowsMercadoPagoManifest() {
        String ts = "1704908010";
        String v1 = WebhookSignature.hmac("segredo", "id:abc123;request-id:req-1;ts:" + ts + ";");
        assertThat(WebhookSignature.valid("segredo", "ts=" + ts + ",v1=" + v1, "req-1", "ABC123")).isTrue();
        assertThat(WebhookSignature.valid("segredo", "ts=" + ts + ",v1=" + v1, "req-1", "abc124")).isFalse();
        assertThat(WebhookSignature.valid("outro", "ts=" + ts + ",v1=" + v1, "req-1", "abc123")).isFalse();
        assertThat(WebhookSignature.valid("segredo", null, "req-1", "abc123")).isFalse();
        assertThat(WebhookSignature.valid("", "ts=" + ts + ",v1=" + v1, "req-1", "abc123")).isFalse();
    }
}
