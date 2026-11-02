package br.com.operadorzero.gamification;

import br.com.operadorzero.notification.Notifier;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Livro-razao de XP. Valores, limite diario e desconto entre as mesmas duas contas vem de xp_rule
 * e gamification_setting (editaveis sem deploy). Cada acao rende XP uma unica vez.
 */
@Service
public class XpService {
    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private final NamedParameterJdbcTemplate jdbc;
    private final Notifier notifier;
    private final Clock clock;

    @Autowired
    public XpService(NamedParameterJdbcTemplate jdbc, Notifier notifier) {
        this(jdbc, notifier, Clock.systemUTC());
    }

    XpService(NamedParameterJdbcTemplate jdbc, Notifier notifier, Clock clock) {
        this.jdbc = jdbc;
        this.notifier = notifier;
        this.clock = clock;
    }

    /** Concede o XP de {@code action}; devolve quanto foi efetivamente creditado (0 se ja concedido, regra inativa ou limite). */
    @Transactional(propagation = Propagation.MANDATORY)
    public int award(long userId, String action, String sourceType, long sourceId, Long counterpartUserId) {
        var rule = jdbc.query("SELECT xp, daily_cap, same_pair_factor FROM xp_rule WHERE action_code = :action AND active",
            Map.of("action", action), (r, i) -> new Rule(r.getInt("xp"), (Integer) r.getObject("daily_cap"), r.getDouble("same_pair_factor")))
            .stream().findFirst().orElse(null);
        if (rule == null || rule.xp() <= 0) return 0;
        Instant now = clock.instant();
        Instant dayStart = LocalDate.ofInstant(now, BRASILIA).atStartOfDay(BRASILIA).toInstant();
        // Serializa os lancamentos do mesmo operador para o limite diario nao ser furado por pedidos simultaneos.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext('xp'), CAST(:userId AS int))", Map.of("userId", userId));
        int xp = rule.xp();
        if (rule.dailyCap() != null) {
            Long today = jdbc.queryForObject("""
                SELECT count(*) FROM xp_event WHERE user_id = :userId AND action_code = :action AND NOT reversal AND awarded_xp > 0
                  AND created_at >= :dayStart
                """, Map.of("userId", userId, "action", action, "dayStart", Timestamp.from(dayStart)), Long.class);
            if (today != null && today >= rule.dailyCap()) xp = 0;
        }
        if (xp > 0 && counterpartUserId != null && rule.samePairFactor() < 1) {
            int windowDays = setting("xp.same_pair_window_days", 7);
            int freeActions = setting("xp.same_pair_free_actions", 3);
            Long pair = jdbc.queryForObject("""
                SELECT count(*) FROM xp_event WHERE user_id = :userId AND counterpart_user_id = :counterpart AND NOT reversal
                  AND created_at >= :since
                """, Map.of("userId", userId, "counterpart", counterpartUserId,
                    "since", Timestamp.from(now.minusSeconds(windowDays * 86_400L))), Long.class);
            if (pair != null && pair >= freeActions) xp = (int) Math.floor(xp * rule.samePairFactor());
        }
        int inserted = jdbc.update("""
            INSERT INTO xp_event(user_id, action_code, source_type, source_id, counterpart_user_id, raw_xp, awarded_xp, created_at)
            VALUES (:userId, :action, :sourceType, :sourceId, :counterpart, :raw, :awarded, :now)
            ON CONFLICT DO NOTHING
            """, new MapSqlParameterSource().addValue("userId", userId).addValue("action", action).addValue("sourceType", sourceType)
                .addValue("sourceId", sourceId).addValue("counterpart", counterpartUserId).addValue("raw", rule.xp())
                .addValue("awarded", xp).addValue("now", Timestamp.from(now)));
        if (inserted == 0 || xp == 0) return 0;
        applyTotal(userId, xp, now);
        return xp;
    }

