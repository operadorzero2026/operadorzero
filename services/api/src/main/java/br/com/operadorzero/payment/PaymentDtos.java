package br.com.operadorzero.payment;

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
     * CANCELLED, EXPIRED ou REFUNDED. canPay: pode gerar um Pix agora.
     */
    public record Mine(String status, BigDecimal amount, boolean onlineEnabled, boolean canPay, String reason, Instant confirmedAt,
                       PixCharge pix) {}

    /** Pix pendente para pagar (copia e cola e QR Code). */
    public record PixCharge(UUID id, BigDecimal amount, String code, String qrBase64, String ticketUrl, Instant expiresAt) {}

    public record Summary(long unpaid, long pending, long confirmed, long cancelled, long refunded, BigDecimal confirmedTotal) {}

    public record ManagedRow(UUID operatorId, String callsign, String participantStatus, String paymentStatus, String method,
                             BigDecimal amount, Instant updatedAt, String note) {}

    public record Managed(BigDecimal price, boolean onlineEnabled, Summary summary, List<ManagedRow> rows) {}

    public record ManualRequest(@NotBlank @Size(min = 3, max = 300) String note) {}
}
