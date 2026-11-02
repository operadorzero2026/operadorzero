package br.com.operadorzero.feed;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FeedInteractionRepository {
    /** Contadores que podem ser ajustados; o nome da coluna nunca vem do cliente. */
    private static final Set<String> COUNTERS = Set.of("on_target", "eliminated", "comments", "shares", "saves");
    private static final String NOT_BLOCKED = """
        NOT EXISTS (SELECT 1 FROM operator_block ob
          WHERE (ob.blocker_user_id = :viewer AND ob.blocked_user_id = fp.author_user_id)
             OR (ob.blocker_user_id = fp.author_user_id AND ob.blocked_user_id = :viewer))
        """;

    private final NamedParameterJdbcTemplate jdbc;

    public FeedInteractionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record VisiblePost(long id, UUID publicId, long authorUserId, boolean publicShare) {}
    public record CommentRow(long id, UUID publicId, UUID parentId, String authorUsername, String authorCallsign, String rankCode,
                             String body, Instant createdAt, boolean own, boolean canDelete, int replies) {}
    public record CommentRef(long id, UUID publicId, long postId, long authorUserId, long postAuthorUserId, Long parentId) {}
    public record ReportRow(UUID id, String targetType, UUID targetId, String excerpt, String reporterUsername, String reason,
                            String details, String status, Instant createdAt, String targetStatus) {}

    /** Post publicado, de autor ativo e sem bloqueio entre as duas contas. */
    public Optional<VisiblePost> visiblePost(UUID postId, long viewer) {
        return jdbc.query("""
            SELECT fp.id, fp.public_id, fp.author_user_id, fp.public_share FROM feed_post fp
            JOIN app_user u ON u.id = fp.author_user_id AND u.status = 'ACTIVE'
            WHERE fp.public_id = :post AND fp.status = 'PUBLISHED' AND""" + " " + NOT_BLOCKED,
            Map.of("post", postId, "viewer", viewer), (r, i) -> new VisiblePost(r.getLong("id"), r.getObject("public_id", UUID.class),
                r.getLong("author_user_id"), r.getBoolean("public_share"))).stream().findFirst();
    }

    public boolean addReaction(long postId, long userId, String kind, Instant now) {
        return jdbc.update("INSERT INTO feed_reaction(post_id, user_id, kind, created_at) VALUES (:post, :user, :kind, :now) ON CONFLICT DO NOTHING",
            Map.of("post", postId, "user", userId, "kind", kind, "now", Timestamp.from(now))) > 0;
    }

    public boolean removeReaction(long postId, long userId, String kind) {
        return jdbc.update("DELETE FROM feed_reaction WHERE post_id = :post AND user_id = :user AND kind = :kind",
            Map.of("post", postId, "user", userId, "kind", kind)) > 0;
    }

    public boolean save(long postId, long userId, Instant now) {
        return jdbc.update("INSERT INTO feed_save(user_id, post_id, created_at) VALUES (:user, :post, :now) ON CONFLICT DO NOTHING",
            Map.of("post", postId, "user", userId, "now", Timestamp.from(now))) > 0;
    }

    public boolean unsave(long postId, long userId) {
        return jdbc.update("DELETE FROM feed_save WHERE post_id = :post AND user_id = :user", Map.of("post", postId, "user", userId)) > 0;
    }

    public void share(long postId, long userId, String channel, Instant now) {
        jdbc.update("INSERT INTO feed_share(post_id, user_id, channel, created_at) VALUES (:post, :user, :channel, :now)",
            Map.of("post", postId, "user", userId, "channel", channel, "now", Timestamp.from(now)));
    }

    public void bump(long postId, String counter, int delta, Instant now) {
        if (!COUNTERS.contains(counter)) throw new IllegalArgumentException(counter);
        jdbc.update("""
            INSERT INTO feed_post_metrics(post_id, %1$s, updated_at) VALUES (:post, GREATEST(:delta, 0), :now)
            ON CONFLICT (post_id) DO UPDATE SET %1$s = GREATEST(feed_post_metrics.%1$s + :delta, 0), updated_at = :now
            """.formatted(counter), Map.of("post", postId, "delta", delta, "now", Timestamp.from(now)));
    }

    public Map<String, Long> counts(long postId) {
        return jdbc.query("SELECT on_target, eliminated, comments, shares, saves FROM feed_post_metrics WHERE post_id = :post",
            Map.of("post", postId), (r, i) -> Map.of("onTarget", r.getLong(1), "eliminated", r.getLong(2), "comments", r.getLong(3),
                "shares", r.getLong(4), "saves", r.getLong(5))).stream().findFirst()
            .orElse(Map.of("onTarget", 0L, "eliminated", 0L, "comments", 0L, "shares", 0L, "saves", 0L));
    }

    // ---- Seguir ----

    public record UserRef(long id, String username) {}

    public Optional<UserRef> followable(String username, long viewer) {
        return jdbc.query("""
            SELECT u.id, u.username FROM app_user u
            WHERE lower(u.username) = lower(:username) AND u.status = 'ACTIVE' AND u.id <> :viewer
              AND NOT EXISTS (SELECT 1 FROM operator_block ob
                  WHERE (ob.blocker_user_id = :viewer AND ob.blocked_user_id = u.id)
                     OR (ob.blocker_user_id = u.id AND ob.blocked_user_id = :viewer))
            """, Map.of("username", username, "viewer", viewer), (r, i) -> new UserRef(r.getLong(1), r.getString(2))).stream().findFirst();
    }

    public boolean follow(long follower, long followed, Instant now) {
        return jdbc.update("INSERT INTO operator_follow(follower_user_id, followed_user_id, created_at) VALUES (:a, :b, :now) ON CONFLICT DO NOTHING",
            Map.of("a", follower, "b", followed, "now", Timestamp.from(now))) > 0;
    }

    public boolean unfollow(long follower, String username) {
        return jdbc.update("""
            DELETE FROM operator_follow WHERE follower_user_id = :a
              AND followed_user_id = (SELECT id FROM app_user WHERE lower(username) = lower(:username))
            """, Map.of("a", follower, "username", username)) > 0;
    }

    public Map<String, Object> followStats(String username, long viewer) {
        return jdbc.queryForMap("""
            SELECT (SELECT count(*) FROM operator_follow WHERE followed_user_id = u.id) followers,
                   (SELECT count(*) FROM operator_follow WHERE follower_user_id = u.id) following,
                   EXISTS (SELECT 1 FROM operator_follow WHERE follower_user_id = :viewer AND followed_user_id = u.id) follows
            FROM app_user u WHERE lower(u.username) = lower(:username)
            """, Map.of("username", username, "viewer", viewer));
    }

    // ---- Comentarios ----

    public UUID insertComment(long postId, long authorId, Long parentId, String body, UUID idempotencyKey, Instant now) {
        // ON CONFLICT em vez de capturar a excecao: no Postgres, um erro de chave invalida a transacao inteira.
        return jdbc.query("""
            INSERT INTO feed_comment(post_id, author_user_id, parent_comment_id, body, idempotency_key, created_at, updated_at)
            VALUES (:post, :author, :parent, :body, :key, :now, :now) ON CONFLICT DO NOTHING RETURNING public_id
            """, new MapSqlParameterSource().addValue("post", postId).addValue("author", authorId).addValue("parent", parentId)
                .addValue("body", body).addValue("key", idempotencyKey).addValue("now", Timestamp.from(now)),
            (r, i) -> r.getObject(1, UUID.class)).stream().findFirst().orElse(null);
    }

    public Optional<CommentRef> comment(UUID commentId) {
        return jdbc.query("""
            SELECT c.id, c.public_id, c.post_id, c.author_user_id, fp.author_user_id post_author, c.parent_comment_id
            FROM feed_comment c JOIN feed_post fp ON fp.id = c.post_id
            WHERE c.public_id = :id AND c.status = 'PUBLISHED'
            """, Map.of("id", commentId), (r, i) -> new CommentRef(r.getLong("id"), r.getObject("public_id", UUID.class), r.getLong("post_id"),
                r.getLong("author_user_id"), r.getLong("post_author"), (Long) r.getObject("parent_comment_id"))).stream().findFirst();
    }

    public Optional<Long> idForComment(long authorId, UUID idempotencyKey) {
        return jdbc.query("SELECT id FROM feed_comment WHERE author_user_id = :author AND idempotency_key = :key",
            Map.of("author", authorId, "key", idempotencyKey), (r, i) -> r.getLong(1)).stream().findFirst();
    }

    public Optional<Long> commentId(UUID publicId) {
        return jdbc.query("SELECT id FROM feed_comment WHERE public_id = :id", Map.of("id", publicId), (r, i) -> r.getLong(1)).stream().findFirst();
    }

    /** Comentarios de primeiro nivel (mais antigos primeiro) ou respostas de um comentario; esconde autores bloqueados. */
    public List<CommentRow> comments(long postId, Long parentId, long viewer, long postAuthor, Instant after, int limit) {
        return jdbc.query("""
            SELECT c.id, c.public_id, parent.public_id parent_public_id, u.username, p.callsign, COALESCE(x.rank_code, 'RECRUTA') rank_code,
                   c.body, c.created_at, c.author_user_id = :viewer own,
                   (SELECT count(*) FROM feed_comment r WHERE r.parent_comment_id = c.id AND r.status = 'PUBLISHED') replies
            FROM feed_comment c
            JOIN app_user u ON u.id = c.author_user_id AND u.status = 'ACTIVE'
            JOIN operator_profile p ON p.user_id = u.id
            LEFT JOIN operator_xp x ON x.user_id = u.id
            LEFT JOIN feed_comment parent ON parent.id = c.parent_comment_id
            WHERE c.post_id = :post AND c.status = 'PUBLISHED'
              AND c.parent_comment_id IS NOT DISTINCT FROM :parent
              AND (CAST(:after AS timestamptz) IS NULL OR c.created_at > CAST(:after AS timestamptz))
              AND NOT EXISTS (SELECT 1 FROM operator_block ob
                  WHERE (ob.blocker_user_id = :viewer AND ob.blocked_user_id = c.author_user_id)
                     OR (ob.blocker_user_id = c.author_user_id AND ob.blocked_user_id = :viewer))
            ORDER BY c.created_at, c.id
            LIMIT :limit
            """, new MapSqlParameterSource().addValue("post", postId).addValue("parent", parentId).addValue("viewer", viewer)
                .addValue("after", after == null ? null : Timestamp.from(after)).addValue("limit", limit),
            (r, i) -> commentRow(r, viewer, postAuthor));
    }

    public record DeletedComment(long id, long authorUserId) {}

    /** Apaga o comentario e suas respostas; devolve cada um para ajustar contagem e estornar XP. */
    public List<DeletedComment> deleteComment(long commentId, Instant now) {
        return jdbc.query("""
            UPDATE feed_comment SET status = 'DELETED', updated_at = :now
            WHERE (id = :id OR parent_comment_id = :id) AND status = 'PUBLISHED'
            RETURNING id, author_user_id
            """, Map.of("id", commentId, "now", Timestamp.from(now)), (r, i) -> new DeletedComment(r.getLong(1), r.getLong(2)));
    }

    // ---- Denuncias e moderacao ----

    public boolean reportPost(long reporterId, long postId, String reason, String details, Instant now) {
        return report("feed_post_id", reporterId, postId, reason, details, now);
    }

    public boolean reportComment(long reporterId, long commentId, String reason, String details, Instant now) {
        return report("feed_comment_id", reporterId, commentId, reason, details, now);
    }

    private boolean report(String column, long reporterId, long targetId, String reason, String details, Instant now) {
        // Ja existe denuncia aberta da mesma pessoa para o mesmo conteudo: nada a fazer.
        return jdbc.update("INSERT INTO community_report(reporter_user_id, " + column + ", reason, details, created_at) "
            + "VALUES (:reporter, :target, :reason, :details, :now) ON CONFLICT DO NOTHING", new MapSqlParameterSource().addValue("reporter", reporterId)
                .addValue("target", targetId).addValue("reason", reason).addValue("details", details).addValue("now", Timestamp.from(now))) > 0;
    }

    public List<ReportRow> feedReports() {
        return jdbc.query("""
            SELECT r.public_id, CASE WHEN r.feed_post_id IS NOT NULL THEN 'POST' WHEN r.feed_comment_id IS NOT NULL THEN 'COMMENT' ELSE 'RECON' END target_type,
                   COALESCE(fp.public_id, fc.public_id, rc.public_id) target_id,
                   left(COALESCE(fp.caption, fc.body, rc.overlay_text, ''), 140) excerpt,
                   reporter.username, r.reason, r.details, r.status, r.created_at,
                   COALESCE(fp.status, fc.status, rc.status) target_status
            FROM community_report r
            JOIN app_user reporter ON reporter.id = r.reporter_user_id
            LEFT JOIN feed_post fp ON fp.id = r.feed_post_id
            LEFT JOIN feed_comment fc ON fc.id = r.feed_comment_id
            LEFT JOIN recon rc ON rc.id = r.recon_id
            WHERE r.feed_post_id IS NOT NULL OR r.feed_comment_id IS NOT NULL OR r.recon_id IS NOT NULL
            ORDER BY CASE WHEN r.status = 'OPEN' THEN 0 ELSE 1 END, r.created_at, r.id
            LIMIT 200
            """, Map.of(), (r, i) -> new ReportRow(r.getObject("public_id", UUID.class), r.getString("target_type"),
                r.getObject("target_id", UUID.class), r.getString("excerpt"), r.getString("username"), r.getString("reason"),
                r.getString("details"), r.getString("status"), r.getTimestamp("created_at").toInstant(), r.getString("target_status")));
    }

    public record ReportTarget(long reportId, Long postId, Long commentId, Long reconId) {}

    public Optional<ReportTarget> openReport(UUID reportId) {
        return jdbc.query("SELECT id, feed_post_id, feed_comment_id, recon_id FROM community_report WHERE public_id = :id AND status = 'OPEN' FOR UPDATE",
            Map.of("id", reportId), (r, i) -> new ReportTarget(r.getLong(1), (Long) r.getObject(2), (Long) r.getObject(3), (Long) r.getObject(4)))
            .stream().findFirst();
    }

    /** Fecha a denuncia e todas as outras abertas contra o mesmo conteudo. */
    public void resolveReports(ReportTarget target, long reviewerId, String status, String resolution, Instant now) {
        jdbc.update("""
            UPDATE community_report SET status = :status, reviewed_by = :reviewer, resolution = :resolution, reviewed_at = :now
            WHERE status = 'OPEN' AND (id = :id
               OR (CAST(:post AS bigint) IS NOT NULL AND feed_post_id = :post)
               OR (CAST(:comment AS bigint) IS NOT NULL AND feed_comment_id = :comment)
               OR (CAST(:recon AS bigint) IS NOT NULL AND recon_id = :recon))
            """, new MapSqlParameterSource().addValue("status", status).addValue("reviewer", reviewerId).addValue("resolution", resolution)
                .addValue("now", Timestamp.from(now)).addValue("id", target.reportId()).addValue("post", target.postId())
                .addValue("comment", target.commentId()).addValue("recon", target.reconId()));
    }

    public void suspendTarget(ReportTarget target, Instant now) {
        var params = new MapSqlParameterSource().addValue("now", Timestamp.from(now));
        if (target.postId() != null) jdbc.update("UPDATE feed_post SET status = 'SUSPENDED', updated_at = :now WHERE id = :id", params.addValue("id", target.postId()));
        if (target.commentId() != null) jdbc.update("UPDATE feed_comment SET status = 'SUSPENDED', updated_at = :now WHERE id = :id", params.addValue("id", target.commentId()));
        if (target.reconId() != null) jdbc.update("UPDATE recon SET status = 'SUSPENDED' WHERE id = :id", params.addValue("id", target.reconId()));
    }

    public Optional<Long> visibleRecon(UUID reconId, long viewer) {
        return jdbc.query("""
            SELECT r.id FROM recon r WHERE r.public_id = :id AND r.status = 'ACTIVE'
              AND NOT EXISTS (SELECT 1 FROM operator_block ob
                  WHERE (ob.blocker_user_id = :viewer AND ob.blocked_user_id = r.author_user_id)
                     OR (ob.blocker_user_id = r.author_user_id AND ob.blocked_user_id = :viewer))
            """, Map.of("id", reconId, "viewer", viewer), (r, i) -> r.getLong(1)).stream().findFirst();
    }

    public boolean reportRecon(long reporterId, long reconId, String reason, String details, Instant now) {
        return report("recon_id", reporterId, reconId, reason, details, now);
    }

    private static CommentRow commentRow(ResultSet r, long viewer, long postAuthor) throws SQLException {
        boolean own = r.getBoolean("own");
        return new CommentRow(r.getLong("id"), r.getObject("public_id", UUID.class), r.getObject("parent_public_id", UUID.class),
            r.getString("username"), r.getString("callsign"), r.getString("rank_code"), r.getString("body"),
            r.getTimestamp("created_at").toInstant(), own, own || viewer == postAuthor, r.getInt("replies"));
    }
}
