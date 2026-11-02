package br.com.operadorzero.feed;

import br.com.operadorzero.feed.FeedDtos.AuthorSummary;
import br.com.operadorzero.feed.FeedDtos.Counts;
import br.com.operadorzero.feed.FeedDtos.PlaceSummary;
import br.com.operadorzero.feed.FeedDtos.TeamSummary;
import br.com.operadorzero.feed.FeedDtos.ViewerState;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FeedRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public FeedRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Pagina cronologica (mais recente primeiro), com cursor (published_at, public_id). */
    public List<PostRow> chronological(long viewerId, boolean followingOnly, Instant cursorAt, UUID cursorId, int limit) {
        return query(viewerId, followingOnly, null, null, false, cursorAt, cursorId, limit);
    }

    public record RankedId(long id, UUID publicId, double score) {}

    /**
     * Pagina do "Para voce" ordenada pela pontuacao de {@link FeedRanking}. {@code anchor} fixa o "agora" da primeira
     * pagina: idade, posts novos e "ja visto" sao medidos contra ele, entao as paginas seguintes nao se embaralham.
     */
    public List<RankedId> ranked(long viewer, Instant anchor, Double cursorScore, UUID cursorId, int limit) {
        var params = new MapSqlParameterSource().addValue("viewer", viewer).addValue("anchor", Timestamp.from(anchor))
            .addValue("since", Timestamp.from(anchor.minus(FeedRanking.WINDOW)))
            .addValue("seenSince", Timestamp.from(anchor.minus(FeedRanking.SEEN_WINDOW)))
            .addValue("cursorScore", cursorScore).addValue("cursorId", cursorId).addValue("limit", limit);
        FeedRanking.WEIGHTS.forEach(params::addValue);
        return jdbc.query("""
            WITH me AS (
                SELECT p.city, p.state_code,
                       (SELECT tm.team_id FROM team_member tm WHERE tm.user_id = :viewer AND tm.left_at IS NULL LIMIT 1) team_id
                FROM operator_profile p WHERE p.user_id = :viewer
            ), played AS (
                SELECT o.field_id, o.modality FROM operation_participant op JOIN airsoft_operation o ON o.id = op.operation_id
                WHERE op.user_id = :viewer AND op.status IN ('CONFIRMED','CHECKED_IN')
            ), scored AS (
                SELECT fp.id, fp.public_id,
                  (1 + ln(1
                      + (COALESCE(m.on_target, 0) + COALESCE(m.eliminated, 0)) * :wReaction
                      + COALESCE(m.comments, 0) * :wComment + COALESCE(m.shares, 0) * :wShare + COALESCE(m.decorations, 0) * :wDecoration
                      + CASE WHEN COALESCE(m.unique_viewers, 0) > 0 THEN :wCompletion * LEAST(1.0, m.completions::float8 / m.unique_viewers) ELSE 0 END
                      + CASE WHEN COALESCE(m.views, 0) > 0 AND COALESCE(vd.duration_ms, 0) > 0
                             THEN :wWatch * LEAST(1.0, m.watched_ms::float8 / (m.views * vd.duration_ms)) ELSE 0 END))
                  * power(0.5, GREATEST(0, extract(epoch FROM (CAST(:anchor AS timestamptz) - fp.published_at))) / 3600.0 / :halfLifeHours)
                  * (1
                      + CASE WHEN COALESCE(f.state_code, ap.state_code) = me.state_code THEN :bState ELSE 0 END
                      + CASE WHEN COALESCE(f.state_code, ap.state_code) = me.state_code AND lower(COALESCE(f.city, ap.city)) = lower(me.city) THEN :bCity ELSE 0 END
                      + CASE WHEN fp.field_id IN (SELECT field_id FROM played) THEN :bField ELSE 0 END
                      + CASE WHEN me.team_id IS NOT NULL AND (fp.team_id = me.team_id OR EXISTS (SELECT 1 FROM team_member atm
                             WHERE atm.user_id = fp.author_user_id AND atm.team_id = me.team_id AND atm.left_at IS NULL)) THEN :bTeam ELSE 0 END
                      + CASE WHEN EXISTS (SELECT 1 FROM operator_follow ff WHERE ff.follower_user_id = :viewer AND ff.followed_user_id = fp.author_user_id)
                             THEN :bFollow ELSE 0 END
                      + CASE WHEN o.modality IN (SELECT modality FROM played) THEN :bModality ELSE 0 END
                    ) AS score
                FROM feed_post fp
                JOIN app_user u ON u.id = fp.author_user_id AND u.status = 'ACTIVE'
                JOIN operator_profile ap ON ap.user_id = u.id
                LEFT JOIN me ON TRUE
                LEFT JOIN airsoft_field f ON f.id = fp.field_id
                LEFT JOIN airsoft_operation o ON o.id = fp.operation_id
                LEFT JOIN feed_post_metrics m ON m.post_id = fp.id
                LEFT JOIN LATERAL (SELECT max(a.duration_ms) duration_ms FROM feed_post_media pm JOIN media_asset a ON a.id = pm.media_asset_id
                                   WHERE pm.post_id = fp.id) vd ON TRUE
                WHERE fp.status = 'PUBLISHED' AND fp.published_at >= :since AND fp.published_at <= :anchor
                  AND NOT EXISTS (SELECT 1 FROM operator_block ob
                      WHERE (ob.blocker_user_id = :viewer AND ob.blocked_user_id = fp.author_user_id)
                         OR (ob.blocker_user_id = fp.author_user_id AND ob.blocked_user_id = :viewer))
                  AND NOT EXISTS (SELECT 1 FROM feed_post_media pm JOIN media_asset ma ON ma.id = pm.media_asset_id
                      WHERE pm.post_id = fp.id AND ma.status <> 'READY')
                  AND NOT EXISTS (SELECT 1 FROM feed_view v WHERE v.post_id = fp.id AND v.user_id = :viewer
                      AND v.last_viewed_at >= :seenSince AND v.last_viewed_at < :anchor)
            )
            SELECT id, public_id, score FROM scored
            WHERE CAST(:cursorScore AS float8) IS NULL OR (score, public_id) < (CAST(:cursorScore AS float8), CAST(:cursorId AS uuid))
            ORDER BY score DESC, public_id DESC
            LIMIT :limit
            """, params, (r, i) -> new RankedId(r.getLong("id"), r.getObject("public_id", UUID.class), r.getDouble("score")));
    }

    /** Linhas completas (mesmo formato do feed) de posts ja escolhidos, na ordem pedida. */
    public List<PostRow> byIds(long viewerId, List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        Map<Long, PostRow> rows = new LinkedHashMap<>();
        query(viewerId, false, null, null, false, null, null, ids.size(), ids).forEach(row -> rows.put(row.id(), row));
        return ids.stream().map(rows::get).filter(java.util.Objects::nonNull).toList();
    }


    /** Mesma consulta do feed com filtros opcionais: hashtag, um post especifico e so posts compartilhaveis (pagina publica). */
    public List<PostRow> query(long viewerId, boolean followingOnly, String hashtag, UUID postId, boolean publicOnly,
                               Instant cursorAt, UUID cursorId, int limit) {
        return query(viewerId, followingOnly, hashtag, postId, publicOnly, cursorAt, cursorId, limit, null);
    }

    private List<PostRow> query(long viewerId, boolean followingOnly, String hashtag, UUID postId, boolean publicOnly,
                                Instant cursorAt, UUID cursorId, int limit, List<Long> ids) {
        return jdbc.query("""
            SELECT fp.id, fp.public_id, fp.post_type, fp.caption, fp.published_at,
                   u.username, p.callsign, COALESCE(x.rank_code, 'RECRUTA') rank_code,
                   f.public_id field_id, f.name field_name,
                   t.public_id team_id, t.name team_name, t.acronym team_acronym,
                   COALESCE(m.on_target, 0) on_target, COALESCE(m.eliminated, 0) eliminated,
                   COALESCE(m.comments, 0) comments, COALESCE(m.shares, 0) shares,
                   COALESCE(m.saves, 0) saves, COALESCE(m.decorations, 0) decorations,
                   EXISTS (SELECT 1 FROM feed_reaction r WHERE r.post_id = fp.id AND r.user_id = :viewer AND r.kind = 'ON_TARGET') viewer_on_target,
                   EXISTS (SELECT 1 FROM feed_reaction r WHERE r.post_id = fp.id AND r.user_id = :viewer AND r.kind = 'ELIMINATED') viewer_eliminated,
                   EXISTS (SELECT 1 FROM feed_save s WHERE s.post_id = fp.id AND s.user_id = :viewer) viewer_saved,
                   EXISTS (SELECT 1 FROM operator_follow ff WHERE ff.follower_user_id = :viewer AND ff.followed_user_id = fp.author_user_id) follows_author,
                   fp.author_user_id = :viewer own_post
            FROM feed_post fp
            JOIN app_user u ON u.id = fp.author_user_id AND u.status = 'ACTIVE'
            JOIN operator_profile p ON p.user_id = u.id
            LEFT JOIN operator_xp x ON x.user_id = u.id
            LEFT JOIN airsoft_field f ON f.id = fp.field_id
            -- Equipe marcada no post; sem marcacao, a equipe atual do autor.
            LEFT JOIN team_member tm ON fp.team_id IS NULL AND tm.user_id = u.id AND tm.left_at IS NULL
            LEFT JOIN team t ON t.id = COALESCE(fp.team_id, tm.team_id) AND t.status = 'ACTIVE'
            LEFT JOIN feed_post_metrics m ON m.post_id = fp.id
            WHERE fp.status = 'PUBLISHED'
              AND NOT EXISTS (SELECT 1 FROM operator_block ob
                  WHERE (ob.blocker_user_id = :viewer AND ob.blocked_user_id = u.id)
                     OR (ob.blocker_user_id = u.id AND ob.blocked_user_id = :viewer))
              AND NOT EXISTS (SELECT 1 FROM feed_post_media pm JOIN media_asset ma ON ma.id = pm.media_asset_id
                  WHERE pm.post_id = fp.id AND ma.status <> 'READY')
              AND (NOT :followingOnly OR EXISTS (SELECT 1 FROM operator_follow ff
                  WHERE ff.follower_user_id = :viewer AND ff.followed_user_id = fp.author_user_id))
              AND (CAST(:cursorAt AS timestamptz) IS NULL OR (fp.published_at, fp.public_id) < (CAST(:cursorAt AS timestamptz), CAST(:cursorId AS uuid)))
              AND (CAST(:hashtag AS varchar) IS NULL OR EXISTS (SELECT 1 FROM feed_post_hashtag h WHERE h.post_id = fp.id AND h.tag = :hashtag))
              AND (CAST(:postId AS uuid) IS NULL OR fp.public_id = :postId)
              AND (NOT :publicOnly OR fp.public_share)
              AND (NOT :filterIds OR fp.id IN (:ids))
            ORDER BY fp.published_at DESC, fp.public_id DESC
            LIMIT :limit
            """, new MapSqlParameterSource().addValue("viewer", viewerId).addValue("followingOnly", followingOnly)
                .addValue("cursorAt", cursorAt == null ? null : Timestamp.from(cursorAt)).addValue("cursorId", cursorId)
                .addValue("limit", limit).addValue("hashtag", hashtag).addValue("postId", postId).addValue("publicOnly", publicOnly)
                .addValue("filterIds", ids != null).addValue("ids", ids == null || ids.isEmpty() ? List.of(-1L) : ids),
            (r, i) -> new PostRow(r.getLong("id"), r.getObject("public_id", UUID.class), r.getString("post_type"),
                r.getString("caption"), r.getTimestamp("published_at").toInstant(),
                new AuthorSummary(r.getString("username"), r.getString("callsign"), r.getString("rank_code")),
                r.getObject("field_id", UUID.class) == null ? null : new PlaceSummary(r.getObject("field_id", UUID.class), r.getString("field_name")),
                r.getObject("team_id", UUID.class) == null ? null : new TeamSummary(r.getObject("team_id", UUID.class), r.getString("team_name"), r.getString("team_acronym")),
                new Counts(r.getLong("on_target"), r.getLong("eliminated"), r.getLong("comments"), r.getLong("shares"), r.getLong("saves"), r.getLong("decorations")),
                new ViewerState(r.getBoolean("viewer_on_target"), r.getBoolean("viewer_eliminated"), r.getBoolean("viewer_saved"),
                    r.getBoolean("follows_author"), r.getBoolean("own_post"))));
    }

    public Map<Long, List<MediaRow>> media(List<Long> postIds) {
        Map<Long, List<MediaRow>> result = new LinkedHashMap<>();
        if (postIds.isEmpty()) return result;
        jdbc.query("""
            SELECT pm.post_id, ma.public_id, ma.kind, ma.width, ma.height, ma.duration_ms, ma.storage_prefix,
                   ma.renditions::text renditions, ma.poster_key, ma.blur_placeholder
            FROM feed_post_media pm JOIN media_asset ma ON ma.id = pm.media_asset_id
            WHERE pm.post_id IN (:ids)
            ORDER BY pm.post_id, pm.position
            """, Map.of("ids", postIds), r -> {
                result.computeIfAbsent(r.getLong("post_id"), key -> new ArrayList<>()).add(new MediaRow(
                    r.getObject("public_id", UUID.class), r.getString("kind"), r.getInt("width"), r.getInt("height"),
                    (Integer) r.getObject("duration_ms"), r.getString("storage_prefix"), r.getString("renditions"),
                    r.getString("poster_key"), r.getString("blur_placeholder")));
            });
        return result;
    }

    public Map<Long, List<String>> hashtags(List<Long> postIds) {
        Map<Long, List<String>> result = new LinkedHashMap<>();
        if (postIds.isEmpty()) return result;
        jdbc.query("SELECT post_id, tag FROM feed_post_hashtag WHERE post_id IN (:ids) ORDER BY post_id, tag",
            Map.of("ids", postIds), r -> {
                result.computeIfAbsent(r.getLong("post_id"), key -> new ArrayList<>()).add(r.getString("tag"));
            });
        return result;
    }

    public record PostRow(long id, UUID publicId, String postType, String caption, Instant publishedAt, AuthorSummary author,
                          PlaceSummary field, TeamSummary team, Counts counts, ViewerState viewer) {}

    public record MediaRow(UUID publicId, String kind, int width, int height, Integer durationMs, String storagePrefix,
                           String renditionsJson, String posterKey, String blurPlaceholder) {}
}
