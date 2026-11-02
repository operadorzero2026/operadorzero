package br.com.operadorzero.recon;

import br.com.operadorzero.feed.FeedRepository.MediaRow;
import br.com.operadorzero.recon.ReconDtos.BarEntry;
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
public class ReconRepository {
    /** Quem pode ver os Recons de um autor: ele mesmo ou quem o segue, sem bloqueio nos dois sentidos. */
    private static final String VISIBLE_TO_VIEWER = """
        (r.author_user_id = :viewer
          OR EXISTS (SELECT 1 FROM operator_follow f WHERE f.follower_user_id = :viewer AND f.followed_user_id = r.author_user_id))
        AND NOT EXISTS (SELECT 1 FROM operator_block ob
          WHERE (ob.blocker_user_id = :viewer AND ob.blocked_user_id = r.author_user_id)
             OR (ob.blocker_user_id = r.author_user_id AND ob.blocked_user_id = :viewer))
        """;
    private static final String MEDIA_COLUMNS = """
        a.public_id media_public_id, a.kind, a.width, a.height, a.duration_ms, a.storage_prefix,
        a.renditions::text renditions, a.poster_key, a.blur_placeholder
        """;

    private final NamedParameterJdbcTemplate jdbc;

    public ReconRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record MediaForRecon(long id, String purpose, String status, boolean used) {}
    public record ReconRow(long id, UUID publicId, String overlayText, Instant publishedAt, Instant expiresAt, boolean seen, MediaRow media) {}
    public record Author(long id, String username, String callsign, String rankCode) {}
    public record ReconState(long id, UUID publicId, long authorUserId, String status, Instant expiresAt) {}
    public record ExpiredMedia(long assetId, String storagePrefix) {}

    public Optional<MediaForRecon> lockMedia(UUID mediaId, long ownerUserId) {
        return jdbc.query("""
            SELECT a.id, a.purpose, a.status,
                   EXISTS (SELECT 1 FROM recon r WHERE r.media_asset_id = a.id) used
            FROM media_asset a WHERE a.public_id = :mediaId AND a.owner_user_id = :userId
            FOR UPDATE
            """, Map.of("mediaId", mediaId, "userId", ownerUserId),
            (r, i) -> new MediaForRecon(r.getLong("id"), r.getString("purpose"), r.getString("status"), r.getBoolean("used")))
            .stream().findFirst();
    }

    public ReconState insert(long authorUserId, long mediaId, String overlayText, boolean active, Instant now, Instant expiresAt) {
        return jdbc.queryForObject("""
            INSERT INTO recon(author_user_id, media_asset_id, overlay_text, status, created_at, published_at, expires_at)
            VALUES (:author, :media, :overlay, :status, :now, :publishedAt, :expiresAt)
            RETURNING id, public_id, author_user_id, status, expires_at
            """, new MapSqlParameterSource().addValue("author", authorUserId).addValue("media", mediaId).addValue("overlay", overlayText)
                .addValue("status", active ? "ACTIVE" : "PROCESSING").addValue("now", Timestamp.from(now))
                .addValue("publishedAt", active ? Timestamp.from(now) : null).addValue("expiresAt", Timestamp.from(expiresAt)),
            (r, i) -> state(r));
    }

    /** Recons ainda dentro das 24 h sao ativados quando a midia fica pronta; os que venceram na fila ja nascem expirados. */
    public List<ReconState> activateFor(long mediaId, Instant now) {
        return jdbc.query("""
            UPDATE recon SET status = CASE WHEN expires_at > :now THEN 'ACTIVE' ELSE 'EXPIRED' END, published_at = :now
            WHERE media_asset_id = :media AND status = 'PROCESSING'
            RETURNING id, public_id, author_user_id, status, expires_at
            """, Map.of("media", mediaId, "now", Timestamp.from(now)), (r, i) -> state(r));
    }

    public List<ReconState> failFor(long mediaId) {
        return jdbc.query("""
            UPDATE recon SET status = 'FAILED' WHERE media_asset_id = :media AND status = 'PROCESSING'
            RETURNING id, public_id, author_user_id, status, expires_at
            """, Map.of("media", mediaId), (r, i) -> state(r));
    }

