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

    public record Payment(long id, UUID publicId, long operationId, long participantId, long userId, BigDecimal amount, String method,
                          String status, String providerPaymentId, String providerStatusDetail, String pixCode, String pixQrBase64,
                          String ticketUrl, Instant expiresAt, Instant confirmedAt, Instant refundedAt, String note,
                          Instant lastSyncedAt, Instant createdAt) {}

    /** Linha do painel financeiro do organizador. */
    public record Row(long participantId, long userId, UUID operatorId, String callsign, String participantStatus, Instant paymentConfirmedAt,
                      UUID paymentId, String paymentStatus, String method, BigDecimal amount, Instant paymentUpdatedAt, String note) {}

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
        return jdbc.query("SELECT * FROM operation_payment WHERE participant_id = :id AND status = 'CONFIRMED' ORDER BY confirmed_at DESC, id DESC LIMIT 1"
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

    public long insert(long operationId, long participantId, long userId, BigDecimal amount, String method, String status,
                       Long recordedBy, String note, Instant now) {
        return jdbc.queryForObject("""
            INSERT INTO operation_payment(operation_id, participant_id, user_id, amount, method, status, confirmed_at,
                                          recorded_by_user_id, note, created_at, updated_at)
            VALUES (:operation, :participant, :user, :amount, :method, :status,
                    CASE WHEN :status = 'CONFIRMED' THEN CAST(:now AS timestamptz) END, :recordedBy, :note, :now, :now)
            RETURNING id
            """, new MapSqlParameterSource().addValue("operation", operationId).addValue("participant", participantId)
            .addValue("user", userId).addValue("amount", amount).addValue("method", method).addValue("status", status)
            .addValue("recordedBy", recordedBy).addValue("note", note).addValue("now", Timestamp.from(now)), Long.class);
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
            WHERE id = :id AND NOT EXISTS (SELECT 1 FROM operation_payment WHERE participant_id = :id AND status = 'CONFIRMED')
            """, Map.of("id", participantId, "now", Timestamp.from(now)));
    }

    /** Inscritos ativos e qualquer pessoa com movimento financeiro (historico preservado mesmo apos cancelar). */
    public List<Row> rows(long operationId) {
        return jdbc.query("""
            SELECT p.id participant_id, p.user_id, prof.public_id operator_id, prof.callsign, p.status participant_status,
                   p.payment_confirmed_at, pay.public_id payment_id, pay.status payment_status, pay.method, pay.amount,
                   pay.updated_at payment_updated_at, pay.note
            FROM operation_participant p
            JOIN operator_profile prof ON prof.user_id = p.user_id
            LEFT JOIN LATERAL (SELECT * FROM operation_payment x WHERE x.participant_id = p.id
                               ORDER BY (x.status = 'CONFIRMED') DESC, x.created_at DESC, x.id DESC LIMIT 1) pay ON true
            WHERE p.operation_id = :id
              AND (p.status IN ('REQUESTED','APPROVED','WAITING_LIST','CONFIRMED','CHECKED_IN') OR pay.id IS NOT NULL OR p.payment_confirmed_at IS NOT NULL)
            ORDER BY p.requested_at, p.id
            """, Map.of("id", operationId), (r, i) -> new Row(r.getLong("participant_id"), r.getLong("user_id"),
                r.getObject("operator_id", UUID.class), r.getString("callsign"), r.getString("participant_status"),
                instant(r, "payment_confirmed_at"), r.getObject("payment_id", UUID.class), r.getString("payment_status"),
                r.getString("method"), r.getBigDecimal("amount"), instant(r, "payment_updated_at"), r.getString("note")));
    }

    private static Payment payment(ResultSet r) throws SQLException {
        return new Payment(r.getLong("id"), r.getObject("public_id", UUID.class), r.getLong("operation_id"), r.getLong("participant_id"),
            r.getLong("user_id"), r.getBigDecimal("amount"), r.getString("method"), r.getString("status"), r.getString("provider_payment_id"),
            r.getString("provider_status_detail"), r.getString("pix_code"), r.getString("pix_qr_base64"), r.getString("ticket_url"),
            instant(r, "expires_at"), instant(r, "confirmed_at"), instant(r, "refunded_at"), r.getString("note"),
            instant(r, "last_synced_at"), instant(r, "created_at"));
    }

    private static Instant instant(ResultSet r, String column) throws SQLException {
        Timestamp value = r.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
