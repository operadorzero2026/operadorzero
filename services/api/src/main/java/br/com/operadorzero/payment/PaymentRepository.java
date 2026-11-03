package br.com.operadorzero.payment;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository {
    private static final String STARTS_AT = "((o.operation_date + o.start_time) AT TIME ZONE 'America/Sao_Paulo')";

    /** Operacao e, quando houver, a inscricao de quem pede. */
    public record Context(long operationId, UUID operationPublicId, long organizerUserId, String name, BigDecimal price, String status,
                          Instant startsAt, Long participantId, String participantStatus, Instant paymentConfirmedAt,
                          String payerEmail, String payerName) {}

    /**
     * provider: MERCADO_PAGO_DIRECT (Pix na conta da plataforma, sem divisao), MERCADO_PAGO_MARKETPLACE (Checkout Pro na conta do
     * organizador, com comissao) ou ORGANIZER (confirmacao manual). commissionAmount e o marketplace_fee enviado;
     * commissionEffective, providerFee e refundedAmount vem do Mercado Pago (feesConfirmed).
     */
    public record Payment(long id, UUID publicId, long operationId, long participantId, long userId, BigDecimal amount, String method,
                          String status, String providerPaymentId, String providerStatusDetail, String pixCode, String pixQrBase64,
                          String ticketUrl, Instant expiresAt, Instant confirmedAt, Instant refundedAt, String note,
                          Instant lastSyncedAt, Instant createdAt, String provider, Long organizerUserId, Long collectorId,
                          String checkoutUrl, String paymentType, BigDecimal commissionPercent, BigDecimal commissionAmount,
                          BigDecimal commissionEffective, BigDecimal providerFee, BigDecimal refundedAmount, boolean feesConfirmed) {
        /** Forma da Etapa F (Pix direto). */
        public Payment(long id, UUID publicId, long operationId, long participantId, long userId, BigDecimal amount, String method,
                       String status, String providerPaymentId, String providerStatusDetail, String pixCode, String pixQrBase64,
                       String ticketUrl, Instant expiresAt, Instant confirmedAt, Instant refundedAt, String note,
                       Instant lastSyncedAt, Instant createdAt) {
            this(id, publicId, operationId, participantId, userId, amount, method, status, providerPaymentId, providerStatusDetail, pixCode,
                pixQrBase64, ticketUrl, expiresAt, confirmedAt, refundedAt, note, lastSyncedAt, createdAt,
                "MANUAL".equals(method) ? "ORGANIZER" : "CHECKOUT".equals(method) ? "MERCADO_PAGO_MARKETPLACE" : "MERCADO_PAGO_DIRECT",
                null, null, null, null, null, null, null, null, null, false);
        }

        public boolean marketplace() {
            return "MERCADO_PAGO_MARKETPLACE".equals(provider);
        }
    }

    /** Linha do painel financeiro do organizador. */
    public record Row(long participantId, long userId, UUID operatorId, String callsign, String participantStatus, Instant paymentConfirmedAt,
                      UUID paymentId, String paymentStatus, String method, BigDecimal amount, Instant paymentUpdatedAt, String note,
                      String provider, String paymentType, BigDecimal commission, BigDecimal providerFee, BigDecimal refunded,
                      BigDecimal net, boolean feesConfirmed, String providerPaymentId) {}

    private final NamedParameterJdbcTemplate jdbc;

    public PaymentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Context> context(UUID operationId, long userId, boolean lock) {
        return context("o.public_id = :id", operationId, userId, lock);
    }

    /** Mesmo contexto pelo id interno (usado ao aplicar o status vindo do Mercado Pago). */
    public Optional<Context> contextByInternalId(long operationId, long userId) {
        return context("o.id = :id", operationId, userId, false);
    }

    private Optional<Context> context(String where, Object operationId, long userId, boolean lock) {
        return jdbc.query("""
            SELECT o.id, o.public_id, o.organizer_user_id, o.name, o.registration_price, o.status,
            """ + STARTS_AT + """
             starts_at, p.id participant_id, p.status participant_status, p.payment_confirmed_at,
                   u.email, prof.display_name
            FROM airsoft_operation o
            JOIN app_user u ON u.id = :user
            LEFT JOIN operator_profile prof ON prof.user_id = u.id
            LEFT JOIN operation_participant p ON p.operation_id = o.id AND p.user_id = :user
            WHERE
            """ + " " + where + """
             AND o.deleted_at IS NULL AND (o.status <> 'DRAFT' OR o.organizer_user_id = :user)
            """ + (lock ? " FOR UPDATE OF o" : ""), Map.of("id", operationId, "user", userId), (r, i) -> new Context(
                r.getLong("id"), r.getObject("public_id", UUID.class), r.getLong("organizer_user_id"), r.getString("name"),
                r.getBigDecimal("registration_price"), r.getString("status"), instant(r, "starts_at"),
                (Long) r.getObject("participant_id"), r.getString("participant_status"), instant(r, "payment_confirmed_at"),
                r.getString("email"), r.getString("display_name"))).stream().findFirst();
    }

    /** Inscricao de um operador (pelo id publico do perfil) nesta operacao: [participantId, userId]. */
    public Optional<long[]> participantByOperator(long operationId, UUID operatorId) {
        return jdbc.query("""
            SELECT p.id, p.user_id FROM operation_participant p JOIN operator_profile prof ON prof.user_id = p.user_id
            WHERE p.operation_id = :operation AND prof.public_id = :operator FOR UPDATE OF p
            """, Map.of("operation", operationId, "operator", operatorId), (r, i) -> new long[] {r.getLong(1), r.getLong(2)}).stream().findFirst();
    }

    public Optional<Instant> participantPaymentConfirmedAt(long participantId) {
        return jdbc.query("SELECT payment_confirmed_at FROM operation_participant WHERE id = :id",
            Map.of("id", participantId), (r, i) -> Optional.ofNullable(instant(r, "payment_confirmed_at"))).stream().findFirst().flatMap(x -> x);
    }

    public Optional<Payment> latest(long participantId) {
        return jdbc.query("SELECT * FROM operation_payment WHERE participant_id = :id ORDER BY created_at DESC, id DESC LIMIT 1",
            Map.of("id", participantId), (r, i) -> payment(r)).stream().findFirst();
    }

    public Optional<Payment> pending(long participantId, boolean lock) {
        return jdbc.query("SELECT * FROM operation_payment WHERE participant_id = :id AND status = 'PENDING'" + (lock ? " FOR UPDATE" : ""),
            Map.of("id", participantId), (r, i) -> payment(r)).stream().findFirst();
    }

    public Optional<Payment> latestConfirmed(long participantId, boolean lock) {
        return jdbc.query("SELECT * FROM operation_payment WHERE participant_id = :id AND status IN ('CONFIRMED', 'PARTIALLY_REFUNDED') ORDER BY confirmed_at DESC, id DESC LIMIT 1"
                + (lock ? " FOR UPDATE" : ""), Map.of("id", participantId), (r, i) -> payment(r)).stream().findFirst();
    }

    public Optional<Payment> byProviderId(String providerPaymentId, boolean lock) {
        return jdbc.query("SELECT * FROM operation_payment WHERE provider_payment_id = :id" + (lock ? " FOR UPDATE" : ""),
            Map.of("id", providerPaymentId), (r, i) -> payment(r)).stream().findFirst();
    }

    public Optional<Payment> byId(long id, boolean lock) {
        return jdbc.query("SELECT * FROM operation_payment WHERE id = :id" + (lock ? " FOR UPDATE" : ""),
            Map.of("id", id), (r, i) -> payment(r)).stream().findFirst();
    }

    /** Pix direto ou confirmacao manual (Etapa F); o organizador vem da operacao. */
    public long insert(long operationId, long participantId, long userId, BigDecimal amount, String method, String status,
                       Long recordedBy, String note, Instant now) {
        return insert(operationId, participantId, userId, amount, method, status, recordedBy, note,
            "MANUAL".equals(method) ? "ORGANIZER" : "MERCADO_PAGO_DIRECT", null, null, null, now);
    }

    /** Checkout Pro do marketplace: guarda a comissao aplicada e a conta que vai receber. */
    public long insertCheckout(long operationId, long participantId, long userId, BigDecimal amount, BigDecimal commissionPercent,
                               BigDecimal commissionAmount, Long collectorId, Instant now) {
        return insert(operationId, participantId, userId, amount, "CHECKOUT", "PENDING", null, null, "MERCADO_PAGO_MARKETPLACE",
            commissionPercent, commissionAmount, collectorId, now);
    }

    private long insert(long operationId, long participantId, long userId, BigDecimal amount, String method, String status, Long recordedBy,
                        String note, String provider, BigDecimal commissionPercent, BigDecimal commissionAmount, Long collectorId, Instant now) {
        return jdbc.queryForObject("""
            INSERT INTO operation_payment(operation_id, participant_id, user_id, amount, method, status, confirmed_at,
                                          recorded_by_user_id, note, provider, organizer_user_id, commission_percent, commission_amount,
                                          collector_id, created_at, updated_at)
            VALUES (:operation, :participant, :user, :amount, :method, :status,
                    CASE WHEN :status = 'CONFIRMED' THEN CAST(:now AS timestamptz) END, :recordedBy, :note, :provider,
                    (SELECT organizer_user_id FROM airsoft_operation WHERE id = :operation), :pct, :fee, :collector, :now, :now)
            RETURNING id
            """, new MapSqlParameterSource().addValue("operation", operationId).addValue("participant", participantId)
            .addValue("user", userId).addValue("amount", amount).addValue("method", method).addValue("status", status)
            .addValue("recordedBy", recordedBy).addValue("note", note).addValue("provider", provider)
            .addValue("pct", commissionPercent).addValue("fee", commissionAmount).addValue("collector", collectorId)
            .addValue("now", Timestamp.from(now)), Long.class);
    }

    public void attachCheckout(long id, String preferenceId, String checkoutUrl, Instant expiresAt, Instant now) {
        jdbc.update("""
            UPDATE operation_payment SET preference_id = :preference, checkout_url = :url, expires_at = :expires, updated_at = :now WHERE id = :id
            """, new MapSqlParameterSource().addValue("id", id).addValue("preference", preferenceId).addValue("url", checkoutUrl)
            .addValue("expires", Timestamp.from(expiresAt)).addValue("now", Timestamp.from(now)));
    }

    /** Liga o pagamento do Mercado Pago (descoberto pela notificacao ou pela busca) ao registro local. */
    public void attachProviderId(long id, String providerPaymentId, Instant now) {
        jdbc.update("UPDATE operation_payment SET provider_payment_id = :provider, updated_at = :now WHERE id = :id AND provider_payment_id IS NULL",
            Map.of("id", id, "provider", providerPaymentId, "now", Timestamp.from(now)));
    }

    public Optional<Payment> byPublicId(UUID publicId, boolean lock) {
        return jdbc.query("SELECT * FROM operation_payment WHERE public_id = :id" + (lock ? " FOR UPDATE" : ""),
            Map.of("id", publicId), (r, i) -> payment(r)).stream().findFirst();
    }

    /** Valores informados pelo Mercado Pago: tarifa, comissao cobrada, devolvido e liquido do organizador. */
    public void updateFinancials(long id, String paymentType, Long collectorId, BigDecimal providerFee, BigDecimal commissionEffective,
                                 BigDecimal refunded, BigDecimal net, boolean feesConfirmed, Instant now) {
        jdbc.update("""
            UPDATE operation_payment SET payment_type = COALESCE(:type, payment_type), collector_id = COALESCE(:collector, collector_id),
                   provider_fee = :fee, commission_effective = :commission, refunded_amount = :refunded, net_organizer = :net,
                   fees_confirmed = :confirmed, updated_at = :now
            WHERE id = :id
            """, new MapSqlParameterSource().addValue("id", id).addValue("type", paymentType).addValue("collector", collectorId)
            .addValue("fee", providerFee).addValue("commission", commissionEffective).addValue("refunded", refunded)
            .addValue("net", net).addValue("confirmed", feesConfirmed).addValue("now", Timestamp.from(now)));
    }

    /** Para a conciliacao: pendentes recentes e pagos do marketplace sem tarifas confirmadas. */
    public List<Payment> toReconcile(Instant pendingOlderThan, Instant notBefore, int limit) {
        return jdbc.query("""
            SELECT * FROM operation_payment
            WHERE provider IN ('MERCADO_PAGO_DIRECT', 'MERCADO_PAGO_MARKETPLACE') AND created_at > :notBefore
              AND ((status = 'PENDING' AND created_at < :olderThan)
                   OR (provider = 'MERCADO_PAGO_MARKETPLACE' AND status IN ('CONFIRMED', 'PARTIALLY_REFUNDED') AND NOT fees_confirmed))
            ORDER BY last_synced_at NULLS FIRST, created_at LIMIT :limit
            """, Map.of("olderThan", Timestamp.from(pendingOlderThan), "notBefore", Timestamp.from(notBefore), "limit", limit),
            (r, i) -> payment(r));
    }

    public void attachPix(long id, String providerPaymentId, String providerStatus, String detail, String pixCode, String qrBase64,
                          String ticketUrl, Instant expiresAt, Instant now) {
        jdbc.update("""
            UPDATE operation_payment SET provider_payment_id = :provider, provider_status = :providerStatus, provider_status_detail = :detail,
                   pix_code = :code, pix_qr_base64 = :qr, ticket_url = :ticket, expires_at = :expires, last_synced_at = :now, updated_at = :now
            WHERE id = :id
            """, new MapSqlParameterSource().addValue("id", id).addValue("provider", providerPaymentId).addValue("providerStatus", providerStatus)
            .addValue("detail", detail).addValue("code", pixCode).addValue("qr", qrBase64).addValue("ticket", ticketUrl)
            .addValue("expires", expiresAt == null ? null : Timestamp.from(expiresAt)).addValue("now", Timestamp.from(now)));
    }

    /** Muda o status; confirmed_at e refunded_at so sao gravados na primeira vez. */
    public void updateStatus(long id, String status, String providerStatus, String detail, String note, Instant now) {
        jdbc.update("""
            UPDATE operation_payment SET status = :status,
                   provider_status = COALESCE(:providerStatus, provider_status),
                   provider_status_detail = COALESCE(:detail, provider_status_detail),
                   note = COALESCE(:note, note),
                   confirmed_at = CASE WHEN :status = 'CONFIRMED' THEN COALESCE(confirmed_at, CAST(:now AS timestamptz)) ELSE confirmed_at END,
                   refunded_at = CASE WHEN :status = 'REFUNDED' THEN COALESCE(refunded_at, CAST(:now AS timestamptz)) ELSE refunded_at END,
                   last_synced_at = CASE WHEN CAST(:providerStatus AS varchar) IS NULL THEN last_synced_at ELSE CAST(:now AS timestamptz) END,
                   updated_at = :now
            WHERE id = :id
            """, new MapSqlParameterSource().addValue("id", id).addValue("status", status).addValue("providerStatus", providerStatus)
            .addValue("detail", detail).addValue("note", note).addValue("now", Timestamp.from(now)));
    }

    public void touchSynced(long id, Instant now) {
        jdbc.update("UPDATE operation_payment SET last_synced_at = :now WHERE id = :id", Map.of("id", id, "now", Timestamp.from(now)));
    }

    public void event(long paymentId, String status, String source, Long actor, String detail, Instant now) {
        jdbc.update("""
            INSERT INTO operation_payment_event(payment_id, status, source, actor_user_id, detail, created_at)
            VALUES (:payment, :status, :source, :actor, :detail, :now)
            """, new MapSqlParameterSource().addValue("payment", paymentId).addValue("status", status).addValue("source", source)
            .addValue("actor", actor).addValue("detail", detail).addValue("now", Timestamp.from(now)));
    }

    /** Grava a confirmacao na inscricao (libera documentos); mantem o primeiro horario. */
    public void markParticipantPaid(long participantId, Instant now) {
        jdbc.update("""
            UPDATE operation_participant SET payment_confirmed_at = COALESCE(payment_confirmed_at, :now), updated_at = :now WHERE id = :id
            """, Map.of("id", participantId, "now", Timestamp.from(now)));
    }

    /** Estorno: tira a confirmacao so se nao sobrar outro pagamento confirmado. */
    public void clearParticipantPaidIfNoneLeft(long participantId, Instant now) {
        jdbc.update("""
            UPDATE operation_participant SET payment_confirmed_at = NULL, updated_at = :now
            WHERE id = :id AND NOT EXISTS (SELECT 1 FROM operation_payment WHERE participant_id = :id AND status IN ('CONFIRMED', 'PARTIALLY_REFUNDED'))
            """, Map.of("id", participantId, "now", Timestamp.from(now)));
    }

    /** Inscritos ativos e qualquer pessoa com movimento financeiro (historico preservado mesmo apos cancelar). */
    public List<Row> rows(long operationId) {
        return jdbc.query("""
            SELECT p.id participant_id, p.user_id, prof.public_id operator_id, prof.callsign, p.status participant_status,
                   p.payment_confirmed_at, pay.public_id payment_id, pay.status payment_status, pay.method, pay.amount,
                   pay.updated_at payment_updated_at, pay.note, pay.provider, pay.payment_type,
                   COALESCE(pay.commission_effective, pay.commission_amount) commission, pay.provider_fee, pay.refunded_amount,
                   pay.net_organizer, COALESCE(pay.fees_confirmed, false) fees_confirmed, pay.provider_payment_id
            FROM operation_participant p
            JOIN operator_profile prof ON prof.user_id = p.user_id
            LEFT JOIN LATERAL (SELECT * FROM operation_payment x WHERE x.participant_id = p.id
                               ORDER BY (x.status IN ('CONFIRMED', 'PARTIALLY_REFUNDED')) DESC, x.created_at DESC, x.id DESC LIMIT 1) pay ON true
            WHERE p.operation_id = :id
              AND (p.status IN ('REQUESTED','APPROVED','WAITING_LIST','CONFIRMED','CHECKED_IN') OR pay.id IS NOT NULL OR p.payment_confirmed_at IS NOT NULL)
            ORDER BY p.requested_at, p.id
            """, Map.of("id", operationId), (r, i) -> new Row(r.getLong("participant_id"), r.getLong("user_id"),
                r.getObject("operator_id", UUID.class), r.getString("callsign"), r.getString("participant_status"),
                instant(r, "payment_confirmed_at"), r.getObject("payment_id", UUID.class), r.getString("payment_status"),
                r.getString("method"), r.getBigDecimal("amount"), instant(r, "payment_updated_at"), r.getString("note"),
                r.getString("provider"), r.getString("payment_type"), r.getBigDecimal("commission"), r.getBigDecimal("provider_fee"),
                r.getBigDecimal("refunded_amount"), r.getBigDecimal("net_organizer"), r.getBoolean("fees_confirmed"),
                r.getString("provider_payment_id")));
    }

    /**
     * Registra a entrega de uma notificacao. Devolve false se esta mesma entrega (x-request-id) ja foi processada: o Mercado Pago
     * reenvia ate receber 200, entao entregas que falharam continuam sendo processadas.
     */
    public boolean notificationIsNew(String requestId, String topic, String dataId, Long mpUserId, Instant now) {
        String status = jdbc.queryForObject("""
            INSERT INTO payment_notification(request_id, topic, data_id, mp_user_id, received_at) VALUES (:request, :topic, :data, :mp, :now)
            ON CONFLICT (request_id) DO UPDATE SET received_at = payment_notification.received_at
            RETURNING status
            """, new MapSqlParameterSource().addValue("request", requestId).addValue("topic", topic).addValue("data", dataId)
            .addValue("mp", mpUserId).addValue("now", Timestamp.from(now)), String.class);
        return !"PROCESSED".equals(status) && !"IGNORED".equals(status);
    }

    public void notificationResult(String requestId, String status, Long paymentId, String detail, Instant now) {
        jdbc.update("""
            UPDATE payment_notification SET status = :status, payment_id = COALESCE(:payment, payment_id), detail = :detail, processed_at = :now
            WHERE request_id = :request
            """, new MapSqlParameterSource().addValue("request", requestId).addValue("status", status).addValue("payment", paymentId)
            .addValue("detail", detail).addValue("now", Timestamp.from(now)));
    }

    private static Payment payment(ResultSet r) throws SQLException {
        return new Payment(r.getLong("id"), r.getObject("public_id", UUID.class), r.getLong("operation_id"), r.getLong("participant_id"),
            r.getLong("user_id"), r.getBigDecimal("amount"), r.getString("method"), r.getString("status"), r.getString("provider_payment_id"),
            r.getString("provider_status_detail"), r.getString("pix_code"), r.getString("pix_qr_base64"), r.getString("ticket_url"),
            instant(r, "expires_at"), instant(r, "confirmed_at"), instant(r, "refunded_at"), r.getString("note"),
            instant(r, "last_synced_at"), instant(r, "created_at"), r.getString("provider"), (Long) r.getObject("organizer_user_id"),
            (Long) r.getObject("collector_id"), r.getString("checkout_url"), r.getString("payment_type"), r.getBigDecimal("commission_percent"),
            r.getBigDecimal("commission_amount"), r.getBigDecimal("commission_effective"), r.getBigDecimal("provider_fee"),
            r.getBigDecimal("refunded_amount"), r.getBoolean("fees_confirmed"));
    }

    private static Instant instant(ResultSet r, String column) throws SQLException {
        Timestamp value = r.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
