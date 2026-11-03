package br.com.operadorzero.payment;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Conexoes Mercado Pago dos organizadores, tentativas de OAuth e comissoes da plataforma. */
@Repository
public class MarketplaceRepository {
    public record Connection(long id, long userId, Long mpUserId, String accessTokenEnc, String refreshTokenEnc, String scope,
                             Boolean liveMode, Instant expiresAt, String status, String lastError, Instant connectedAt,
                             Instant refreshedAt) {}

    public record Attempt(long userId, String codeVerifierEnc, Instant expiresAt, Instant usedAt) {}

    /** percent nulo em linha de organizador = segue o padrao. */
    public record CommissionRow(Long userId, String username, String callsign, BigDecimal percent, Instant effectiveFrom,
                                String changedBy, String note) {}

    private final NamedParameterJdbcTemplate jdbc;

    public MarketplaceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- conexoes ----

    public Optional<Connection> connection(long userId, boolean lock) {
        return jdbc.query("SELECT * FROM mercadopago_connection WHERE user_id = :user" + (lock ? " FOR UPDATE" : ""),
            Map.of("user", userId), (r, i) -> connection(r)).stream().findFirst();
    }

    public Optional<Connection> connectedByAccount(long mpUserId) {
        return jdbc.query("SELECT * FROM mercadopago_connection WHERE mp_user_id = :mp AND status = 'CONNECTED'",
            Map.of("mp", mpUserId), (r, i) -> connection(r)).stream().findFirst();
    }

    public void saveConnected(long userId, long mpUserId, String accessEnc, String refreshEnc, String publicKey, String scope,
                              Boolean liveMode, Instant expiresAt, Instant now) {
        jdbc.update("""
            INSERT INTO mercadopago_connection(user_id, mp_user_id, access_token_enc, refresh_token_enc, public_key, scope, live_mode,
                                               expires_at, status, last_error, connected_at, refreshed_at, disconnected_at, updated_at)
            VALUES (:user, :mp, :access, :refresh, :publicKey, :scope, :live, :expires, 'CONNECTED', NULL, :now, :now, NULL, :now)
            ON CONFLICT (user_id) DO UPDATE SET mp_user_id = EXCLUDED.mp_user_id, access_token_enc = EXCLUDED.access_token_enc,
                refresh_token_enc = EXCLUDED.refresh_token_enc, public_key = EXCLUDED.public_key, scope = EXCLUDED.scope,
                live_mode = EXCLUDED.live_mode, expires_at = EXCLUDED.expires_at, status = 'CONNECTED', last_error = NULL,
                connected_at = EXCLUDED.connected_at, refreshed_at = EXCLUDED.refreshed_at, disconnected_at = NULL, updated_at = EXCLUDED.updated_at
            """, new MapSqlParameterSource().addValue("user", userId).addValue("mp", mpUserId).addValue("access", accessEnc)
            .addValue("refresh", refreshEnc).addValue("publicKey", publicKey).addValue("scope", truncate(scope, 300))
            .addValue("live", liveMode).addValue("expires", Timestamp.from(expiresAt)).addValue("now", Timestamp.from(now)));
    }

    public void saveRefreshed(long userId, String accessEnc, String refreshEnc, Instant expiresAt, Instant now) {
        jdbc.update("""
            UPDATE mercadopago_connection SET access_token_enc = :access, refresh_token_enc = COALESCE(:refresh, refresh_token_enc),
                   expires_at = :expires, refreshed_at = :now, status = 'CONNECTED', last_error = NULL, updated_at = :now
            WHERE user_id = :user
            """, new MapSqlParameterSource().addValue("user", userId).addValue("access", accessEnc).addValue("refresh", refreshEnc)
            .addValue("expires", Timestamp.from(expiresAt)).addValue("now", Timestamp.from(now)));
    }

    /** Status sem token valido: apaga os tokens cifrados (nao servem mais) e guarda o motivo. */
    public void markStatus(long userId, String status, String error, Instant now) {
        jdbc.update("""
            UPDATE mercadopago_connection SET status = :status, last_error = :error, updated_at = :now,
                   access_token_enc = CASE WHEN :status = 'CONNECTED' THEN access_token_enc END,
                   refresh_token_enc = CASE WHEN :status = 'CONNECTED' THEN refresh_token_enc END,
                   disconnected_at = CASE WHEN :status = 'DISCONNECTED' THEN CAST(:now AS timestamptz) ELSE disconnected_at END
            WHERE user_id = :user
            """, new MapSqlParameterSource().addValue("user", userId).addValue("status", status).addValue("error", truncate(error, 300))
            .addValue("now", Timestamp.from(now)));
    }

    /** Conexoes cujo token vence antes de "until" (renovacao preventiva). */
    public List<Long> expiring(Instant until) {
        return jdbc.queryForList("SELECT user_id FROM mercadopago_connection WHERE status = 'CONNECTED' AND expires_at < :until",
            Map.of("until", Timestamp.from(until)), Long.class);
    }

    // ---- tentativas de OAuth ----

    public void saveAttempt(String stateHash, long userId, String verifierEnc, Instant expiresAt, Instant now) {
        jdbc.update("""
            INSERT INTO mercadopago_oauth_attempt(state_hash, user_id, code_verifier_enc, created_at, expires_at)
            VALUES (:state, :user, :verifier, :now, :expires)
            """, new MapSqlParameterSource().addValue("state", stateHash).addValue("user", userId).addValue("verifier", verifierEnc)
            .addValue("now", Timestamp.from(now)).addValue("expires", Timestamp.from(expiresAt)));
    }

