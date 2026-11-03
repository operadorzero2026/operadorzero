package br.com.operadorzero.payment;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PaymentDtos {
    private PaymentDtos() {}

    /**
     * Situacao financeira de quem pede. status: FREE (operacao gratuita), NOT_REGISTERED, UNPAID, PENDING, CONFIRMED, REJECTED,
     * CANCELLED, EXPIRED, REFUNDED, PARTIALLY_REFUNDED ou CHARGEBACK. canPay: pode iniciar um pagamento agora.
     * checkoutEnabled: o organizador recebe pelo Mercado Pago (com divisao); pixEnabled: Pix direto da plataforma (sem divisao),
     * so quando o organizador nao conectou a conta. pendingMethod/checkoutUrl: pagamento em andamento.
     */
    public record Mine(String status, BigDecimal amount, boolean onlineEnabled, boolean canPay, String reason, Instant confirmedAt,
                       PixCharge pix, boolean checkoutEnabled, boolean pixEnabled, String pendingMethod, String checkoutUrl,
                       String providerPaymentId) {}

    /** Pix pendente para pagar (copia e cola e QR Code). */
    public record PixCharge(UUID id, BigDecimal amount, String code, String qrBase64, String ticketUrl, Instant expiresAt) {}

    public record Checkout(String url) {}

    /** Totais: confirmed = valor informado pelo Mercado Pago; estimated = previsto na criacao (tarifa ainda desconhecida). */
    public record Summary(long unpaid, long pending, long confirmed, long cancelled, long refunded, BigDecimal confirmedTotal,
                          BigDecimal providerFees, BigDecimal commission, BigDecimal netOrganizer, BigDecimal refundedTotal,
                          boolean feesEstimated) {}

    public record ManagedRow(UUID operatorId, String callsign, String participantStatus, String paymentStatus, String method,
                             BigDecimal amount, Instant updatedAt, String note, String provider, String paymentType,
                             BigDecimal commission, BigDecimal providerFee, BigDecimal refunded, BigDecimal net, boolean feesConfirmed,
                             String transactionId, boolean refundableOnline) {}

    public record Managed(BigDecimal price, boolean onlineEnabled, Summary summary, List<ManagedRow> rows, boolean marketplace,
                          BigDecimal commissionPercent) {}

    public record ManualRequest(@NotBlank @Size(min = 3, max = 300) String note) {}

    /** Reembolso pelo Mercado Pago pedido pelo organizador. amount vazio = total. */
    public record RefundRequest(@NotBlank @Size(min = 3, max = 300) String note,
                                @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal amount) {}

    public record CommissionRequest(@DecimalMin("0") @DecimalMax("50") @Digits(integer = 2, fraction = 2) BigDecimal percent,
                                    @Size(max = 80) String username, @Size(max = 300) String note) {}
}
