package br.com.operadorzero.payment;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.notification.Notifier;
import br.com.operadorzero.operation.OperationDtos.MessageResponse;
import br.com.operadorzero.operation.OperationRepository;
import br.com.operadorzero.payment.MercadoPagoClient.PaymentGatewayException;
import br.com.operadorzero.payment.MercadoPagoClient.PixRequest;
import br.com.operadorzero.payment.MercadoPagoClient.Preference;
import br.com.operadorzero.payment.MercadoPagoClient.PreferenceRequest;
import br.com.operadorzero.payment.MercadoPagoClient.RemotePayment;
import br.com.operadorzero.payment.MercadoPagoClient.TokenRejectedException;
import br.com.operadorzero.payment.PaymentDtos.Checkout;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pagamento das inscricoes, com um so historico para todos os meios:
 * <ul>
 *   <li>Checkout Pro do marketplace (Split 1:1): cobranca criada com o token OAuth do organizador; o Mercado Pago tira a tarifa
 *       dele e a comissao da plataforma (marketplace_fee) no proprio pagamento.</li>
 *   <li>Pix direto (Etapa F): conta da plataforma, sem divisao; so aparece quando o organizador nao conectou a conta.</li>
 *   <li>Confirmacao manual do organizador.</li>
 * </ul>
 * O status vem sempre da consulta ao Mercado Pago (nunca do navegador nem do corpo do webhook), conferindo referencia, valor
 * e conta recebedora. O status financeiro e separado do status da inscricao. Nada e apagado e nao ha reembolso automatico.
 */
