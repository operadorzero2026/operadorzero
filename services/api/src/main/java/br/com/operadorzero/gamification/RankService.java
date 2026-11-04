package br.com.operadorzero.gamification;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.web.BusinessException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Patente visivel: XP, patente atual, progresso ate a proxima, ultimos ganhos e a comemoracao de promocao (uma vez por
 * patente). O administrador ajusta os valores de XP por acao e as faixas das patentes sem deploy.
 */
@Service
public class RankService {
    public record RankView(String username, String rankCode, String rankName, int position, int totalXp, int currentMinXp,
                           String nextRankCode, String nextRankName, Integer nextMinXp, double progress, Instant promotedAt) {}

    public record XpLine(String action, String description, int xp, Instant at) {}

    /** Minha patente: inclui os ultimos ganhos e se ha uma promocao ainda nao comemorada. */
    public record MyRank(RankView rank, boolean promotionPending, List<XpLine> recent) {}

    public record Rule(String actionCode, int xp, Integer dailyCap, BigDecimal samePairFactor, boolean active, String description) {}

    public record Tier(String code, String name, int position, int minXp) {}

    public record Settings(List<Rule> rules, List<Tier> tiers) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditEventRepository audit;
    private final Clock clock;

    @Autowired
    public RankService(NamedParameterJdbcTemplate jdbc, AuditEventRepository audit) {
        this(jdbc, audit, Clock.systemUTC());
    }

