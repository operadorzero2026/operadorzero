package br.com.operadorzero.community;

import br.com.operadorzero.community.CommunityDtos.AuthorResponse;
import br.com.operadorzero.community.CommunityDtos.CategoryResponse;
import br.com.operadorzero.community.CommunityDtos.CommentResponse;
import br.com.operadorzero.community.CommunityDtos.PostResponse;
import br.com.operadorzero.community.CommunityDtos.PostSummaryResponse;
import br.com.operadorzero.community.CommunityDtos.ReportResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CommunityRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public CommunityRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CategoryResponse> categories() {
        return jdbc.query("""
            SELECT public_id, slug, name
            FROM community_category
            WHERE active = TRUE
            ORDER BY display_order, name
            """, Map.of(), (row, index) -> new CategoryResponse(
                row.getObject("public_id", UUID.class), row.getString("slug"), row.getString("name")));
    }

    public List<PostSummaryResponse> search(long userId, boolean moderator, String query, String categorySlug,
                                             String orderBy, int limit, int offset) {
        String sql = """
            SELECT p.public_id, p.title, p.body, p.created_at, p.comments_locked,
                   u.public_id author_public_id, u.username, op.display_name, op.callsign, op.city, op.state_code,
                   team.name team_name, team.acronym team_acronym,
                   c.public_id category_public_id, c.slug category_slug, c.name category_name,
                   COALESCE(votes.total, 0) vote_count, COALESCE(comments.total, 0) comment_count,
                   EXISTS(SELECT 1 FROM community_vote cv WHERE cv.post_id = p.id AND cv.user_id = :userId) voted,
                   EXISTS(SELECT 1 FROM community_bookmark cb WHERE cb.post_id = p.id AND cb.user_id = :userId) saved,
                   (p.author_user_id = :userId OR :moderator) can_manage,
                   media.public_id cover_image_id
            FROM community_post p
            JOIN community_category c ON c.id = p.category_id
            JOIN app_user u ON u.id = p.author_user_id AND u.status = 'ACTIVE'
            JOIN operator_profile op ON op.user_id = u.id
            LEFT JOIN team_member tm ON tm.user_id = u.id AND tm.left_at IS NULL
            LEFT JOIN team ON team.id = tm.team_id AND team.status = 'ACTIVE'
            LEFT JOIN LATERAL (
                SELECT count(*) total FROM community_vote cv WHERE cv.post_id = p.id
            ) votes ON TRUE
            LEFT JOIN LATERAL (
                SELECT count(*) total FROM community_comment cc
                WHERE cc.post_id = p.id AND cc.status = 'PUBLISHED'
            ) comments ON TRUE
            LEFT JOIN LATERAL (
                SELECT cm.public_id FROM community_post_media cm
                WHERE cm.post_id = p.id ORDER BY cm.id LIMIT 1
            ) media ON TRUE
            WHERE p.status = 'PUBLISHED'
              AND c.active = TRUE
              AND (:query = '' OR lower(p.title) LIKE :likeQuery OR lower(p.body) LIKE :likeQuery
                   OR lower(op.callsign) LIKE :likeQuery OR lower(u.username) LIKE :likeQuery)
              AND (:category = '' OR c.slug = :category)
            ORDER BY %s
            LIMIT :limit OFFSET :offset
            """.formatted(orderBy);
        MapSqlParameterSource params = baseParams(userId, moderator, query, categorySlug)
            .addValue("limit", limit).addValue("offset", offset);
        return jdbc.query(sql, params, (row, index) -> mapSummary(row));
    }

    public long count(String query, String categorySlug) {
        return jdbc.queryForObject("""
            SELECT count(*)
            FROM community_post p
            JOIN community_category c ON c.id = p.category_id
            JOIN app_user u ON u.id = p.author_user_id AND u.status = 'ACTIVE'
            JOIN operator_profile op ON op.user_id = u.id
            WHERE p.status = 'PUBLISHED' AND c.active = TRUE
              AND (:query = '' OR lower(p.title) LIKE :likeQuery OR lower(p.body) LIKE :likeQuery
                   OR lower(op.callsign) LIKE :likeQuery OR lower(u.username) LIKE :likeQuery)
              AND (:category = '' OR c.slug = :category)
            """, baseParams(0, false, query, categorySlug), Long.class);
    }

    public Optional<PostResponse> find(UUID postId, long userId, boolean moderator) {
        try {
            PostResponse base = jdbc.queryForObject("""
                SELECT p.public_id, p.title, p.body, p.created_at, p.updated_at, p.comments_locked,
                       u.public_id author_public_id, u.username, op.display_name, op.callsign, op.city, op.state_code,
                       team.name team_name, team.acronym team_acronym,
                       c.public_id category_public_id, c.slug category_slug, c.name category_name,
                       (SELECT count(*) FROM community_vote cv WHERE cv.post_id = p.id) vote_count,
                       (SELECT count(*) FROM community_comment cc WHERE cc.post_id = p.id AND cc.status = 'PUBLISHED') comment_count,
                       EXISTS(SELECT 1 FROM community_vote cv WHERE cv.post_id = p.id AND cv.user_id = :userId) voted,
                       EXISTS(SELECT 1 FROM community_bookmark cb WHERE cb.post_id = p.id AND cb.user_id = :userId) saved,
                       (p.author_user_id = :userId OR :moderator) can_manage
                FROM community_post p
                JOIN community_category c ON c.id = p.category_id
                JOIN app_user u ON u.id = p.author_user_id AND u.status = 'ACTIVE'
                JOIN operator_profile op ON op.user_id = u.id
                LEFT JOIN team_member tm ON tm.user_id = u.id AND tm.left_at IS NULL
                LEFT JOIN team ON team.id = tm.team_id AND team.status = 'ACTIVE'
                WHERE p.public_id = :postId AND (p.status = 'PUBLISHED' OR :moderator)
                """, Map.of("postId", postId, "userId", userId, "moderator", moderator),
                (row, index) -> mapPost(row, List.of()));
            List<UUID> imageIds = jdbc.query("""
                SELECT m.public_id
                FROM community_post_media m
                JOIN community_post p ON p.id = m.post_id
                WHERE p.public_id = :postId
                ORDER BY m.id
                """, Map.of("postId", postId), (row, index) -> row.getObject("public_id", UUID.class));
            return Optional.of(new PostResponse(base.id(), base.author(), base.category(), base.title(),
                base.description(), base.createdAt(), base.updatedAt(), base.voteCount(), base.commentCount(),
                base.votedByCurrentUser(), base.savedByCurrentUser(), base.canManage(), base.commentsLocked(), imageIds));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public Optional<AuthorResponse> author(String username) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT u.public_id author_public_id, u.username, op.display_name, op.callsign, op.city, op.state_code,
                       team.name team_name, team.acronym team_acronym
                FROM app_user u
                JOIN operator_profile op ON op.user_id = u.id
                LEFT JOIN team_member tm ON tm.user_id = u.id AND tm.left_at IS NULL
                LEFT JOIN team ON team.id = tm.team_id AND team.status = 'ACTIVE'
                WHERE lower(u.username) = lower(:username) AND u.status = 'ACTIVE'
                """, Map.of("username", username), (row, index) -> mapAuthor(row)));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public UUID createPost(long userId, String categorySlug, String title, String body,
                           UUID idempotencyKey, Instant now) {
        try {
            return jdbc.queryForObject("""
                INSERT INTO community_post(author_user_id, category_id, title, body, idempotency_key, created_at, updated_at)
                SELECT :userId, c.id, :title, :body, :idempotencyKey, :now, :now
                FROM community_category c WHERE c.slug = :category AND c.active = TRUE
                ON CONFLICT(author_user_id, idempotency_key)
                DO UPDATE SET updated_at = community_post.updated_at
                RETURNING public_id
                """, new MapSqlParameterSource()
                .addValue("userId", userId).addValue("category", categorySlug)
                .addValue("title", title).addValue("body", body)
                .addValue("idempotencyKey", idempotencyKey).addValue("now", Timestamp.from(now)), UUID.class);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    public UUID addImage(UUID postId, long userId, String contentType, byte[] data, Instant now) {
        try {
            return jdbc.queryForObject("""
                INSERT INTO community_post_media(post_id, content_type, image_data, created_at)
                SELECT p.id, :contentType, :data, :now
                FROM community_post p
                WHERE p.public_id = :postId AND p.author_user_id = :userId AND p.status = 'PUBLISHED'
                  AND (SELECT count(*) FROM community_post_media m WHERE m.post_id = p.id) < 4
                RETURNING public_id
                """, new MapSqlParameterSource().addValue("postId", postId).addValue("userId", userId)
                .addValue("contentType", contentType).addValue("data", data)
                .addValue("now", Timestamp.from(now)), UUID.class);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    public Optional<MediaRow> media(UUID mediaId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT m.content_type, m.image_data
                FROM community_post_media m
                JOIN community_post p ON p.id = m.post_id
                WHERE m.public_id = :mediaId AND p.status = 'PUBLISHED'
                """, Map.of("mediaId", mediaId),
                (row, index) -> new MediaRow(row.getString("content_type"), row.getBytes("image_data"))));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public List<CommentResponse> comments(UUID postId, long userId, boolean moderator) {
        return jdbc.query("""
            SELECT cc.public_id, p.public_id post_public_id, parent.public_id parent_public_id,
                   cc.body, cc.created_at,
                   u.public_id author_public_id, u.username, op.display_name, op.callsign, op.city, op.state_code,
                   team.name team_name, team.acronym team_acronym,
                   (cc.author_user_id = :userId OR :moderator) can_manage
            FROM community_comment cc
            JOIN community_post p ON p.id = cc.post_id
            LEFT JOIN community_comment parent ON parent.id = cc.parent_comment_id
            JOIN app_user u ON u.id = cc.author_user_id AND u.status = 'ACTIVE'
            JOIN operator_profile op ON op.user_id = u.id
            LEFT JOIN team_member tm ON tm.user_id = u.id AND tm.left_at IS NULL
            LEFT JOIN team ON team.id = tm.team_id AND team.status = 'ACTIVE'
            WHERE p.public_id = :postId AND p.status = 'PUBLISHED'
              AND (cc.status = 'PUBLISHED' OR :moderator)
            ORDER BY cc.created_at, cc.id
            """, Map.of("postId", postId, "userId", userId, "moderator", moderator),
            (row, index) -> new CommentResponse(
                row.getObject("public_id", UUID.class),
                row.getObject("post_public_id", UUID.class),
                row.getObject("parent_public_id", UUID.class),
                mapAuthor(row), row.getString("body"), row.getTimestamp("created_at").toInstant(),
                row.getBoolean("can_manage")));
    }

    public UUID createComment(UUID postId, UUID parentId, long userId, String body,
                              UUID idempotencyKey, Instant now) {
        try {
            return jdbc.queryForObject("""
                INSERT INTO community_comment(post_id, author_user_id, parent_comment_id, body,
                                              idempotency_key, created_at, updated_at)
                SELECT p.id, :userId, parent.id, :body, :idempotencyKey, :now, :now
                FROM community_post p
                LEFT JOIN community_comment parent
                  ON parent.public_id = :parentId AND parent.post_id = p.id AND parent.status = 'PUBLISHED'
                WHERE p.public_id = :postId AND p.status = 'PUBLISHED' AND p.comments_locked = FALSE
                  AND (CAST(:parentId AS uuid) IS NULL OR parent.id IS NOT NULL)
                ON CONFLICT(author_user_id, idempotency_key)
                DO UPDATE SET updated_at = community_comment.updated_at
                RETURNING public_id
                """, new MapSqlParameterSource()
                .addValue("postId", postId).addValue("parentId", parentId)
                .addValue("userId", userId).addValue("body", body)
                .addValue("idempotencyKey", idempotencyKey).addValue("now", Timestamp.from(now)), UUID.class);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    public boolean deletePost(UUID postId, long userId, boolean moderator, Instant now) {
        return jdbc.update("""
            UPDATE community_post SET status = 'DELETED', updated_at = :now, version = version + 1
            WHERE public_id = :postId AND status <> 'DELETED'
              AND (author_user_id = :userId OR :moderator)
            """, Map.of("postId", postId, "userId", userId, "moderator", moderator, "now", Timestamp.from(now))) == 1;
    }

    public boolean deleteComment(UUID commentId, long userId, boolean moderator, Instant now) {
        return jdbc.update("""
            UPDATE community_comment SET status = 'DELETED', updated_at = :now, version = version + 1
            WHERE public_id = :commentId AND status <> 'DELETED'
              AND (author_user_id = :userId OR :moderator)
            """, Map.of("commentId", commentId, "userId", userId, "moderator", moderator,
                "now", Timestamp.from(now))) == 1;
    }

    public boolean removeVote(UUID postId, long userId) {
        return jdbc.update("""
            DELETE FROM community_vote cv USING community_post p
            WHERE cv.post_id = p.id AND p.public_id = :postId AND cv.user_id = :userId
            """, Map.of("postId", postId, "userId", userId)) == 1;
    }

    public boolean addVote(UUID postId, long userId, Instant now) {
        try {
            return jdbc.update("""
                INSERT INTO community_vote(post_id, user_id, created_at)
                SELECT p.id, :userId, :now FROM community_post p
                WHERE p.public_id = :postId AND p.status = 'PUBLISHED'
                """, Map.of("postId", postId, "userId", userId, "now", Timestamp.from(now))) == 1;
        } catch (DuplicateKeyException exception) {
            return true;
        }
    }

    public boolean removeBookmark(UUID postId, long userId) {
        return jdbc.update("""
            DELETE FROM community_bookmark cb USING community_post p
            WHERE cb.post_id = p.id AND p.public_id = :postId AND cb.user_id = :userId
            """, Map.of("postId", postId, "userId", userId)) == 1;
    }

    public boolean addBookmark(UUID postId, long userId, Instant now) {
        try {
            return jdbc.update("""
                INSERT INTO community_bookmark(post_id, user_id, created_at)
                SELECT p.id, :userId, :now FROM community_post p
                WHERE p.public_id = :postId AND p.status = 'PUBLISHED'
                """, Map.of("postId", postId, "userId", userId, "now", Timestamp.from(now))) == 1;
        } catch (DuplicateKeyException exception) {
            return true;
        }
    }

    public int voteCount(UUID postId) {
        Integer count = jdbc.queryForObject("""
            SELECT count(*) FROM community_vote cv
            JOIN community_post p ON p.id = cv.post_id WHERE p.public_id = :postId
            """, Map.of("postId", postId), Integer.class);
        return count == null ? 0 : count;
    }

    public boolean reportPost(UUID postId, long userId, String reason, String details, Instant now) {
        try {
            return jdbc.update("""
                INSERT INTO community_report(reporter_user_id, post_id, reason, details, created_at)
                SELECT :userId, p.id, :reason, :details, :now
                FROM community_post p WHERE p.public_id = :postId AND p.status = 'PUBLISHED'
                """, new MapSqlParameterSource().addValue("postId", postId).addValue("userId", userId)
                .addValue("reason", reason).addValue("details", details)
                .addValue("now", Timestamp.from(now))) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public boolean reportComment(UUID commentId, long userId, String reason, String details, Instant now) {
        try {
            return jdbc.update("""
                INSERT INTO community_report(reporter_user_id, comment_id, reason, details, created_at)
                SELECT :userId, cc.id, :reason, :details, :now
                FROM community_comment cc
                JOIN community_post p ON p.id = cc.post_id
                WHERE cc.public_id = :commentId AND cc.status = 'PUBLISHED' AND p.status = 'PUBLISHED'
                """, new MapSqlParameterSource().addValue("commentId", commentId).addValue("userId", userId)
                .addValue("reason", reason).addValue("details", details)
                .addValue("now", Timestamp.from(now))) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public List<ReportResponse> reports() {
        return jdbc.query("""
            SELECT r.public_id, CASE WHEN r.post_id IS NOT NULL THEN 'POST' ELSE 'COMMENT' END target_type,
                   COALESCE(p.public_id, cc.public_id) target_public_id,
                   COALESCE(p.title, left(cc.body, 120)) target_title,
                   reporter.username reporter_username, r.reason, r.details, r.status, r.created_at
            FROM community_report r
            JOIN app_user reporter ON reporter.id = r.reporter_user_id
            LEFT JOIN community_post p ON p.id = r.post_id
            LEFT JOIN community_comment cc ON cc.id = r.comment_id
            ORDER BY CASE WHEN r.status = 'OPEN' THEN 0 ELSE 1 END, r.created_at, r.id
            LIMIT 200
            """, Map.of(), (row, index) -> new ReportResponse(
                row.getObject("public_id", UUID.class), row.getString("target_type"),
                row.getObject("target_public_id", UUID.class), row.getString("target_title"),
                row.getString("reporter_username"), row.getString("reason"), row.getString("details"),
                row.getString("status"), row.getTimestamp("created_at").toInstant()));
    }

    public boolean moderatePost(UUID postId, String status, Instant now) {
        return jdbc.update("""
            UPDATE community_post SET status = :status, updated_at = :now, version = version + 1
            WHERE public_id = :postId
            """, Map.of("postId", postId, "status", status, "now", Timestamp.from(now))) == 1;
    }

    public boolean moderateComment(UUID commentId, String status, Instant now) {
        return jdbc.update("""
            UPDATE community_comment SET status = :status, updated_at = :now, version = version + 1
            WHERE public_id = :commentId
            """, Map.of("commentId", commentId, "status", status, "now", Timestamp.from(now))) == 1;
    }

    public boolean lockComments(UUID postId, boolean locked, Instant now) {
        return jdbc.update("""
            UPDATE community_post SET comments_locked = :locked, updated_at = :now, version = version + 1
            WHERE public_id = :postId
            """, Map.of("postId", postId, "locked", locked, "now", Timestamp.from(now))) == 1;
    }

    public boolean resolveReport(UUID reportId, long reviewerId, String status, String resolution, Instant now) {
        return jdbc.update("""
            UPDATE community_report
            SET status = :status, reviewed_by = :reviewerId, resolution = :resolution, reviewed_at = :now
            WHERE public_id = :reportId AND status = 'OPEN'
            """, Map.of("reportId", reportId, "reviewerId", reviewerId, "status", status,
                "resolution", resolution, "now", Timestamp.from(now))) == 1;
    }

    private MapSqlParameterSource baseParams(long userId, boolean moderator, String query, String categorySlug) {
        String normalized = query == null ? "" : query.trim().toLowerCase();
        return new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("moderator", moderator)
            .addValue("query", normalized)
            .addValue("likeQuery", "%" + normalized + "%")
            .addValue("category", categorySlug == null ? "" : categorySlug.trim().toLowerCase());
    }

    private PostSummaryResponse mapSummary(ResultSet row) throws SQLException {
        return new PostSummaryResponse(
            row.getObject("public_id", UUID.class), mapAuthor(row), mapCategory(row),
            row.getString("title"), row.getString("body"), row.getTimestamp("created_at").toInstant(),
            row.getInt("vote_count"), row.getInt("comment_count"), row.getBoolean("voted"),
            row.getBoolean("saved"), row.getBoolean("can_manage"), row.getBoolean("comments_locked"),
            row.getObject("cover_image_id", UUID.class));
    }

    private PostResponse mapPost(ResultSet row, List<UUID> imageIds) throws SQLException {
        return new PostResponse(
            row.getObject("public_id", UUID.class), mapAuthor(row), mapCategory(row),
            row.getString("title"), row.getString("body"), row.getTimestamp("created_at").toInstant(),
            row.getTimestamp("updated_at").toInstant(), row.getInt("vote_count"), row.getInt("comment_count"),
            row.getBoolean("voted"), row.getBoolean("saved"), row.getBoolean("can_manage"),
            row.getBoolean("comments_locked"), imageIds);
    }

    private AuthorResponse mapAuthor(ResultSet row) throws SQLException {
        return new AuthorResponse(
            row.getObject("author_public_id", UUID.class), row.getString("username"),
            row.getString("display_name"), row.getString("callsign"), row.getString("city"),
            row.getString("state_code"), row.getString("team_name"), row.getString("team_acronym"));
    }

    private CategoryResponse mapCategory(ResultSet row) throws SQLException {
        return new CategoryResponse(
            row.getObject("category_public_id", UUID.class), row.getString("category_slug"),
            row.getString("category_name"));
    }

    public record MediaRow(String contentType, byte[] data) {}
}
