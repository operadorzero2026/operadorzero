package br.com.operadorzero.gamification;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DecorationRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public DecorationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Target(long id, UUID publicId, long authorUserId) {}
    public record HonorRow(long postId, long decorations) {}

    /** Post publicado, autor ativo e sem bloqueio entre as contas. */
    public Optional<Target> target(UUID postId, long giver) {
        return jdbc.query("""
            SELECT fp.id, fp.public_id, fp.author_user_id FROM feed_post fp
            JOIN app_user u ON u.id = fp.author_user_id AND u.status = 'ACTIVE'
            WHERE fp.public_id = :post AND fp.status = 'PUBLISHED'
              AND NOT EXISTS (SELECT 1 FROM operator_block ob
                  WHERE (ob.blocker_user_id = :giver AND ob.blocked_user_id = fp.author_user_id)
                     OR (ob.blocker_user_id = fp.author_user_id AND ob.blocked_user_id = :giver))
            """, Map.of("post", postId, "giver", giver),
            (r, i) -> new Target(r.getLong(1), r.getObject(2, UUID.class), r.getLong(3))).stream().findFirst();
    }

    /** Serializa as condecoracoes de quem da, para a cota semanal nao ser furada por cliques simultaneos. */
    public void lockGiver(long giver) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext('decoration'), CAST(:giver AS int))", Map.of("giver", giver));
    }

    public int usedThisWeek(long giver, LocalDate weekStart) {
        Integer used = jdbc.queryForObject("SELECT count(*) FROM decoration WHERE giver_user_id = :giver AND week_start = :week",
            Map.of("giver", giver, "week", Date.valueOf(weekStart)), Integer.class);
        return used == null ? 0 : used;
    }

    public boolean alreadyDecorated(long giver, long postId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM decoration WHERE giver_user_id = :giver AND post_id = :post)",
            Map.of("giver", giver, "post", postId), Boolean.class));
    }

    public long insert(long giver, long postId, LocalDate weekStart, Instant now) {
        return jdbc.queryForObject("""
            INSERT INTO decoration(giver_user_id, post_id, week_start, created_at) VALUES (:giver, :post, :week, :now) RETURNING id
            """, Map.of("giver", giver, "post", postId, "week", Date.valueOf(weekStart), "now", Timestamp.from(now)), Long.class);
    }

    public long bumpPost(long postId, Instant now) {
        return jdbc.queryForObject("""
            INSERT INTO feed_post_metrics(post_id, decorations, updated_at) VALUES (:post, 1, :now)
            ON CONFLICT (post_id) DO UPDATE SET decorations = feed_post_metrics.decorations + 1, updated_at = :now
            RETURNING decorations
            """, Map.of("post", postId, "now", Timestamp.from(now)), Long.class);
    }

    public int weeklyQuota(int fallback) {
        return jdbc.query("SELECT value::text FROM gamification_setting WHERE key = 'decoration.weekly_quota'", Map.of(), (r, i) -> r.getString(1))
            .stream().findFirst().map(value -> {
                try { return Math.max(0, (int) Double.parseDouble(value)); } catch (NumberFormatException e) { return fallback; }
            }).orElse(fallback);
    }

    /** Posts mais condecorados na semana (empate: o que chegou primeiro ao total). */
    public List<HonorRow> honorBoard(LocalDate weekStart, long viewer, int limit) {
        return jdbc.query("""
            SELECT d.post_id, count(*) total, max(d.created_at) last_at FROM decoration d
            JOIN feed_post fp ON fp.id = d.post_id AND fp.status = 'PUBLISHED'
            JOIN app_user u ON u.id = fp.author_user_id AND u.status = 'ACTIVE'
            WHERE d.week_start = :week
              AND NOT EXISTS (SELECT 1 FROM operator_block ob
                  WHERE (ob.blocker_user_id = :viewer AND ob.blocked_user_id = fp.author_user_id)
                     OR (ob.blocker_user_id = fp.author_user_id AND ob.blocked_user_id = :viewer))
            GROUP BY d.post_id
            ORDER BY total DESC, last_at, d.post_id
            LIMIT :limit
            """, Map.of("week", Date.valueOf(weekStart), "viewer", viewer, "limit", limit), (r, i) -> new HonorRow(r.getLong(1), r.getLong(2)));
    }
}