    /** Estorna o XP de uma acao desfeita (post apagado, reacao retirada). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reverse(long userId, String action, String sourceType, long sourceId, Long counterpartUserId) {
        Integer awarded = jdbc.query("""
            SELECT awarded_xp FROM xp_event WHERE user_id = :userId AND action_code = :action AND source_type = :sourceType
              AND source_id = :sourceId AND counterpart_user_id IS NOT DISTINCT FROM :counterpart AND NOT reversal
            """, new MapSqlParameterSource().addValue("userId", userId).addValue("action", action).addValue("sourceType", sourceType)
                .addValue("sourceId", sourceId).addValue("counterpart", counterpartUserId), (r, i) -> r.getInt(1))
            .stream().findFirst().orElse(null);
        if (awarded == null || awarded == 0) return;
        Instant now = clock.instant();
        int inserted = jdbc.update("""
            INSERT INTO xp_event(user_id, action_code, source_type, source_id, counterpart_user_id, raw_xp, awarded_xp, reversal, created_at)
            VALUES (:userId, :action, :sourceType, :sourceId, :counterpart, :raw, :raw, TRUE, :now)
            ON CONFLICT DO NOTHING
            """, new MapSqlParameterSource().addValue("userId", userId).addValue("action", action).addValue("sourceType", sourceType)
                .addValue("sourceId", sourceId).addValue("counterpart", counterpartUserId).addValue("raw", -awarded)
                .addValue("now", Timestamp.from(now)));
        if (inserted > 0) applyTotal(userId, -awarded, now);
    }

    private void applyTotal(long userId, int delta, Instant now) {
        var row = jdbc.queryForMap("""
            WITH previous AS (SELECT rank_code FROM operator_xp WHERE user_id = :userId),
            upserted AS (
                INSERT INTO operator_xp(user_id, total_xp, updated_at) VALUES (:userId, GREATEST(:delta, 0), :now)
                ON CONFLICT (user_id) DO UPDATE SET total_xp = GREATEST(operator_xp.total_xp + :delta, 0), updated_at = :now
                RETURNING total_xp
            )
            SELECT (SELECT rank_code FROM previous) previous_rank, upserted.total_xp,
                   (SELECT code FROM rank_tier WHERE min_xp <= upserted.total_xp ORDER BY min_xp DESC LIMIT 1) new_rank,
                   (SELECT name FROM rank_tier WHERE min_xp <= upserted.total_xp ORDER BY min_xp DESC LIMIT 1) new_rank_name
            FROM upserted
            """, Map.of("userId", userId, "delta", delta, "now", Timestamp.from(now)));
        String previous = (String) row.get("previous_rank");
        String current = (String) row.get("new_rank");
        if (current.equals(previous)) return;
        Integer previousPosition = previous == null ? 1 : jdbc.queryForObject("SELECT position FROM rank_tier WHERE code = :code", Map.of("code", previous), Integer.class);
        Integer currentPosition = jdbc.queryForObject("SELECT position FROM rank_tier WHERE code = :code", Map.of("code", current), Integer.class);
        boolean promoted = currentPosition != null && previousPosition != null && currentPosition > previousPosition;
        jdbc.update("UPDATE operator_xp SET rank_code = :rank, promoted_at = CASE WHEN :promoted THEN :now ELSE promoted_at END WHERE user_id = :userId",
            new MapSqlParameterSource().addValue("rank", current).addValue("promoted", promoted).addValue("now", Timestamp.from(now)).addValue("userId", userId));
        if (promoted) {
            notifier.toUser(userId, "RANK_PROMOTION", "Promovido a " + row.get("new_rank_name"),
                "Sua atividade na plataforma rendeu uma nova patente.", "Perfil");
        }
    }

    private int setting(String key, int fallback) {
        return jdbc.query("SELECT value::text FROM gamification_setting WHERE key = :key", Map.of("key", key), (r, i) -> r.getString(1))
            .stream().findFirst().map(value -> {
                try { return (int) Double.parseDouble(value); } catch (NumberFormatException e) { return fallback; }
            }).orElse(fallback);
    }

    private record Rule(int xp, Integer dailyCap, double samePairFactor) {}
}
