package br.com.operadorzero.feed;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class FeedPostRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public FeedPostRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record MediaForPost(long id, String purpose, String status, boolean attached) {}
    public record PostState(long id, UUID publicId, long authorUserId, String status) {}
    public record UserRef(long id, String username) {}

    public Optional<MediaForPost> lockMedia(UUID mediaId, long ownerUserId) {
        return jdbc.query("""
            SELECT a.id, a.purpose, a.status,
                   EXISTS (SELECT 1 FROM feed_post_media pm WHERE pm.media_asset_id = a.id) attached
            FROM media_asset a WHERE a.public_id = :mediaId AND a.owner_user_id = :userId
            FOR UPDATE
            """, Map.of("mediaId", mediaId, "userId", ownerUserId),
            (r, i) -> new MediaForPost(r.getLong("id"), r.getString("purpose"), r.getString("status"), r.getBoolean("attached")))
            .stream().findFirst();
    }

    public Optional<PostState> byIdempotency(long authorUserId, UUID key) {
        return jdbc.query("SELECT id, public_id, author_user_id, status FROM feed_post WHERE author_user_id = :userId AND idempotency_key = :key",
            Map.of("userId", authorUserId, "key", key), (r, i) -> state(r)).stream().findFirst();
    }

    public Optional<Long> activeField(UUID fieldId) {
        return jdbc.query("SELECT id FROM airsoft_field WHERE public_id = :id AND status = 'ACTIVE'", Map.of("id", fieldId),
            (r, i) -> r.getLong(1)).stream().findFirst();
    }

    /** So a equipe atual do proprio autor pode ser marcada (ninguem posta "em nome" de outra equipe). */
    public Optional<Long> ownActiveTeam(UUID teamId, long userId) {
        return jdbc.query("""
            SELECT t.id FROM team t JOIN team_member tm ON tm.team_id = t.id AND tm.user_id = :userId AND tm.left_at IS NULL
            WHERE t.public_id = :teamId AND t.status = 'ACTIVE'
            """, Map.of("teamId", teamId, "userId", userId), (r, i) -> r.getLong(1)).stream().findFirst();
    }

    /** Usuarios ativos, sem bloqueio com o autor; nomes desconhecidos sao ignorados. */
    public List<UserRef> reachableUsers(List<String> usernames, long authorUserId) {
        if (usernames.isEmpty()) return List.of();
        return jdbc.query("""
            SELECT u.id, u.username FROM app_user u
            WHERE lower(u.username) IN (:usernames) AND u.status = 'ACTIVE' AND u.id <> :author
              AND NOT EXISTS (SELECT 1 FROM operator_block ob
                  WHERE (ob.blocker_user_id = :author AND ob.blocked_user_id = u.id)
                     OR (ob.blocker_user_id = u.id AND ob.blocked_user_id = :author))
            """, Map.of("usernames", usernames, "author", authorUserId), (r, i) -> new UserRef(r.getLong("id"), r.getString("username")));
    }

    public PostState insert(long authorUserId, String caption, Long fieldId, Long teamId, boolean publicShare, boolean published,
                            UUID idempotencyKey, Instant now) {
        return jdbc.queryForObject("""
            INSERT INTO feed_post(author_user_id, caption, field_id, team_id, public_share, status, idempotency_key, created_at, published_at, updated_at)
            VALUES (:author, :caption, :fieldId, :teamId, :publicShare, :status, :key, :now, :publishedAt, :now)
            RETURNING id, public_id, author_user_id, status
            """, new MapSqlParameterSource().addValue("author", authorUserId).addValue("caption", caption).addValue("fieldId", fieldId)
                .addValue("teamId", teamId).addValue("publicShare", publicShare).addValue("status", published ? "PUBLISHED" : "PROCESSING")
                .addValue("key", idempotencyKey).addValue("now", Timestamp.from(now)).addValue("publishedAt", published ? Timestamp.from(now) : null),
            (r, i) -> state(r));
    }

    public void attach(long postId, long mediaId, List<String> hashtags, List<Long> mentioned, List<Long> tagged) {
        jdbc.update("INSERT INTO feed_post_media(post_id, media_asset_id, position) VALUES (:post, :media, 0)", Map.of("post", postId, "media", mediaId));
        jdbc.update("INSERT INTO feed_post_metrics(post_id) VALUES (:post)", Map.of("post", postId));
        batch("INSERT INTO feed_post_hashtag(post_id, tag) VALUES (:post, :value)", postId, hashtags);
        batch("INSERT INTO feed_post_mention(post_id, user_id) VALUES (:post, :value)", postId, mentioned);
        batch("INSERT INTO feed_post_tag(post_id, user_id) VALUES (:post, :value)", postId, tagged);
    }

    /** Publica os posts que so esperavam esta midia (todas as midias do post prontas). */
    public List<PostState> publishWaitingFor(long mediaId, Instant now) {
        return jdbc.query("""
            UPDATE feed_post fp SET status = 'PUBLISHED', published_at = :now, updated_at = :now
            WHERE fp.status = 'PROCESSING'
              AND EXISTS (SELECT 1 FROM feed_post_media pm WHERE pm.post_id = fp.id AND pm.media_asset_id = :media)
              AND NOT EXISTS (SELECT 1 FROM feed_post_media pm JOIN media_asset a ON a.id = pm.media_asset_id
                  WHERE pm.post_id = fp.id AND a.status <> 'READY')
            RETURNING fp.id, fp.public_id, fp.author_user_id, fp.status
            """, Map.of("media", mediaId, "now", Timestamp.from(now)), (r, i) -> state(r));
    }

    public List<PostState> failWaitingFor(long mediaId, Instant now) {
        return jdbc.query("""
            UPDATE feed_post fp SET status = 'FAILED', updated_at = :now
            WHERE fp.status = 'PROCESSING'
              AND EXISTS (SELECT 1 FROM feed_post_media pm WHERE pm.post_id = fp.id AND pm.media_asset_id = :media)
            RETURNING fp.id, fp.public_id, fp.author_user_id, fp.status
            """, Map.of("media", mediaId, "now", Timestamp.from(now)), (r, i) -> state(r));
    }

    public List<Long> notifiedUsers(long postId) {
        return jdbc.query("SELECT user_id FROM feed_post_mention WHERE post_id = :post UNION SELECT user_id FROM feed_post_tag WHERE post_id = :post",
            Map.of("post", postId), (r, i) -> r.getLong(1));
    }

    public Optional<PostState> lockOwn(UUID postId, long authorUserId) {
        return jdbc.query("SELECT id, public_id, author_user_id, status FROM feed_post WHERE public_id = :id AND author_user_id = :author FOR UPDATE",
            Map.of("id", postId, "author", authorUserId), (r, i) -> state(r)).stream().findFirst();
    }

    public void markDeleted(long postId, Instant now) {
        jdbc.update("UPDATE feed_post SET status = 'DELETED', updated_at = :now WHERE id = :id", Map.of("id", postId, "now", Timestamp.from(now)));
    }

    public String callsign(long userId) {
        return jdbc.query("SELECT callsign FROM operator_profile WHERE user_id = :id", Map.of("id", userId), (r, i) -> r.getString(1))
            .stream().findFirst().orElse("Um operador");
    }

    private <T> void batch(String sql, long postId, List<T> values) {
        if (values.isEmpty()) return;
        SqlParameterSource[] rows = values.stream().map(value -> new MapSqlParameterSource().addValue("post", postId).addValue("value", value))
            .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(sql, rows);
    }

    private static PostState state(java.sql.ResultSet r) throws java.sql.SQLException {
        return new PostState(r.getLong("id"), r.getObject("public_id", UUID.class), r.getLong("author_user_id"), r.getString("status"));
    }
}
