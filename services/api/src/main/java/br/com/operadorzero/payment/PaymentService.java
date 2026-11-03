package br.com.operadorzero.payment;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.notification.Notifier;
import br.com.operadorzero.operation.OperationDtos.MessageResponse;
import br.com.operadorzero.operation.OperationRepository;
import br.com.operadorzero.payment.MercadoPagoClient.PaymentGatewayException;
import br.com.operadorzero.payment.MercadoPagoClient.PixRequest;
import br.com.operadorzero.payment.MercadoPagoClient.RemotePayment;
import br.com.operadorzero.payment.PaymentDtos.Managed;
import br.com.operadorzero.payment.PaymentDtos.ManagedRow;
import br.com.operadorzero.payment.PaymentDtos.Mine;
import br.com.operadorzero.payment.PaymentDtos.PixCharge;
import br.com.operadorzero.payment.PaymentDtos.Summary;
import br.com.operadorzero.payment.PaymentRepository.Context;
import br.com.operadorzero.payment.PaymentRepository.Payment;
import br.com.operadorzero.payment.PaymentRepository.Row;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.web.BusinessException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pagamento das inscricoes. O status financeiro e separado do status da inscricao: estar inscrito nao significa ter pago.
 * Pix pelo Mercado Pago (o status vem sempre da consulta ao Mercado Pago, nunca do corpo do webhook) ou confirmacao manual do
 * organizador. Nada e apagado e nao ha estorno automatico: o organizador devolve o dinheiro e registra o estorno.
 */