    public List<BarEntry> bar(long viewer, Instant now) {
        return jdbc.query("""
            SELECT u.username, p.callsign, COALESCE(x.rank_code, 'RECRUTA') rank_code, count(*) total,
                   bool_or(NOT EXISTS (SELECT 1 FROM recon_view v WHERE v.recon_id = r.id AND v.viewer_user_id = :viewer)) has_unseen,
                   max(r.published_at) latest_at, r.author_user_id = :viewer own
            FROM recon r
            JOIN app_user u ON u.id = r.author_user_id AND u.status = 'ACTIVE'
            JOIN operator_profile p ON p.user_id = u.id
            LEFT JOIN operator_xp x ON x.user_id = u.id
            WHERE r.status = 'ACTIVE' AND r.expires_at > :now AND """ + VISIBLE_TO_VIEWER + """
            GROUP BY u.username, p.callsign, x.rank_code, r.author_user_id
            ORDER BY own DESC, has_unseen DESC, latest_at DESC
            LIMIT 60
            """, Map.of("viewer", viewer, "now", Timestamp.from(now)), (r, i) -> new BarEntry(r.getString("username"),
                r.getString("callsign"), r.getString("rank_code"), r.getInt("total"), r.getBoolean("has_unseen"),
                r.getTimestamp("latest_at").toInstant(), r.getBoolean("own")));
    }