@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    /** Inscricoes que podem pagar (aprovadas). Pedido em analise e lista de espera ainda nao pagam. */
    static final Set<String> PAYABLE = Set.of("APPROVED", "CONFIRMED", "CHECKED_IN");
    /** Situacoes em que a inscricao conta como paga (libera documentos). */
    static final Set<String> PAID = Set.of("CONFIRMED", "PARTIALLY_REFUNDED");
    private static final Duration SYNC_INTERVAL = Duration.ofSeconds(15);

    private final PaymentRepository repository;
    private final OperationRepository operations;
    private final MercadoPagoClient gateway;
    private final MercadoPagoProperties properties;
    private final MarketplaceService marketplace;
    private final AuditEventRepository audit;
    private final Notifier notifier;
    private final URI webhookUrl;
    private final String frontendBaseUrl;
    private final Clock clock;
    private PaymentService self = this;

    @Autowired
    public PaymentService(PaymentRepository repository, OperationRepository operations, MercadoPagoClient gateway,
                          MercadoPagoProperties properties, MarketplaceService marketplace, AuditEventRepository audit, Notifier notifier,
                          @Value("${app.auth.frontend-base-url:}") String frontendBaseUrl) {
        this(repository, operations, gateway, properties, marketplace, audit, notifier, webhookUrl(properties, frontendBaseUrl),
            frontendBaseUrl, Clock.systemUTC());
    }

    PaymentService(PaymentRepository repository, OperationRepository operations, MercadoPagoClient gateway, MercadoPagoProperties properties,
                   MarketplaceService marketplace, AuditEventRepository audit, Notifier notifier, URI webhookUrl, String frontendBaseUrl,
                   Clock clock) {
        this.repository = repository;
        this.operations = operations;
        this.gateway = gateway;
        this.properties = properties;
        this.marketplace = marketplace;
        this.audit = audit;
        this.notifier = notifier;
        this.webhookUrl = webhookUrl;
        this.frontendBaseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.replaceAll("/+$", "");
        this.clock = clock;
    }

    /** Proxy de si mesmo: cada pagamento da conciliacao roda na propria transacao. */
    @Autowired
    void setSelf(@Lazy PaymentService self) {
        this.self = self;
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

    // ---- Checkout Pro do marketplace ------------------------------------------------------------------------------

    /**
     * Cria (ou reaproveita) a cobranca no Checkout Pro do organizador, com a comissao calculada aqui no servidor. O site so
     * recebe a URL do Mercado Pago; valor e comissao nunca vem do navegador.
     */
    @Transactional
    public Checkout createCheckout(AuthenticatedUser user, UUID operationId) {
        Context ctx = context(user, operationId);
        String blocked = payBlockedReason(ctx);
        if (blocked == null && !marketplace.connected(ctx.organizerUserId())) {
            blocked = "O organizador ainda não conectou a conta Mercado Pago. Combine o pagamento com ele.";
        }
        if (blocked != null) throw BusinessException.conflict("PAYMENT_NOT_ALLOWED", blocked);
        Instant now = clock.instant();
        Optional<Payment> pending = repository.pending(ctx.participantId(), true);
        if (pending.isPresent()) {
            Payment current = pending.get();
            if ("CHECKOUT".equals(current.method()) && current.checkoutUrl() != null && current.expiresAt() != null
                && current.expiresAt().isAfter(now.plus(Duration.ofMinutes(2)))) {
                return new Checkout(current.checkoutUrl());
            }
            // Checkout vencido ou Pix direto aberto: fecha e cria outro. Se o anterior for pago depois, o webhook confirma mesmo assim.
            if ("PIX".equals(current.method())) cancelPix(current, "PLAYER", user.internalId(), "Trocado pelo pagamento no Mercado Pago", now);
            else expire(current, now);
        }
        BigDecimal percent = marketplace.commissionPercent(ctx.organizerUserId());
        BigDecimal fee = MarketplaceService.commissionAmount(ctx.price(), percent);
        Long collector = marketplace.accountId(ctx.organizerUserId()).orElse(null);
        long paymentId;
        try {
            paymentId = repository.insertCheckout(ctx.operationId(), ctx.participantId(), user.internalId(), ctx.price(), percent, fee, collector, now);
        } catch (DuplicateKeyException race) {
            throw BusinessException.conflict("PAYMENT_IN_PROGRESS", "Já existe um pagamento em andamento. Atualize a página.");
        }
        Payment created = repository.byId(paymentId, true).orElseThrow();
        Instant expiresAt = now.plus(properties.checkoutExpiration());
        Preference preference;
        try {
            String token = marketplace.sellerToken(ctx.organizerUserId());
            preference = gateway.createPreference(token, new PreferenceRequest(created.publicId().toString(), ctx.operationPublicId().toString(),
                "Inscrição: " + truncate(ctx.name(), 200), ctx.price(), fee, created.publicId().toString(), webhookUrl,
                URI.create(frontendBaseUrl + "/convocacao/" + ctx.operationPublicId() + "?pagamento=retorno"), expiresAt,
                ctx.operationPublicId().toString(), created.publicId().toString()));
        } catch (TokenRejectedException rejected) {
            marketplace.tokenRejected(ctx.organizerUserId());
            throw BusinessException.badRequest("PAYMENT_GATEWAY_ERROR", "O organizador precisa reconectar a conta Mercado Pago.");
        } catch (PaymentGatewayException failure) {
            throw BusinessException.badRequest("PAYMENT_GATEWAY_ERROR", failure.getMessage());
        }
        String url = properties.testEnvironment() && preference.sandboxInitPoint() != null ? preference.sandboxInitPoint() : preference.initPoint();
        if (url == null || !url.startsWith("https://") && !properties.testEnvironment()) {
            throw BusinessException.badRequest("PAYMENT_GATEWAY_ERROR", "O Mercado Pago não devolveu o endereço de pagamento. Tente novamente.");
        }
        repository.attachCheckout(paymentId, preference.id(), url, expiresAt, now);
        repository.event(paymentId, "PENDING", "PLAYER", user.internalId(), "Checkout criado (comissão " + percent.stripTrailingZeros().toPlainString()
            + "% = R$ " + fee.toPlainString() + ")", now);
        return new Checkout(url);
    }

    // ---- Pix direto (Etapa F) --------------------------------------------------------------------------------------

    @Transactional
    public Mine createPix(AuthenticatedUser user, UUID operationId) {
        Context ctx = context(user, operationId);
        String blocked = payBlockedReason(ctx);
        if (blocked == null && marketplace.connected(ctx.organizerUserId())) {
            blocked = "Esta operação recebe pelo Mercado Pago do organizador. Use \"Pagar com Mercado Pago\".";
        }
        if (blocked == null && !properties.enabled()) blocked = "Pagamento online indisponível. Combine o pagamento com o organizador.";
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

    // ---- Notificacoes, consulta e conciliacao ----------------------------------------------------------------------

    /**
     * Webhook do Mercado Pago (assinatura ja conferida). O status e consultado na API. Pagamento ainda desconhecido so e
     * consultado quando a notificacao vem de uma conta de organizador conectada (sem amplificar chamadas falsas).
     * Devolve o id local do pagamento afetado, se houver.
     */
    @Transactional
    public Optional<Long> notification(String type, String dataId, Long mpUserId) {
        if (dataId == null || !("payment".equals(type) || type == null)) return Optional.empty();
        Optional<Payment> local = repository.byProviderId(dataId, true);
        if (local.isPresent()) {
            remoteOf(local.get()).ifPresent(remote -> apply(local.get(), remote, "WEBHOOK"));
            return Optional.of(local.get().id());
        }
        if (mpUserId == null) return Optional.empty();
        Optional<Long> organizer = marketplace.organizerByAccount(mpUserId);
        if (organizer.isEmpty()) return Optional.empty();
        Optional<RemotePayment> remote = withSellerToken(organizer.get(), token -> gateway.find(token, dataId));
        if (remote.isEmpty() || remote.get().externalReference() == null) return Optional.empty();
        UUID reference;
        try { reference = UUID.fromString(remote.get().externalReference()); } catch (IllegalArgumentException foreign) { return Optional.empty(); }
        Optional<Payment> byReference = repository.byPublicId(reference, true)
            .filter(p -> p.marketplace() && organizer.get().equals(p.organizerUserId()));
        if (byReference.isEmpty()) return Optional.empty();
        repository.attachProviderId(byReference.get().id(), remote.get().id(), clock.instant());
        apply(byReference.get(), remote.get(), "WEBHOOK");
        return Optional.of(byReference.get().id());
    }

    /** Recupera notificacoes perdidas: confere pendentes e busca tarifas que ainda nao chegaram. */
    @Scheduled(initialDelayString = "PT3M", fixedDelayString = "PT10M")
    public void reconcile() {
        if (!properties.anyEnabled()) return;
        Instant now = clock.instant();
        for (Payment payment : repository.toReconcile(now.minus(Duration.ofMinutes(2)), now.minus(Duration.ofDays(30)), 50)) {
            try {
                self.reconcileOne(payment.id());
            } catch (RuntimeException failure) {
                log.warn("Conciliacao do pagamento {} falhou: {}", payment.publicId(), failure.getClass().getSimpleName());
            }
        }
    }

    @Transactional
    public void reconcileOne(long paymentId) {
        Payment payment = repository.byId(paymentId, true).orElse(null);
        if (payment == null) return;
        Instant now = clock.instant();
        Optional<RemotePayment> remote = remoteOf(payment);
        if (remote.isPresent()) {
            if (payment.providerPaymentId() == null) repository.attachProviderId(payment.id(), remote.get().id(), now);
            apply(payment, remote.get(), "RECONCILE");
        } else {
            repository.touchSynced(payment.id(), now);
            // Checkout vencido sem pagamento no Mercado Pago: encerra. Se aparecer depois, o webhook confirma.
            if ("PENDING".equals(payment.status()) && "CHECKOUT".equals(payment.method()) && payment.expiresAt() != null
                && payment.expiresAt().plus(Duration.ofHours(1)).isBefore(now)) {
                repository.updateStatus(payment.id(), "EXPIRED", null, null, null, now);
                repository.event(payment.id(), "EXPIRED", "RECONCILE", null, "Checkout vencido sem pagamento", now);
            }
        }
    }

    @Transactional
    public Managed managed(AuthenticatedUser user, UUID operationId) {
        Context ctx = managedContext(user, operationId);
        List<Row> rows = repository.rows(ctx.operationId());
        long unpaid = 0, pending = 0, confirmed = 0, cancelled = 0, refunded = 0;
        BigDecimal total = BigDecimal.ZERO, fees = BigDecimal.ZERO, commission = BigDecimal.ZERO, net = BigDecimal.ZERO, refundedTotal = BigDecimal.ZERO;
        boolean estimated = false;
        List<ManagedRow> view = new ArrayList<>();
        for (Row row : rows) {
            String status = rowStatus(row);
            switch (status) {
                case "CONFIRMED", "PARTIALLY_REFUNDED" -> {
                    confirmed++;
                    if (row.amount() != null) total = total.add(row.amount());
                    if (row.commission() != null) commission = commission.add(row.commission());
                    if (row.providerFee() != null) fees = fees.add(row.providerFee());
                    if (row.net() != null) net = net.add(row.net());
                    else if (row.amount() != null) net = net.add(row.amount().subtract(row.commission() == null ? BigDecimal.ZERO : row.commission()));
                    if ("MERCADO_PAGO_MARKETPLACE".equals(row.provider()) && !row.feesConfirmed()) estimated = true;
                }
                case "PENDING" -> pending++;
                case "REFUNDED", "CHARGEBACK" -> refunded++;
                case "UNPAID" -> unpaid++;
                default -> cancelled++;
            }
            if (row.refunded() != null) refundedTotal = refundedTotal.add(row.refunded());
            boolean refundable = "MERCADO_PAGO_MARKETPLACE".equals(row.provider()) && PAID.contains(status) && row.providerPaymentId() != null;
            view.add(new ManagedRow(row.operatorId(), row.callsign(), row.participantStatus(), status, row.method(), row.amount(),
                row.paymentUpdatedAt(), row.note(), row.provider(), row.paymentType(), row.commission(), row.providerFee(), row.refunded(),
                row.net(), row.feesConfirmed(), row.providerPaymentId(), refundable));
        }
        boolean connected = marketplace.connected(ctx.organizerUserId());
        return new Managed(ctx.price(), properties.enabled() || connected,
            new Summary(unpaid, pending, confirmed, cancelled, refunded, total, fees, commission, net, refundedTotal, estimated), view,
            connected, connected ? marketplace.commissionPercent(ctx.organizerUserId()) : null);
    }

    /** Organizador confirma um pagamento recebido fora do app (dinheiro, Pix direto etc.). */
    @Transactional
    public MessageResponse confirmManually(AuthenticatedUser user, UUID operationId, UUID operatorId, String note) {
        Context ctx = managedContext(user, operationId);
        if (ctx.price() == null || ctx.price().signum() <= 0) throw BusinessException.badRequest("FREE_OPERATION", "Esta operação é gratuita.");
        long[] participant = repository.participantByOperator(ctx.operationId(), operatorId)
            .orElseThrow(() -> BusinessException.notFound("Inscrição não encontrada."));
        Instant now = clock.instant();
        Optional<Payment> pending = repository.pending(participant[0], true);
        if (pending.isPresent()) {
            Optional<RemotePayment> remote = safeRemote(pending.get());
            if (remote.isPresent() && "CONFIRMED".equals(map(remote.get()))) {
                if (pending.get().providerPaymentId() == null) repository.attachProviderId(pending.get().id(), remote.get().id(), now);
                apply(pending.get(), remote.get(), "SYNC");
                return new MessageResponse("O pagamento deste jogador já foi aprovado pelo Mercado Pago.");
            }
        }
        if (repository.participantPaymentConfirmedAt(participant[0]).isPresent()) {
            throw BusinessException.conflict("ALREADY_PAID", "O pagamento deste jogador já está confirmado.");
        }
        pending.ifPresent(p -> {
            if ("PIX".equals(p.method())) cancelPix(p, "ORGANIZER", user.internalId(), "Substituído por confirmação manual", now);
            else {
                repository.updateStatus(p.id(), "CANCELLED", null, null, null, now);
                repository.event(p.id(), "CANCELLED", "ORGANIZER", user.internalId(), "Substituído por confirmação manual", now);
            }
        });
        long id = repository.insert(ctx.operationId(), participant[0], participant[1], ctx.price(), "MANUAL", "CONFIRMED",
            user.internalId(), note.strip(), now);
        repository.event(id, "CONFIRMED", "ORGANIZER", user.internalId(), truncate(note.strip(), 300), now);
        repository.markParticipantPaid(participant[0], now);
        audit.record(user.internalId(), "OPERATION_PAYMENT_CONFIRMED_MANUALLY", "OPERATION", ctx.operationPublicId(), null, now);
        notifier.toUserAt(participant[1], "PAYMENT_CONFIRMED", "Pagamento confirmado", "O organizador confirmou o pagamento da sua inscrição em "
            + ctx.name() + ".", "Operações", "/convocacao/" + ctx.operationPublicId());
        return new MessageResponse("Pagamento confirmado.");
    }

    /** Registra um estorno feito por fora (pagamento manual ou Pix direto). O sistema nao devolve dinheiro sozinho. */
    @Transactional
    public MessageResponse registerRefund(AuthenticatedUser user, UUID operationId, UUID operatorId, String note) {
        Context ctx = managedContext(user, operationId);
        long[] participant = repository.participantByOperator(ctx.operationId(), operatorId)
            .orElseThrow(() -> BusinessException.notFound("Inscrição não encontrada."));
        Payment confirmed = repository.latestConfirmed(participant[0], true)
            .orElseThrow(() -> BusinessException.conflict("NOT_PAID", "Não há pagamento confirmado para estornar."));
        if (confirmed.marketplace()) {
            throw BusinessException.conflict("USE_MERCADO_PAGO_REFUND", "Este pagamento foi feito no Mercado Pago do organizador: use \"Reembolsar pelo Mercado Pago\".");
        }
        Instant now = clock.instant();
        repository.updateStatus(confirmed.id(), "REFUNDED", null, null, truncate(note.strip(), 300), now);
        repository.event(confirmed.id(), "REFUNDED", "ORGANIZER", user.internalId(), truncate(note.strip(), 300), now);
        repository.clearParticipantPaidIfNoneLeft(participant[0], now);
        audit.record(user.internalId(), "OPERATION_PAYMENT_REFUNDED", "OPERATION", ctx.operationPublicId(), null, now);
        notifier.toUserAt(participant[1], "PAYMENT_REFUNDED", "Pagamento estornado", "O organizador registrou o estorno do seu pagamento em "
            + ctx.name() + ".", "Operações", "/convocacao/" + ctx.operationPublicId());
        return new MessageResponse("Estorno registrado. A devolução do dinheiro é feita por você (no Mercado Pago ou em mãos).");
    }

    /**
     * Reembolso de um pagamento do marketplace, pedido pelo organizador (nunca automatico). O Mercado Pago debita o organizador
     * e a plataforma na proporcao de cada um; sem saldo, ele recusa e nada muda aqui.
     */
    @Transactional
    public MessageResponse refundViaMercadoPago(AuthenticatedUser user, UUID operationId, UUID operatorId, BigDecimal amount, String note) {
        Context ctx = managedContext(user, operationId);
        long[] participant = repository.participantByOperator(ctx.operationId(), operatorId)
            .orElseThrow(() -> BusinessException.notFound("Inscrição não encontrada."));
        Payment paid = repository.latestConfirmed(participant[0], true)
            .filter(p -> p.marketplace() && p.providerPaymentId() != null)
            .orElseThrow(() -> BusinessException.conflict("NOT_REFUNDABLE", "Não há pagamento pelo Mercado Pago para reembolsar."));
        BigDecimal alreadyRefunded = paid.refundedAmount() == null ? BigDecimal.ZERO : paid.refundedAmount();
        BigDecimal available = paid.amount().subtract(alreadyRefunded);
        if (amount != null && amount.compareTo(available) > 0) {
            throw BusinessException.badRequest("INVALID_REFUND_AMOUNT", "O valor passa do que ainda pode ser devolvido (R$ " + available.toPlainString() + ").");
        }
        Instant now = clock.instant();
        try {
            withSellerToken(paid.organizerUserId(), token -> {
                gateway.refund(token, paid.providerPaymentId(), amount, UUID.randomUUID().toString());
                return Optional.empty();
            });
        } catch (PaymentGatewayException refused) {
            throw BusinessException.badRequest("REFUND_REFUSED", "O Mercado Pago não aceitou o reembolso agora (saldo insuficiente, prazo vencido ou "
                + "indisponibilidade). Confira no painel do Mercado Pago.");
        }
        repository.event(paid.id(), paid.status(), "ORGANIZER", user.internalId(), truncate("Reembolso pedido"
            + (amount == null ? " (total)" : " de R$ " + amount.toPlainString()) + ": " + note.strip(), 300), now);
        audit.record(user.internalId(), "OPERATION_PAYMENT_REFUND_REQUESTED", "OPERATION", ctx.operationPublicId(), null, now);
        remoteOf(paid).ifPresent(remote -> apply(paid, remote, "SYNC"));
        return new MessageResponse("Reembolso enviado ao Mercado Pago. O valor volta ao jogador pelo mesmo meio de pagamento.");
    }

    // ---------------------------------------------------------------------------------------------------------------

    private void syncPending(long participantId) {
        Optional<Payment> pending = repository.pending(participantId, true);
        if (pending.isEmpty()) return;
        Payment payment = pending.get();
        if ("MANUAL".equals(payment.method())) return;
        Instant now = clock.instant();
        if (payment.lastSyncedAt() != null && payment.lastSyncedAt().plus(SYNC_INTERVAL).isAfter(now)) return;
        Optional<RemotePayment> remote = safeRemote(payment);
        if (remote.isPresent()) {
            if (payment.providerPaymentId() == null) repository.attachProviderId(payment.id(), remote.get().id(), now);
            apply(payment, remote.get(), "SYNC");
        } else {
            repository.touchSynced(payment.id(), now);
        }
    }

    /** Consulta o pagamento no Mercado Pago com o token certo (plataforma no Pix direto, organizador no marketplace). */
    private Optional<RemotePayment> remoteOf(Payment payment) {
        if (payment.marketplace()) {
            if (payment.organizerUserId() == null) return Optional.empty();
            return withSellerToken(payment.organizerUserId(), token -> payment.providerPaymentId() != null
                ? gateway.find(token, payment.providerPaymentId())
                : gateway.findByReference(token, payment.publicId().toString()));
        }
        if (payment.providerPaymentId() == null || !properties.enabled()) return Optional.empty();
        return gateway.find(payment.providerPaymentId());
    }

    private Optional<RemotePayment> safeRemote(Payment payment) {
        try {
            return remoteOf(payment);
        } catch (PaymentGatewayException unavailable) {
            return Optional.empty();
        }
    }

    private <T> Optional<T> withSellerToken(long organizerUserId, java.util.function.Function<String, Optional<T>> call) {
        String token = marketplace.sellerToken(organizerUserId);
        try {
            return call.apply(token);
        } catch (TokenRejectedException rejected) {
            marketplace.tokenRejected(organizerUserId);
            throw rejected;
        }
    }

    /**
     * Aplica o que o Mercado Pago informa, conferindo referencia, valor e (no marketplace) a conta que recebeu. Sempre respeita
     * o que o Mercado Pago diz que foi pago, mesmo depois de vencido. Idempotente: repetir nao muda nada.
     */
    void apply(Payment payment, RemotePayment remote, String source) {
        Instant now = clock.instant();
        if ("MANUAL".equals(payment.method())) return;
        if (!payment.publicId().toString().equals(remote.externalReference())
            || remote.amount() == null || remote.amount().compareTo(payment.amount()) != 0) {
            log.warn("Pagamento {} do Mercado Pago nao corresponde ao registro local; ignorado", remote.id());
            return;
        }
        if (payment.marketplace() && payment.collectorId() != null && remote.collectorId() != null
            && !payment.collectorId().equals(remote.collectorId())) {
            log.warn("Pagamento {} caiu em outra conta Mercado Pago; ignorado", remote.id());
            return;
        }
        if (payment.marketplace()) recordFinancials(payment, remote, now);
        String status = map(remote);
        if (status == null || status.equals(payment.status()) || !allowed(payment.status(), status)) {
            repository.touchSynced(payment.id(), now);
            return;
        }
        repository.updateStatus(payment.id(), status, remote.status(), remote.statusDetail(), null, now);
        repository.event(payment.id(), status, source, null, remote.statusDetail(), now);
        Context ctx = repository.contextByInternalId(payment.operationId(), payment.userId()).orElse(null);
        String name = ctx == null ? "operação" : ctx.name();
        String path = ctx == null ? "/feed" : "/convocacao/" + ctx.operationPublicId();
        if ("CONFIRMED".equals(status)) {
            repository.markParticipantPaid(payment.participantId(), now);
            notifier.toUserAt(payment.userId(), "PAYMENT_CONFIRMED", "Pagamento confirmado", "Seu pagamento da inscrição em " + name
                + " foi aprovado.", "Operações", path);
            if (ctx != null) notifier.toUserAt(ctx.organizerUserId(), "PAYMENT_RECEIVED", "Pagamento recebido",
                "Um jogador pagou a inscrição em " + name + ".", "Operações", path);
        } else if ("REFUNDED".equals(status) || "CHARGEBACK".equals(status)) {
            repository.clearParticipantPaidIfNoneLeft(payment.participantId(), now);
            notifier.toUserAt(payment.userId(), "PAYMENT_REFUNDED", "CHARGEBACK".equals(status) ? "Pagamento contestado" : "Pagamento estornado",
                "O pagamento da inscrição em " + name + (("CHARGEBACK".equals(status)) ? " foi contestado." : " foi estornado."), "Operações", path);
            if (ctx != null && "CHARGEBACK".equals(status)) notifier.toUserAt(ctx.organizerUserId(), "PAYMENT_CHARGEBACK", "Contestação de pagamento",
                "Um pagamento da inscrição em " + name + " foi contestado no cartão. Confira no Mercado Pago.", "Operações", path);
        }
    }

    /** Tarifa e comissao efetivas (fee_details) e liquido do organizador; marcadas como confirmadas quando o pagamento foi aprovado. */
    private void recordFinancials(Payment payment, RemotePayment remote, Instant now) {
        boolean approved = "approved".equals(remote.status()) || "refunded".equals(remote.status()) || "charged_back".equals(remote.status());
        BigDecimal commission = remote.feesKnown() && remote.applicationFee() != null && remote.applicationFee().signum() > 0
            ? remote.applicationFee() : payment.commissionAmount();
        BigDecimal fee = remote.feesKnown() ? remote.providerFee() : null;
        BigDecimal refunded = remote.refunded() == null ? BigDecimal.ZERO : remote.refunded();
        BigDecimal net = null;
        if (fee != null && commission != null) net = payment.amount().subtract(fee).subtract(commission);
        repository.updateFinancials(payment.id(), remote.paymentTypeId(), remote.collectorId(), fee, commission, refunded, net,
            approved && remote.feesKnown(), now);
    }

    /** Transicoes aceitas: pago so sai por estorno ou contestacao; estornado e contestado sao finais. */
    static boolean allowed(String from, String to) {
        return switch (from) {
            case "CONFIRMED" -> Set.of("REFUNDED", "PARTIALLY_REFUNDED", "CHARGEBACK").contains(to);
            case "PARTIALLY_REFUNDED" -> Set.of("REFUNDED", "CHARGEBACK").contains(to);
            case "REFUNDED", "CHARGEBACK" -> false;
            default -> true;
        };
    }

    /**
     * approved = pago (approved + partially_refunded = estorno parcial); cancelled por prazo = expirado; refunded = estornado;
     * charged_back = contestacao; in_mediation (disputa aberta) nao muda nada.
     */
    static String map(RemotePayment remote) {
        if (remote.status() == null) return null;
        return switch (remote.status()) {
            case "approved" -> "partially_refunded".equals(remote.statusDetail()) ? "PARTIALLY_REFUNDED" : "CONFIRMED";
            case "pending", "in_process", "authorized" -> "PENDING";
            case "rejected" -> "REJECTED";
            case "cancelled" -> "expired".equals(remote.statusDetail()) ? "EXPIRED" : "CANCELLED";
            case "refunded" -> "REFUNDED";
            case "charged_back" -> "CHARGEBACK";
            default -> null;
        };
    }

    private void expire(Payment payment, Instant now) {
        repository.updateStatus(payment.id(), "EXPIRED", null, null, null, now);
        repository.event(payment.id(), "EXPIRED", "SYNC", null, "PIX".equals(payment.method()) ? "Código Pix vencido" : "Checkout vencido", now);
        if ("PIX".equals(payment.method()) && payment.providerPaymentId() != null && properties.enabled()) gateway.cancel(payment.providerPaymentId());
    }

    private void cancelPix(Payment payment, String source, long actor, String reason, Instant now) {
        repository.updateStatus(payment.id(), "CANCELLED", null, null, null, now);
        repository.event(payment.id(), "CANCELLED", source, actor, truncate(reason, 300), now);
        if (payment.providerPaymentId() != null && properties.enabled()) gateway.cancel(payment.providerPaymentId());
    }

    private Mine mineView(Context ctx) {
        BigDecimal price = ctx.price() == null ? BigDecimal.ZERO : ctx.price();
        boolean checkout = price.signum() > 0 && marketplace.connected(ctx.organizerUserId());
        boolean pixDirect = price.signum() > 0 && !checkout && properties.enabled();
        boolean online = checkout || pixDirect;
        if (price.signum() <= 0) return new Mine("FREE", price, online, false, null, null, null, false, false, null, null, null);
        String blocked = payBlockedReason(ctx);
        if (blocked == null && !online) blocked = "Pagamento online indisponível. Combine o pagamento com o organizador.";
        if (ctx.participantId() == null) return new Mine("NOT_REGISTERED", price, online, false, blocked, null, null, checkout, pixDirect, null, null, null);
        Optional<Payment> latest = repository.latest(ctx.participantId());
        if (ctx.paymentConfirmedAt() != null) {
            String status = latest.map(Payment::status).filter("PARTIALLY_REFUNDED"::equals).orElse("CONFIRMED");
            return new Mine(status, price, online, false, null, ctx.paymentConfirmedAt(), null, checkout, pixDirect, null, null,
                repository.latestConfirmed(ctx.participantId(), false).map(Payment::providerPaymentId).orElse(null));
        }
        Instant now = clock.instant();
        String status = latest.map(Payment::status).orElse("UNPAID");
        PixCharge pix = null;
        String pendingMethod = null;
        String checkoutUrl = null;
        if (latest.isPresent() && "PENDING".equals(status)) {
            Payment p = latest.get();
            pendingMethod = p.method();
            if ("PIX".equals(p.method())) {
                if (p.expiresAt() != null && p.expiresAt().isBefore(now)) { status = "EXPIRED"; pendingMethod = null; }
                else if (p.pixCode() != null) pix = new PixCharge(p.publicId(), p.amount(), p.pixCode(), p.pixQrBase64(), p.ticketUrl(), p.expiresAt());
            } else if (p.checkoutUrl() != null && p.expiresAt() != null && p.expiresAt().isAfter(now)) {
                checkoutUrl = p.checkoutUrl();
            }
        }
        if (PAID.contains(status)) status = "UNPAID"; // confirmacao desfeita por estorno de outro registro
        return new Mine(status, price, online, blocked == null && pix == null, blocked, null, pix, checkout, pixDirect, pendingMethod,
            checkoutUrl, null);
    }

    /** null = a inscricao pode pagar agora (falta conferir se ha meio de pagamento); senao, o motivo para mostrar. */
    private String payBlockedReason(Context ctx) {
        if (ctx.price() == null || ctx.price().signum() <= 0) return "Esta operação é gratuita.";
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
        if (row.paymentConfirmedAt() != null) return "PARTIALLY_REFUNDED".equals(row.paymentStatus()) ? "PARTIALLY_REFUNDED" : "CONFIRMED";
        if (row.paymentStatus() == null || PAID.contains(row.paymentStatus())) return "UNPAID";
        return switch (row.paymentStatus()) {
            case "PENDING" -> "PENDING";
            case "REFUNDED" -> "REFUNDED";
            case "CHARGEBACK" -> "CHARGEBACK";
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