    /** Consome a tentativa (uso unico): devolve-a so na primeira vez. */
    public Optional<Attempt> consumeAttempt(String stateHash, Instant now) {
        return jdbc.query("""
            UPDATE mercadopago_oauth_attempt SET used_at = :now WHERE state_hash = :state AND used_at IS NULL
            RETURNING user_id, code_verifier_enc, expires_at, used_at
            """, Map.of("state", stateHash, "now", Timestamp.from(now)), (r, i) -> new Attempt(r.getLong("user_id"),
                r.getString("code_verifier_enc"), instant(r, "expires_at"), instant(r, "used_at"))).stream().findFirst();
    }

    public int recentAttempts(long userId, Instant since) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM mercadopago_oauth_attempt WHERE user_id = :user AND created_at > :since",
            Map.of("user", userId, "since", Timestamp.from(since)), Integer.class);
        return count == null ? 0 : count;
    }

    public void purgeAttempts(Instant before) {
        jdbc.update("DELETE FROM mercadopago_oauth_attempt WHERE expires_at < :before", Map.of("before", Timestamp.from(before)));
    }

    // ---- comissoes ----

    public Optional<BigDecimal> defaultPercent(Instant now) {
        return jdbc.query("""
            SELECT percent FROM platform_commission WHERE user_id IS NULL AND effective_from <= :now ORDER BY effective_from DESC, id DESC LIMIT 1
            """, Map.of("now", Timestamp.from(now)), (r, i) -> r.getBigDecimal(1)).stream().findFirst();
    }

    /** Linha individual mais recente: Optional.empty = nunca configurado; Optional(null) nao existe, usar overrideRow. */
    public Optional<CommissionRow> overrideRow(long userId, Instant now) {
        return jdbc.query("""
            SELECT c.user_id, NULL username, NULL callsign, c.percent, c.effective_from, NULL changed_by, c.note
            FROM platform_commission c WHERE c.user_id = :user AND c.effective_from <= :now ORDER BY c.effective_from DESC, c.id DESC LIMIT 1
            """, Map.of("user", userId, "now", Timestamp.from(now)), (r, i) -> commission(r)).stream().findFirst();
    }

    public void insertCommission(Long userId, BigDecimal percent, long changedBy, String note, Instant now) {
        jdbc.update("""
            INSERT INTO platform_commission(user_id, percent, effective_from, changed_by_user_id, note, created_at)
            VALUES (:user, :percent, :now, :by, :note, :now)
            """, new MapSqlParameterSource().addValue("user", userId).addValue("percent", percent).addValue("by", changedBy)
            .addValue("note", truncate(note, 300)).addValue("now", Timestamp.from(now)));
    }

    /** Percentual individual vigente de cada organizador que ja teve um (inclui os que voltaram ao padrao). */
    public List<CommissionRow> overrides(Instant now) {
        return jdbc.query("""
            SELECT DISTINCT ON (c.user_id) c.user_id, u.username, prof.callsign, c.percent, c.effective_from, by_prof.callsign changed_by, c.note
            FROM platform_commission c
            JOIN app_user u ON u.id = c.user_id
            LEFT JOIN operator_profile prof ON prof.user_id = c.user_id
            LEFT JOIN operator_profile by_prof ON by_prof.user_id = c.changed_by_user_id
            WHERE c.user_id IS NOT NULL AND c.effective_from <= :now
            ORDER BY c.user_id, c.effective_from DESC, c.id DESC
            """, Map.of("now", Timestamp.from(now)), (r, i) -> commission(r));
    }

    public List<CommissionRow> history(int limit) {
        return jdbc.query("""
            SELECT c.user_id, u.username, prof.callsign, c.percent, c.effective_from, by_prof.callsign changed_by, c.note
            FROM platform_commission c
            LEFT JOIN app_user u ON u.id = c.user_id
            LEFT JOIN operator_profile prof ON prof.user_id = c.user_id
            LEFT JOIN operator_profile by_prof ON by_prof.user_id = c.changed_by_user_id
            ORDER BY c.created_at DESC, c.id DESC LIMIT :limit
            """, Map.of("limit", limit), (r, i) -> commission(r));
    }

    public Optional<Long> userIdByUsername(String username) {
        return jdbc.query("SELECT id FROM app_user WHERE lower(username) = lower(:name)", Map.of("name", username),
            (r, i) -> r.getLong(1)).stream().findFirst();
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static Connection connection(ResultSet r) throws SQLException {
        return new Connection(r.getLong("id"), r.getLong("user_id"), (Long) r.getObject("mp_user_id"), r.getString("access_token_enc"),
            r.getString("refresh_token_enc"), r.getString("scope"), (Boolean) r.getObject("live_mode"), instant(r, "expires_at"),
            r.getString("status"), r.getString("last_error"), instant(r, "connected_at"), instant(r, "refreshed_at"));
    }

    private static CommissionRow commission(ResultSet r) throws SQLException {
        return new CommissionRow((Long) r.getObject("user_id"), r.getString("username"), r.getString("callsign"), r.getBigDecimal("percent"),
            instant(r, "effective_from"), r.getString("changed_by"), r.getString("note"));
    }

    private static Instant instant(ResultSet r, String column) throws SQLException {
        Timestamp value = r.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
