package br.com.operadorzero.payment;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Leituras financeiras: por operacao (organizador) e consolidado (administrador), sempre a partir de operation_payment. */
@Repository
public class FinanceRepository {
    public record OperationTotals(UUID operationId, String name, String date, long registrations, long paid, long pending, long cancelled,
                                  BigDecimal gross, BigDecimal providerFees, BigDecimal commission, BigDecimal net, BigDecimal refunded,
                                  boolean estimated) {}

    public record PaymentLine(UUID id, Instant createdAt, UUID operationId, String operationName, String organizer, String player,
                              String provider, String method, String paymentType, String status, BigDecimal amount,
                              BigDecimal commissionPercent, BigDecimal commission, BigDecimal providerFee, BigDecimal refunded,
                              BigDecimal net, boolean feesConfirmed, String transactionId) {}

    public record Filters(String organizer, UUID operationId, Instant from, Instant to, String method, String status) {}

    public record Totals(long payments, long approved, long pending, long refunded, long chargebacks, BigDecimal gross,
                         BigDecimal providerFees, BigDecimal commission, BigDecimal refundedAmount, boolean estimated) {}

    public record ConnectionStats(long organizers, long connected, long notConnected, long needsReconnect) {}

    private static final String PAID = "('CONFIRMED','PARTIALLY_REFUNDED')";
    private final NamedParameterJdbcTemplate jdbc;

    public FinanceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Operacoes que o usuario organiza ou administra, com inscricoes e valores. */
    public List<OperationTotals> operationTotals(long userId) {
        return jdbc.query("""
            SELECT o.public_id, o.name, to_char(o.operation_date, 'DD/MM/YYYY') op_date,
                   (SELECT count(*) FROM operation_participant p WHERE p.operation_id = o.id
                      AND p.status IN ('REQUESTED','APPROVED','WAITING_LIST','CONFIRMED','CHECKED_IN')) registrations,
                   count(*) FILTER (WHERE pay.status IN """ + PAID + """
            ) paid,
                   count(*) FILTER (WHERE pay.status = 'PENDING') pending,
                   count(*) FILTER (WHERE pay.status IN ('CANCELLED','EXPIRED','REJECTED')) cancelled,
                   COALESCE(sum(pay.amount) FILTER (WHERE pay.status IN """ + PAID + """
            ), 0) gross,
                   COALESCE(sum(pay.provider_fee) FILTER (WHERE pay.status IN """ + PAID + """
            ), 0) fees,
                   COALESCE(sum(COALESCE(pay.commission_effective, pay.commission_amount)) FILTER (WHERE pay.status IN """ + PAID + """
            ), 0) commission,
                   COALESCE(sum(COALESCE(pay.net_organizer, pay.amount - COALESCE(pay.commission_amount, 0))) FILTER (WHERE pay.status IN """ + PAID + """
            ), 0) net,
                   COALESCE(sum(pay.refunded_amount), 0) refunded,
                   bool_or(pay.provider = 'MERCADO_PAGO_MARKETPLACE' AND pay.status IN """ + PAID + """
             AND NOT pay.fees_confirmed) estimated
            FROM airsoft_operation o
            LEFT JOIN operation_payment pay ON pay.operation_id = o.id
            WHERE o.deleted_at IS NULL AND o.registration_price > 0
              AND (o.organizer_user_id = :user OR EXISTS (SELECT 1 FROM operation_role_assignment a
                     WHERE a.operation_id = o.id AND a.user_id = :user AND a.role = 'OPERATION_ADMIN'))
            GROUP BY o.id ORDER BY o.operation_date DESC, o.id DESC LIMIT 100
            """, Map.of("user", userId), (r, i) -> new OperationTotals(r.getObject("public_id", UUID.class), r.getString("name"),
                r.getString("op_date"), r.getLong("registrations"), r.getLong("paid"), r.getLong("pending"), r.getLong("cancelled"),
                r.getBigDecimal("gross"), r.getBigDecimal("fees"), r.getBigDecimal("commission"), r.getBigDecimal("net"),
                r.getBigDecimal("refunded"), r.getBoolean("estimated")));
    }

    /** Pagamentos das operacoes que o usuario organiza ou administra. */
    public List<PaymentLine> organizerLines(long userId, int limit) {
        return lines("""
            (o.organizer_user_id = :user OR EXISTS (SELECT 1 FROM operation_role_assignment a
               WHERE a.operation_id = o.id AND a.user_id = :user AND a.role = 'OPERATION_ADMIN'))
            """, new MapSqlParameterSource("user", userId), limit);
    }

    public List<PaymentLine> adminLines(Filters filters, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        return lines(where(filters, params), params, limit);
    }