    public boolean ownProcessing(long viewer, Instant now) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM recon WHERE author_user_id = :viewer AND status = 'PROCESSING' AND expires_at > :now)
            """, Map.of("viewer", viewer, "now", Timestamp.from(now)), Boolean.class));
    }

    public Optional<Author> author(String username) {
        return jdbc.query("""
            SELECT u.id, u.username, p.callsign, COALESCE(x.rank_code, 'RECRUTA') rank_code
            FROM app_user u JOIN operator_profile p ON p.user_id = u.id LEFT JOIN operator_xp x ON x.user_id = u.id
            WHERE lower(u.username) = lower(:username) AND u.status = 'ACTIVE'
            """, Map.of("username", username), (r, i) -> new Author(r.getLong("id"), r.getString("username"),
                r.getString("callsign"), r.getString("rank_code"))).stream().findFirst();
    }

    public List<ReconRow> activeOf(long authorId, long viewer, Instant now) {
        return jdbc.query("SELECT r.id, r.public_id, r.overlay_text, r.published_at, r.expires_at, "
            + "EXISTS (SELECT 1 FROM recon_view v WHERE v.recon_id = r.id AND v.viewer_user_id = :viewer) seen, " + MEDIA_COLUMNS + """
            FROM recon r JOIN media_asset a ON a.id = r.media_asset_id AND a.status = 'READY'
            WHERE r.author_user_id = :author AND r.status = 'ACTIVE' AND r.expires_at > :now AND """ + VISIBLE_TO_VIEWER + """
            ORDER BY r.published_at, r.id
            """, Map.of("author", authorId, "viewer", viewer, "now", Timestamp.from(now)), (r, i) -> reconRow(r));
    }

    /** Registra a visualizacao so se o Recon ainda esta no ar e o espectador pode ve-lo. */
    public boolean markViewed(UUID reconId, long viewer, Instant now) {
        return jdbc.update("""
            INSERT INTO recon_view(recon_id, viewer_user_id, viewed_at)
            SELECT r.id, :viewer, :now FROM recon r
            WHERE r.public_id = :recon AND r.status = 'ACTIVE' AND r.expires_at > :now AND """ + VISIBLE_TO_VIEWER + """
            ON CONFLICT (recon_id, viewer_user_id) DO UPDATE SET viewed_at = recon_view.viewed_at
            """, new MapSqlParameterSource().addValue("recon", reconId).addValue("viewer", viewer).addValue("now", Timestamp.from(now))) > 0;
    }

    public Optional<ReconState> lockOwn(UUID reconId, long authorUserId) {
        return jdbc.query("SELECT id, public_id, author_user_id, status, expires_at FROM recon WHERE public_id = :id AND author_user_id = :author FOR UPDATE",
            Map.of("id", reconId, "author", authorUserId), (r, i) -> state(r)).stream().findFirst();
    }

    public void markDeleted(long reconId) {
        jdbc.update("DELETE FROM dossier_recon WHERE recon_id = :id", Map.of("id", reconId));
        jdbc.update("UPDATE recon SET status = 'DELETED' WHERE id = :id", Map.of("id", reconId));
    }

    public int expire(Instant now) {
        return jdbc.update("UPDATE recon SET status = 'EXPIRED' WHERE status IN ('ACTIVE','PROCESSING') AND expires_at <= :now",
            Map.of("now", Timestamp.from(now)));
    }

    /** Midias de Recons encerrados (expirados, apagados ou com falha) que nao estao guardados em nenhum Dossie. */
    public List<ExpiredMedia> mediaToDiscard(int limit) {
        return jdbc.query("""
            SELECT a.id, a.storage_prefix FROM recon r JOIN media_asset a ON a.id = r.media_asset_id
            WHERE r.status IN ('EXPIRED','DELETED','FAILED','SUSPENDED') AND a.status <> 'DELETED'
              AND NOT EXISTS (SELECT 1 FROM dossier_recon dr WHERE dr.recon_id = r.id)
            LIMIT :limit
            """, Map.of("limit", limit), (r, i) -> new ExpiredMedia(r.getLong("id"), r.getString("storage_prefix")));
    }

    public void markMediaDeleted(long assetId, Instant now) {
        jdbc.update("UPDATE media_asset SET status = 'DELETED', deleted_at = :now, updated_at = :now WHERE id = :id",
            Map.of("id", assetId, "now", Timestamp.from(now)));
    }

    // ---- Dossies ----

    public int dossierCount(long userId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM dossier WHERE user_id = :user", Map.of("user", userId), Integer.class);
        return count == null ? 0 : count;
    }

    public UUID createDossier(long userId, String title, Instant now) {
        return jdbc.queryForObject("""
            INSERT INTO dossier(user_id, title, position, created_at, updated_at)
            VALUES (:user, :title, (SELECT COALESCE(max(position) + 1, 0) FROM dossier WHERE user_id = :user), :now, :now)
            RETURNING public_id
            """, Map.of("user", userId, "title", title, "now", Timestamp.from(now)), UUID.class);
    }

    public Optional<Long> ownDossier(UUID dossierId, long userId) {
        return jdbc.query("SELECT id FROM dossier WHERE public_id = :id AND user_id = :user FOR UPDATE",
            Map.of("id", dossierId, "user", userId), (r, i) -> r.getLong(1)).stream().findFirst();
    }

    /** Recon proprio, ainda com midia (ativo ou expirado ha pouco), pode ser guardado. */
    public Optional<Long> archivableRecon(UUID reconId, long userId) {
        return jdbc.query("""
            SELECT r.id FROM recon r JOIN media_asset a ON a.id = r.media_asset_id AND a.status = 'READY'
            WHERE r.public_id = :id AND r.author_user_id = :user AND r.status IN ('ACTIVE','EXPIRED')
            """, Map.of("id", reconId, "user", userId), (r, i) -> r.getLong(1)).stream().findFirst();
    }

    public int dossierSize(long dossierId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM dossier_recon WHERE dossier_id = :id", Map.of("id", dossierId), Integer.class);
        return count == null ? 0 : count;
    }

    public void addToDossier(long dossierId, long reconId, Instant now) {
        jdbc.update("""
            INSERT INTO dossier_recon(dossier_id, recon_id, position, added_at)
            VALUES (:dossier, :recon, (SELECT COALESCE(max(position) + 1, 0) FROM dossier_recon WHERE dossier_id = :dossier), :now)
            ON CONFLICT DO NOTHING
            """, Map.of("dossier", dossierId, "recon", reconId, "now", Timestamp.from(now)));
        jdbc.update("""
            UPDATE dossier SET updated_at = :now,
                   cover_media_asset_id = COALESCE(cover_media_asset_id, (SELECT media_asset_id FROM recon WHERE id = :recon))
            WHERE id = :dossier
            """, Map.of("dossier", dossierId, "recon", reconId, "now", Timestamp.from(now)));
    }

    public void removeFromDossier(long dossierId, UUID reconId) {
        jdbc.update("DELETE FROM dossier_recon WHERE dossier_id = :dossier AND recon_id = (SELECT id FROM recon WHERE public_id = :recon)",
            Map.of("dossier", dossierId, "recon", reconId));
        jdbc.update("""
            UPDATE dossier SET cover_media_asset_id = (
                SELECT r.media_asset_id FROM dossier_recon dr JOIN recon r ON r.id = dr.recon_id
                WHERE dr.dossier_id = :dossier ORDER BY dr.position LIMIT 1)
            WHERE id = :dossier
            """, Map.of("dossier", dossierId));
    }

    public void deleteDossier(long dossierId) {
        jdbc.update("DELETE FROM dossier WHERE id = :id", Map.of("id", dossierId));
    }

    public record DossierRow(UUID id, String title, String coverPrefix, String coverKey, String coverRenditions, int count) {}

    public List<DossierRow> dossiersOf(long ownerId, long viewer) {
        return jdbc.query("""
            SELECT d.public_id, d.title, a.storage_prefix, a.poster_key, a.renditions::text renditions,
                   (SELECT count(*) FROM dossier_recon dr WHERE dr.dossier_id = d.id) total
            FROM dossier d LEFT JOIN media_asset a ON a.id = d.cover_media_asset_id AND a.status = 'READY'
            WHERE d.user_id = :owner
              AND NOT EXISTS (SELECT 1 FROM operator_block ob
                  WHERE (ob.blocker_user_id = :viewer AND ob.blocked_user_id = :owner)
                     OR (ob.blocker_user_id = :owner AND ob.blocked_user_id = :viewer))
            ORDER BY d.position, d.id
            """, Map.of("owner", ownerId, "viewer", viewer), (r, i) -> new DossierRow(r.getObject("public_id", UUID.class),
                r.getString("title"), r.getString("storage_prefix"), r.getString("poster_key"), r.getString("renditions"), r.getInt("total")));
    }

    public record DossierHeader(long id, UUID publicId, String title, long ownerId, String ownerUsername) {}

    public Optional<DossierHeader> dossier(UUID dossierId, long viewer) {
        return jdbc.query("""
            SELECT d.id, d.public_id, d.title, d.user_id, u.username FROM dossier d JOIN app_user u ON u.id = d.user_id AND u.status = 'ACTIVE'
            WHERE d.public_id = :id
              AND NOT EXISTS (SELECT 1 FROM operator_block ob
                  WHERE (ob.blocker_user_id = :viewer AND ob.blocked_user_id = d.user_id)
                     OR (ob.blocker_user_id = d.user_id AND ob.blocked_user_id = :viewer))
            """, Map.of("id", dossierId, "viewer", viewer), (r, i) -> new DossierHeader(r.getLong("id"),
                r.getObject("public_id", UUID.class), r.getString("title"), r.getLong("user_id"), r.getString("username")))
            .stream().findFirst();
    }

    public List<ReconRow> dossierRecons(long dossierId) {
        return jdbc.query("SELECT r.id, r.public_id, r.overlay_text, r.published_at, r.expires_at, TRUE seen, " + MEDIA_COLUMNS + """
            FROM dossier_recon dr JOIN recon r ON r.id = dr.recon_id AND r.status IN ('ACTIVE','EXPIRED')
            JOIN media_asset a ON a.id = r.media_asset_id AND a.status = 'READY'
            WHERE dr.dossier_id = :dossier
            ORDER BY dr.position, dr.added_at
            """, Map.of("dossier", dossierId), (r, i) -> reconRow(r));
    }

    private static ReconRow reconRow(ResultSet r) throws SQLException {
        Timestamp published = r.getTimestamp("published_at");
        return new ReconRow(r.getLong("id"), r.getObject("public_id", UUID.class), r.getString("overlay_text"),
            published == null ? null : published.toInstant(), r.getTimestamp("expires_at").toInstant(), r.getBoolean("seen"),
            new MediaRow(r.getObject("media_public_id", UUID.class), r.getString("kind"), r.getInt("width"), r.getInt("height"),
                (Integer) r.getObject("duration_ms"), r.getString("storage_prefix"), r.getString("renditions"),
                r.getString("poster_key"), r.getString("blur_placeholder")));
    }

    private static ReconState state(ResultSet r) throws SQLException {
        return new ReconState(r.getLong("id"), r.getObject("public_id", UUID.class), r.getLong("author_user_id"),
            r.getString("status"), r.getTimestamp("expires_at").toInstant());
    }
}