@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    /** Inscricoes que podem pagar (aprovadas). Pedido em analise e lista de espera ainda nao pagam. */
    static final Set<String> PAYABLE = Set.of("APPROVED", "CONFIRMED", "CHECKED_IN");
    private static final Duration SYNC_INTERVAL = Duration.ofSeconds(15);

    private final PaymentRepository repository;
    private final OperationRepository operations;
    private final MercadoPagoClient gateway;
    private final MercadoPagoProperties properties;
    private final AuditEventRepository audit;
    private final Notifier notifier;
    private final URI webhookUrl;
    private final Clock clock;

    @Autowired
    public PaymentService(PaymentRepository repository, OperationRepository operations, MercadoPagoClient gateway,
                          MercadoPagoProperties properties, AuditEventRepository audit, Notifier notifier,
                          @Value("${app.auth.frontend-base-url:}") String frontendBaseUrl) {
        this(repository, operations, gateway, properties, audit, notifier, webhookUrl(properties, frontendBaseUrl), Clock.systemUTC());
    }

    PaymentService(PaymentRepository repository, OperationRepository operations, MercadoPagoClient gateway, MercadoPagoProperties properties,
                   AuditEventRepository audit, Notifier notifier, URI webhookUrl, Clock clock) {
        this.repository = repository;
        this.operations = operations;
        this.gateway = gateway;
        this.properties = properties;
        this.audit = audit;
        this.notifier = notifier;
        this.webhookUrl = webhookUrl;
        this.clock = clock;
    }

    /** O Mercado Pago so aceita notificacao em endereco publico https. */
    static URI webhookUrl(MercadoPagoProperties properties, String frontendBaseUrl) {
        if (properties.notificationUrl() != null) return properties.notificationUrl();
        if (frontendBaseUrl == null || !frontendBaseUrl.startsWith("https://")) return null;
        return URI.create(frontendBaseUrl.replaceAll("/+$", "") + "/api/payments/mercadopago/webhook");
    }

    @Transactional
    public Mine mine(AuthenticatedUser user, UUID operationId) {
        Context ctx = context(user, operationId);
        if (ctx.participantId() != null) syncPending(ctx.participantId());
        return mineView(context(user, operationId));
    }

    @Transactional
    public Mine createPix(AuthenticatedUser user, UUID operationId) {
        Context ctx = context(user, operationId);
        String blocked = payBlockedReason(ctx);
        if (blocked != null) throw BusinessException.conflict("PAYMENT_NOT_ALLOWED", blocked);
        Instant now = clock.instant();
        Optional<Payment> pending = repository.pending(ctx.participantId(), true);
        if (pending.isPresent()) {
            Payment current = pending.get();
            if ("PIX".equals(current.method()) && current.expiresAt() != null && current.expiresAt().isAfter(now.plusSeconds(60))) {
                return mineView(ctx);
            }
            // Codigo vencido (ou quase): fecha e gera outro. Se o Mercado Pago aprovar depois, o webhook confirma mesmo assim.
            expire(current, now);
        }
        long paymentId;
        try {
            paymentId = repository.insert(ctx.operationId(), ctx.participantId(), user.internalId(), ctx.price(), "PIX", "PENDING", null, null, now);
        } catch (DuplicateKeyException race) {
            return mineView(ctx);
        }
        Payment created = repository.byId(paymentId, true).orElseThrow();
        Instant expiresAt = now.plus(properties.pixExpiration());
        RemotePayment remote;
        try {
            remote = gateway.createPix(new PixRequest(created.publicId().toString(), ctx.price(),
                "Inscrição: " + truncate(ctx.name(), 200), created.publicId().toString(), ctx.payerEmail(), ctx.payerName(), expiresAt, webhookUrl));
        } catch (PaymentGatewayException failure) {
            throw BusinessException.badRequest("PAYMENT_GATEWAY_ERROR", failure.getMessage());
        }
        if (remote.pixCode() == null) throw BusinessException.badRequest("PAYMENT_GATEWAY_ERROR", "O Mercado Pago não gerou o código Pix. Tente novamente.");
        repository.attachPix(paymentId, remote.id(), remote.status(), remote.statusDetail(), remote.pixCode(), remote.pixQrBase64(),
            remote.ticketUrl(), remote.expiresAt() != null ? remote.expiresAt() : expiresAt, now);
        repository.event(paymentId, "PENDING", "PLAYER", user.internalId(), "Pix gerado", now);
        return mineView(context(user, operationId));
    }

    /** Webhook do Mercado Pago: a assinatura ja foi conferida; o status e consultado na API, nunca lido da notificacao. */
    @Transactional
    public void notification(String type, String dataId) {
        if (dataId == null || !("payment".equals(type) || type == null)) return;
        Optional<Payment> local = repository.byProviderId(dataId, true);
        if (local.isEmpty()) return;
        gateway.find(dataId).ifPresent(remote -> apply(local.get(), remote, "WEBHOOK"));
    }

    @Transactional
    public Managed managed(AuthenticatedUser user, UUID operationId) {
        Context ctx = managedContext(user, operationId);
        List<Row> rows = repository.rows(ctx.operationId());
        long unpaid = 0, pending = 0, confirmed = 0, cancelled = 0, refunded = 0;
        BigDecimal total = BigDecimal.ZERO;
        List<ManagedRow> view = new java.util.ArrayList<>();
        for (Row row : rows) {
            String status = rowStatus(row);
            switch (status) {
                case "CONFIRMED" -> { confirmed++; if (row.amount() != null) total = total.add(row.amount()); }
                case "PENDING" -> pending++;
                case "REFUNDED" -> refunded++;
                case "UNPAID" -> unpaid++;
                default -> cancelled++;
            }
            view.add(new ManagedRow(row.operatorId(), row.callsign(), row.participantStatus(), status, row.method(), row.amount(),
                row.paymentUpdatedAt(), row.note()));
        }
        return new Managed(ctx.price(), properties.enabled(), new Summary(unpaid, pending, confirmed, cancelled, refunded, total), view);
    }

    /** Organizador confirma um pagamento recebido fora do Pix do app (dinheiro, Pix direto etc.). */
    @Transactional
    public MessageResponse confirmManually(AuthenticatedUser user, UUID operationId, UUID operatorId, String note) {
        Context ctx = managedContext(user, operationId);
        if (ctx.price() == null || ctx.price().signum() <= 0) throw BusinessException.badRequest("FREE_OPERATION", "Esta operação é gratuita.");
        long[] participant = repository.participantByOperator(ctx.operationId(), operatorId)
            .orElseThrow(() -> BusinessException.notFound("Inscrição não encontrada."));
        Instant now = clock.instant();
        Optional<Payment> pending = repository.pending(participant[0], true);
        if (pending.isPresent() && pending.get().providerPaymentId() != null && properties.enabled()) {
            Optional<RemotePayment> remote = safeFind(pending.get().providerPaymentId());
            if (remote.isPresent() && "CONFIRMED".equals(map(remote.get()))) {
                apply(pending.get(), remote.get(), "SYNC");
                return new MessageResponse("O Pix deste jogador já foi aprovado pelo Mercado Pago.");
            }
        }
        if (repository.participantPaymentConfirmedAt(participant[0]).isPresent()) {
            throw BusinessException.conflict("ALREADY_PAID", "O pagamento deste jogador já está confirmado.");
        }
        pending.ifPresent(p -> cancelPix(p, "ORGANIZER", user.internalId(), "Substituído por confirmação manual", now));
        long id = repository.insert(ctx.operationId(), participant[0], participant[1], ctx.price(), "MANUAL", "CONFIRMED",
            user.internalId(), note.strip(), now);
        repository.event(id, "CONFIRMED", "ORGANIZER", user.internalId(), truncate(note.strip(), 300), now);
        repository.markParticipantPaid(participant[0], now);
        audit.record(user.internalId(), "OPERATION_PAYMENT_CONFIRMED_MANUALLY", "OPERATION", ctx.operationPublicId(), null, now);
        notifier.toUserAt(participant[1], "PAYMENT_CONFIRMED", "Pagamento confirmado", "O organizador confirmou o pagamento da sua inscrição em "
            + ctx.name() + ".", "Operações", "/convocacao/" + ctx.operationPublicId());
        return new MessageResponse("Pagamento confirmado.");
    }

    /** Registra um estorno feito pelo organizador. O sistema nao devolve dinheiro sozinho. */
    @Transactional
    public MessageResponse registerRefund(AuthenticatedUser user, UUID operationId, UUID operatorId, String note) {
        Context ctx = managedContext(user, operationId);
        long[] participant = repository.participantByOperator(ctx.operationId(), operatorId)
            .orElseThrow(() -> BusinessException.notFound("Inscrição não encontrada."));
        Payment confirmed = repository.latestConfirmed(participant[0], true)
            .orElseThrow(() -> BusinessException.conflict("NOT_PAID", "Não há pagamento confirmado para estornar."));
        Instant now = clock.instant();
        repository.updateStatus(confirmed.id(), "REFUNDED", null, null, truncate(note.strip(), 300), now);
        repository.event(confirmed.id(), "REFUNDED", "ORGANIZER", user.internalId(), truncate(note.strip(), 300), now);
        repository.clearParticipantPaidIfNoneLeft(participant[0], now);
        audit.record(user.internalId(), "OPERATION_PAYMENT_REFUNDED", "OPERATION", ctx.operationPublicId(), null, now);
        notifier.toUserAt(participant[1], "PAYMENT_REFUNDED", "Pagamento estornado", "O organizador registrou o estorno do seu pagamento em "
            + ctx.name() + ".", "Operações", "/convocacao/" + ctx.operationPublicId());
        return new MessageResponse("Estorno registrado. A devolução do dinheiro é feita por você (no Mercado Pago ou em mãos).");
    }

    // ---------------------------------------------------------------------------------------------------------------

    private void syncPending(long participantId) {
        if (!properties.enabled()) return;
        Optional<Payment> pending = repository.pending(participantId, true);
        if (pending.isEmpty() || pending.get().providerPaymentId() == null) return;
        Payment payment = pending.get();
        Instant now = clock.instant();
        if (payment.lastSyncedAt() != null && payment.lastSyncedAt().plus(SYNC_INTERVAL).isAfter(now)) return;
        Optional<RemotePayment> remote = safeFind(payment.providerPaymentId());
        if (remote.isPresent()) apply(payment, remote.get(), "SYNC");
        else repository.touchSynced(payment.id(), now);
    }

    /** Aplica o status do Mercado Pago, conferindo referencia e valor. Sempre respeita o que o Mercado Pago diz que foi pago. */
    void apply(Payment payment, RemotePayment remote, String source) {
        Instant now = clock.instant();
        if (!"PIX".equals(payment.method())) return;
        if (!payment.publicId().toString().equals(remote.externalReference())
            || remote.amount() == null || remote.amount().compareTo(payment.amount()) != 0) {
            log.warn("Pagamento {} do Mercado Pago nao corresponde ao registro local; ignorado", remote.id());
            return;
        }
        String status = map(remote);
        if (status == null || status.equals(payment.status())) { repository.touchSynced(payment.id(), now); return; }
        // Pagamento aprovado so sai de CONFIRMED por estorno.
        if ("CONFIRMED".equals(payment.status()) && !"REFUNDED".equals(status)) { repository.touchSynced(payment.id(), now); return; }
        if ("REFUNDED".equals(payment.status())) { repository.touchSynced(payment.id(), now); return; }
        repository.updateStatus(payment.id(), status, remote.status(), remote.statusDetail(), null, now);
        repository.event(payment.id(), status, source, null, remote.statusDetail(), now);
        Context ctx = repository.contextByInternalId(payment.operationId(), payment.userId()).orElse(null);
        String name = ctx == null ? "operação" : ctx.name();
        String path = ctx == null ? "/feed" : "/convocacao/" + ctx.operationPublicId();
        if ("CONFIRMED".equals(status)) {
            repository.markParticipantPaid(payment.participantId(), now);
            notifier.toUserAt(payment.userId(), "PAYMENT_CONFIRMED", "Pagamento confirmado", "Seu Pix da inscrição em " + name
                + " foi aprovado.", "Operações", path);
            if (ctx != null) notifier.toUserAt(ctx.organizerUserId(), "PAYMENT_RECEIVED", "Pix recebido",
                "Um jogador pagou a inscrição em " + name + ".", "Operações", path);
        } else if ("REFUNDED".equals(status)) {
            repository.clearParticipantPaidIfNoneLeft(payment.participantId(), now);
            notifier.toUserAt(payment.userId(), "PAYMENT_REFUNDED", "Pagamento estornado", "O pagamento da inscrição em " + name
                + " foi estornado.", "Operações", path);
        }
    }

    /** approved = pago; cancelled por prazo = expirado; refunded/charged_back = estornado; in_mediation nao muda nada. */
    static String map(RemotePayment remote) {
        if (remote.status() == null) return null;
        return switch (remote.status()) {
            case "approved" -> "CONFIRMED";
            case "pending", "in_process", "authorized" -> "PENDING";
            case "rejected" -> "REJECTED";
            case "cancelled" -> "expired".equals(remote.statusDetail()) ? "EXPIRED" : "CANCELLED";
            case "refunded", "charged_back" -> "REFUNDED";
            default -> null;
        };
    }

    private void expire(Payment payment, Instant now) {
        repository.updateStatus(payment.id(), "EXPIRED", null, null, null, now);
        repository.event(payment.id(), "EXPIRED", "SYNC", null, "Código Pix vencido", now);
        if (payment.providerPaymentId() != null && properties.enabled()) gateway.cancel(payment.providerPaymentId());
    }

    private void cancelPix(Payment payment, String source, long actor, String reason, Instant now) {
        repository.updateStatus(payment.id(), "CANCELLED", null, null, null, now);
        repository.event(payment.id(), "CANCELLED", source, actor, truncate(reason, 300), now);
        if (payment.providerPaymentId() != null && properties.enabled()) gateway.cancel(payment.providerPaymentId());
    }

    private Optional<RemotePayment> safeFind(String providerId) {
        try {
            return gateway.find(providerId);
        } catch (PaymentGatewayException unavailable) {
            return Optional.empty();
        }
    }

    private Mine mineView(Context ctx) {
        BigDecimal price = ctx.price() == null ? BigDecimal.ZERO : ctx.price();
        if (price.signum() <= 0) return new Mine("FREE", price, properties.enabled(), false, null, null, null);
        String blocked = payBlockedReason(ctx);
        if (ctx.participantId() == null) return new Mine("NOT_REGISTERED", price, properties.enabled(), false, blocked, null, null);
        if (ctx.paymentConfirmedAt() != null) return new Mine("CONFIRMED", price, properties.enabled(), false, null, ctx.paymentConfirmedAt(), null);
        Instant now = clock.instant();
        Optional<Payment> latest = repository.latest(ctx.participantId());
        String status = latest.map(Payment::status).orElse("UNPAID");
        PixCharge pix = null;
        if (latest.isPresent() && "PENDING".equals(status)) {
            Payment p = latest.get();
            if (p.expiresAt() != null && p.expiresAt().isBefore(now)) status = "EXPIRED";
            else if (p.pixCode() != null) pix = new PixCharge(p.publicId(), p.amount(), p.pixCode(), p.pixQrBase64(), p.ticketUrl(), p.expiresAt());
        }
        if ("CONFIRMED".equals(status)) status = "UNPAID"; // confirmacao desfeita por estorno de outro registro
        return new Mine(status, price, properties.enabled(), blocked == null && pix == null, blocked, null, pix);
    }

    /** null = pode pagar agora; senao, o motivo para mostrar. */
    private String payBlockedReason(Context ctx) {
        if (ctx.price() == null || ctx.price().signum() <= 0) return "Esta operação é gratuita.";
        if (!properties.enabled()) return "Pagamento online indisponível. Combine o pagamento com o organizador.";
        if (Set.of("CANCELLED", "FINISHED").contains(ctx.status())) return "Esta operação não aceita mais pagamentos.";
        if (ctx.participantId() == null) return "Inscreva-se para pagar.";
        if (ctx.paymentConfirmedAt() != null) return "Seu pagamento já está confirmado.";
        if (!PAYABLE.contains(ctx.participantStatus())) {
            return switch (ctx.participantStatus()) {
                case "REQUESTED" -> "Aguarde a aprovação da sua inscrição para pagar.";
                case "WAITING_LIST" -> "Você está na lista de espera. O pagamento abre quando sua vaga for confirmada.";
                default -> "Sua inscrição não está ativa.";
            };
        }
        return null;
    }

    private static String rowStatus(Row row) {
        if (row.paymentConfirmedAt() != null) return "CONFIRMED";
        if (row.paymentStatus() == null || "CONFIRMED".equals(row.paymentStatus())) return "UNPAID";
        return switch (row.paymentStatus()) {
            case "PENDING" -> "PENDING";
            case "REFUNDED" -> "REFUNDED";
            default -> PAYABLE.contains(row.participantStatus()) ? "UNPAID" : "CANCELLED";
        };
    }

    private Context context(AuthenticatedUser user, UUID operationId) {
        return repository.context(operationId, user.internalId(), false).orElseThrow(() -> BusinessException.notFound("Operação não encontrada."));
    }

    private Context managedContext(AuthenticatedUser user, UUID operationId) {
        Context ctx = context(user, operationId);
        if (ctx.organizerUserId() != user.internalId() && !operations.isOperationAdmin(ctx.operationId(), user.internalId())) {
            throw BusinessException.forbidden("Só o organizador pode ver e registrar pagamentos.");
        }
        return ctx;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