    public Totals adminTotals(Filters filters) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        return jdbc.queryForObject("""
            SELECT count(*) payments, count(*) FILTER (WHERE pay.status IN """ + PAID + """
            ) approved,
                   count(*) FILTER (WHERE pay.status = 'PENDING') pending,
                   count(*) FILTER (WHERE pay.status IN ('REFUNDED','PARTIALLY_REFUNDED')) refunded,
                   count(*) FILTER (WHERE pay.status = 'CHARGEBACK') chargebacks,
                   COALESCE(sum(pay.amount) FILTER (WHERE pay.status IN """ + PAID + """
            ), 0) gross,
                   COALESCE(sum(pay.provider_fee) FILTER (WHERE pay.status IN """ + PAID + """
            ), 0) fees,
                   COALESCE(sum(COALESCE(pay.commission_effective, pay.commission_amount)) FILTER (WHERE pay.status IN """ + PAID + """
             AND pay.provider = 'MERCADO_PAGO_MARKETPLACE'), 0) commission,
                   COALESCE(sum(pay.refunded_amount), 0) refunded_amount,
                   COALESCE(bool_or(pay.provider = 'MERCADO_PAGO_MARKETPLACE' AND pay.status IN """ + PAID + """
             AND NOT pay.fees_confirmed), false) estimated
            FROM operation_payment pay
            JOIN airsoft_operation o ON o.id = pay.operation_id
            LEFT JOIN app_user org ON org.id = pay.organizer_user_id
            WHERE
            """ + " " + where(filters, params), params, (r, i) -> new Totals(r.getLong("payments"), r.getLong("approved"), r.getLong("pending"),
                r.getLong("refunded"), r.getLong("chargebacks"), r.getBigDecimal("gross"), r.getBigDecimal("fees"), r.getBigDecimal("commission"),
                r.getBigDecimal("refunded_amount"), r.getBoolean("estimated")));
    }

    public ConnectionStats connectionStats() {
        return jdbc.queryForObject("""
            SELECT count(DISTINCT o.organizer_user_id) organizers,
                   count(DISTINCT o.organizer_user_id) FILTER (WHERE c.status = 'CONNECTED') connected,
                   count(DISTINCT o.organizer_user_id) FILTER (WHERE c.status IS NULL OR c.status IN ('DISCONNECTED')) not_connected,
                   count(DISTINCT o.organizer_user_id) FILTER (WHERE c.status IN ('NEEDS_RECONNECT','ERROR')) needs_reconnect
            FROM airsoft_operation o
            LEFT JOIN mercadopago_connection c ON c.user_id = o.organizer_user_id
            WHERE o.deleted_at IS NULL AND o.registration_price > 0
            """, Map.of(), (r, i) -> new ConnectionStats(r.getLong("organizers"), r.getLong("connected"), r.getLong("not_connected"),
                r.getLong("needs_reconnect")));
    }

    private String where(Filters f, MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder("true");
        if (f.organizer() != null && !f.organizer().isBlank()) {
            where.append(" AND lower(org.username) = lower(:organizer)");
            params.addValue("organizer", f.organizer().strip().replaceFirst("^@", ""));
        }
        if (f.operationId() != null) { where.append(" AND o.public_id = :operation"); params.addValue("operation", f.operationId()); }
        if (f.from() != null) { where.append(" AND pay.created_at >= :from"); params.addValue("from", Timestamp.from(f.from())); }
        if (f.to() != null) { where.append(" AND pay.created_at < :to"); params.addValue("to", Timestamp.from(f.to())); }
        if (f.method() != null && !f.method().isBlank()) {
            // PIX/CHECKOUT/MANUAL ou o meio escolhido no checkout (pix, credit_card, account_money...)
            where.append(" AND (pay.method = :method OR pay.payment_type = :methodLower)");
            params.addValue("method", f.method().strip().toUpperCase()).addValue("methodLower", f.method().strip().toLowerCase());
        }
        if (f.status() != null && !f.status().isBlank()) { where.append(" AND pay.status = :status"); params.addValue("status", f.status().strip().toUpperCase()); }
        return where.toString();
    }

    private List<PaymentLine> lines(String where, MapSqlParameterSource params, int limit) {
        params.addValue("limit", limit);
        return jdbc.query("""
            SELECT pay.public_id, pay.created_at, o.public_id operation_id, o.name operation_name, orgp.callsign organizer,
                   pp.callsign player, pay.provider, pay.method, pay.payment_type, pay.status, pay.amount, pay.commission_percent,
                   COALESCE(pay.commission_effective, pay.commission_amount) commission, pay.provider_fee, pay.refunded_amount,
                   pay.net_organizer, pay.fees_confirmed, pay.provider_payment_id
            FROM operation_payment pay
            JOIN airsoft_operation o ON o.id = pay.operation_id
            LEFT JOIN app_user org ON org.id = pay.organizer_user_id
            LEFT JOIN operator_profile orgp ON orgp.user_id = pay.organizer_user_id
            LEFT JOIN operator_profile pp ON pp.user_id = pay.user_id
            WHERE
            """ + " " + where + " ORDER BY pay.created_at DESC, pay.id DESC LIMIT :limit", params, (r, i) -> line(r));
    }

    private static PaymentLine line(ResultSet r) throws SQLException {
        Timestamp created = r.getTimestamp("created_at");
        return new PaymentLine(r.getObject("public_id", UUID.class), created == null ? null : created.toInstant(),
            r.getObject("operation_id", UUID.class), r.getString("operation_name"), r.getString("organizer"), r.getString("player"),
            r.getString("provider"), r.getString("method"), r.getString("payment_type"), r.getString("status"), r.getBigDecimal("amount"),
            r.getBigDecimal("commission_percent"), r.getBigDecimal("commission"), r.getBigDecimal("provider_fee"),
            r.getBigDecimal("refunded_amount"), r.getBigDecimal("net_organizer"), r.getBoolean("fees_confirmed"),
            r.getString("provider_payment_id"));
    }
}