    RankService(NamedParameterJdbcTemplate jdbc, AuditEventRepository audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RankView of(String username) {
        return jdbc.query(RANK_SQL + " WHERE lower(u.username) = lower(:name) AND u.status = 'ACTIVE'", Map.of("name", username),
            (r, i) -> view(r)).stream().findFirst().orElseThrow(() -> BusinessException.notFound("Operador não encontrado."));
    }

    @Transactional(readOnly = true)
    public MyRank mine(AuthenticatedUser user) {
        RankView rank = jdbc.query(RANK_SQL + " WHERE u.id = :id", Map.of("id", user.internalId()), (r, i) -> view(r))
            .stream().findFirst().orElseThrow(() -> BusinessException.notFound("Operador não encontrado."));
        Boolean pending = jdbc.query("""
            SELECT promoted_at IS NOT NULL AND (promotion_seen_at IS NULL OR promotion_seen_at < promoted_at) pending
            FROM operator_xp WHERE user_id = :id
            """, Map.of("id", user.internalId()), (r, i) -> r.getBoolean(1)).stream().findFirst().orElse(false);
        List<XpLine> recent = jdbc.query("""
            SELECT e.action_code, COALESCE(r.description, e.action_code) description, e.awarded_xp, e.created_at
            FROM xp_event e LEFT JOIN xp_rule r ON r.action_code = e.action_code
            WHERE e.user_id = :id AND e.awarded_xp <> 0
            ORDER BY e.created_at DESC, e.id DESC LIMIT 12
            """, Map.of("id", user.internalId()), (r, i) -> new XpLine(r.getString(1), r.getString(2), r.getInt(3), r.getTimestamp(4).toInstant()));
        return new MyRank(rank, pending, recent);
    }

    /** O app mostrou a comemoracao: nao mostrar de novo ate a proxima promocao. */
    @Transactional
    public void promotionSeen(AuthenticatedUser user) {
        jdbc.update("UPDATE operator_xp SET promotion_seen_at = :now WHERE user_id = :id",
            Map.of("id", user.internalId(), "now", Timestamp.from(clock.instant())));
    }

    // ---- administracao ----

    @Transactional(readOnly = true)
    public Settings settings() {
        return new Settings(
            jdbc.query("SELECT action_code, xp, daily_cap, same_pair_factor, active, description FROM xp_rule ORDER BY action_code",
                Map.of(), (r, i) -> new Rule(r.getString(1), r.getInt(2), (Integer) r.getObject(3), r.getBigDecimal(4), r.getBoolean(5), r.getString(6))),
            jdbc.query("SELECT code, name, position, min_xp FROM rank_tier ORDER BY position", Map.of(),
                (r, i) -> new Tier(r.getString(1), r.getString(2), r.getInt(3), r.getInt(4))));
    }

    /** Novos valores valem para as proximas acoes; o XP ja ganho nao muda. */
    @Transactional
    public void updateRule(AuthenticatedUser admin, String actionCode, int xp, Integer dailyCap, BigDecimal samePairFactor, boolean active) {
        if (xp < 0 || xp > 1000) throw BusinessException.badRequest("INVALID_XP", "O XP por ação deve ficar entre 0 e 1000.");
        if (dailyCap != null && (dailyCap < 1 || dailyCap > 1000)) throw BusinessException.badRequest("INVALID_XP", "O limite diário deve ficar entre 1 e 1000, ou vazio.");
        if (samePairFactor == null || samePairFactor.signum() < 0 || samePairFactor.compareTo(BigDecimal.ONE) > 0) {
            throw BusinessException.badRequest("INVALID_XP", "O fator entre as mesmas contas deve ficar entre 0 e 1.");
        }
        int updated = jdbc.update("""
            UPDATE xp_rule SET xp = :xp, daily_cap = :cap, same_pair_factor = :factor, active = :active, updated_at = :now WHERE action_code = :code
            """, new MapSqlParameterSource().addValue("xp", xp).addValue("cap", dailyCap).addValue("factor", samePairFactor)
            .addValue("active", active).addValue("now", Timestamp.from(clock.instant())).addValue("code", actionCode));
        if (updated == 0) throw BusinessException.notFound("Ação não encontrada.");
        audit.record(admin.internalId(), "XP_RULE_UPDATED", "PLATFORM", null, actionCode + "=" + xp, clock.instant());
    }

    /**
     * Novas faixas de XP (minimo de cada patente, crescente, Recruta sempre 0). As patentes de todos sao recalculadas sem
     * aviso de promocao (a mudanca e da regra, nao merito do operador).
     */
    @Transactional
    public void updateTiers(AuthenticatedUser admin, Map<String, Integer> minXpByCode) {
        List<Tier> current = settings().tiers();
        if (minXpByCode.size() != current.size() || !minXpByCode.keySet().containsAll(current.stream().map(Tier::code).toList())) {
            throw BusinessException.badRequest("INVALID_TIERS", "Informe o XP mínimo de todas as patentes.");
        }
        int previous = -1;
        for (Tier tier : current) {
            int min = minXpByCode.get(tier.code());
            if (tier.position() == 1 ? min != 0 : min <= previous || min >= 10_000_000) {
                throw BusinessException.badRequest("INVALID_TIERS", "Recruta começa em 0 e cada patente precisa de mais XP que a anterior.");
            }
            previous = min;
        }
        // Duas fases para nao esbarrar na unicidade de min_xp durante a troca (valores temporarios acima de qualquer faixa valida).
        jdbc.update("UPDATE rank_tier SET min_xp = 10000000 + position", Map.of());
        for (Tier tier : current) {
            jdbc.update("UPDATE rank_tier SET min_xp = :min WHERE code = :code", Map.of("min", minXpByCode.get(tier.code()), "code", tier.code()));
        }
        jdbc.update("""
            UPDATE operator_xp x SET rank_code = (SELECT code FROM rank_tier WHERE min_xp <= x.total_xp ORDER BY min_xp DESC LIMIT 1)
            """, Map.of());
        audit.record(admin.internalId(), "RANK_TIERS_UPDATED", "PLATFORM", null, null, clock.instant());
    }

    private static final String RANK_SQL = """
        SELECT u.username, COALESCE(x.total_xp, 0) total_xp, x.promoted_at, t.code, t.name, t.position, t.min_xp,
               n.code next_code, n.name next_name, n.min_xp next_min
        FROM app_user u
        LEFT JOIN operator_xp x ON x.user_id = u.id
        JOIN rank_tier t ON t.code = (SELECT code FROM rank_tier WHERE min_xp <= COALESCE(x.total_xp, 0) ORDER BY min_xp DESC LIMIT 1)
        LEFT JOIN rank_tier n ON n.position = t.position + 1
        """;

    private static RankView view(ResultSet r) throws SQLException {
        int total = r.getInt("total_xp");
        int min = r.getInt("min_xp");
        Integer next = (Integer) r.getObject("next_min");
        double progress = next == null ? 1 : Math.max(0, Math.min(1, (double) (total - min) / Math.max(1, next - min)));
        Timestamp promoted = r.getTimestamp("promoted_at");
        return new RankView(r.getString("username"), r.getString("code"), r.getString("name"), r.getInt("position"), total, min,
            r.getString("next_code"), r.getString("next_name"), next, progress, promoted == null ? null : promoted.toInstant());
    }
}
