package br.com.operadorzero.media;

import br.com.operadorzero.media.MediaDtos.Crop;
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
public class MediaRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public MediaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record UploadRow(long uploadId, UUID uploadPublicId, long assetId, UUID assetPublicId, long userId, String purpose,
                            String kind, long totalBytes, long receivedBytes, int chunkBytes, String status, Instant expiresAt) {}

    public record AssetRow(long id, UUID publicId, long ownerUserId, String purpose, String kind, String status,
                           String detectedType, String filterCode, Crop crop, String storagePrefix, String errorCode, int attempts,
                           boolean stripAudio) {}

    public UploadRow createUpload(long userId, String purpose, String kind, String declaredType, long totalBytes,
                                  String filterCode, Crop crop, int chunkBytes, Instant now, Instant expiresAt) {
        var params = new MapSqlParameterSource().addValue("userId", userId).addValue("purpose", purpose).addValue("kind", kind)
            .addValue("declaredType", declaredType).addValue("totalBytes", totalBytes).addValue("filterCode", filterCode)
            .addValue("cropX", crop == null ? null : crop.x()).addValue("cropY", crop == null ? null : crop.y())
            .addValue("cropZoom", crop == null ? null : crop.zoom()).addValue("chunkBytes", chunkBytes)
            .addValue("now", Timestamp.from(now)).addValue("expiresAt", Timestamp.from(expiresAt));
        return jdbc.queryForObject("""
            WITH asset AS (
                INSERT INTO media_asset(owner_user_id, purpose, kind, original_bytes, filter_code, crop_x, crop_y, crop_zoom, created_at, updated_at)
                VALUES (:userId, :purpose, :kind, :totalBytes, NULLIF(:filterCode, ''), :cropX, :cropY, :cropZoom, :now, :now)
                RETURNING id, public_id
            ), upload AS (
                INSERT INTO media_upload(media_asset_id, user_id, declared_type, total_bytes, chunk_bytes, created_at, updated_at, expires_at)
                SELECT id, :userId, :declaredType, :totalBytes, :chunkBytes, :now, :now, :expiresAt FROM asset
                RETURNING id, public_id
            )
            SELECT upload.id upload_id, upload.public_id upload_public_id, asset.id asset_id, asset.public_id asset_public_id
            FROM upload, asset
            """, params, (r, i) -> new UploadRow(r.getLong("upload_id"), r.getObject("upload_public_id", UUID.class),
                r.getLong("asset_id"), r.getObject("asset_public_id", UUID.class), userId, purpose, kind, totalBytes, 0,
                chunkBytes, "OPEN", expiresAt));
    }

    /** Bloqueia o envio para que dois pedacos simultaneos nao gravem no mesmo arquivo. */
    public Optional<UploadRow> lockUpload(UUID uploadId, long userId) {
        return jdbc.query("""
            SELECT u.id, u.public_id, a.id asset_id, a.public_id asset_public_id, u.user_id, a.purpose, a.kind,
                   u.total_bytes, u.received_bytes, u.chunk_bytes, u.status, u.expires_at
            FROM media_upload u JOIN media_asset a ON a.id = u.media_asset_id
            WHERE u.public_id = :uploadId AND u.user_id = :userId
            FOR UPDATE OF u
            """, Map.of("uploadId", uploadId, "userId", userId), (r, i) -> uploadRow(r)).stream().findFirst();
    }

    public Optional<UploadRow> findUpload(UUID uploadId, long userId) {
        return jdbc.query("""
            SELECT u.id, u.public_id, a.id asset_id, a.public_id asset_public_id, u.user_id, a.purpose, a.kind,
                   u.total_bytes, u.received_bytes, u.chunk_bytes, u.status, u.expires_at
            FROM media_upload u JOIN media_asset a ON a.id = u.media_asset_id
            WHERE u.public_id = :uploadId AND u.user_id = :userId
            """, Map.of("uploadId", uploadId, "userId", userId), (r, i) -> uploadRow(r)).stream().findFirst();
    }

    public void updateReceived(long uploadId, long receivedBytes, Instant now) {
        jdbc.update("UPDATE media_upload SET received_bytes = :received, updated_at = :now WHERE id = :id",
            Map.of("received", receivedBytes, "now", Timestamp.from(now), "id", uploadId));
    }

    public void completeUpload(long uploadId, long assetId, String detectedType, Instant now) {
        var params = Map.<String, Object>of("uploadId", uploadId, "assetId", assetId, "type", detectedType, "now", Timestamp.from(now));
        jdbc.update("UPDATE media_upload SET status = 'COMPLETE', updated_at = :now WHERE id = :uploadId", params);
        jdbc.update("UPDATE media_asset SET status = 'QUEUED', detected_type = :type, updated_at = :now WHERE id = :assetId", params);
    }

    /** Enquadramento so substitui o do inicio do envio quando vem preenchido. */
    public void applyCompleteOptions(long assetId, boolean stripAudio, Crop crop) {
        jdbc.update("""
            UPDATE media_asset SET strip_audio = :strip,
                   crop_x = CASE WHEN :hasCrop THEN :cropX ELSE crop_x END,
                   crop_y = CASE WHEN :hasCrop THEN :cropY ELSE crop_y END,
                   crop_zoom = CASE WHEN :hasCrop THEN :cropZoom ELSE crop_zoom END
            WHERE id = :id
            """, new MapSqlParameterSource().addValue("id", assetId).addValue("strip", stripAudio).addValue("hasCrop", crop != null)
                .addValue("cropX", crop == null ? null : crop.x()).addValue("cropY", crop == null ? null : crop.y())
                .addValue("cropZoom", crop == null ? null : crop.zoom()));
    }

    public void failUpload(long uploadId, long assetId, String errorCode, Instant now) {
        var params = Map.<String, Object>of("uploadId", uploadId, "assetId", assetId, "error", errorCode, "now", Timestamp.from(now));
        jdbc.update("UPDATE media_upload SET status = 'ABORTED', updated_at = :now WHERE id = :uploadId", params);
        jdbc.update("UPDATE media_asset SET status = 'FAILED', error_code = :error, updated_at = :now WHERE id = :assetId", params);
    }

    /** Pega o proximo da fila; SKIP LOCKED permite mais de um processador sem duplicar trabalho. */
    public Optional<AssetRow> claimNext(Instant now) {
        return jdbc.query("""
            UPDATE media_asset SET status = 'PROCESSING', attempts = attempts + 1, updated_at = :now
            WHERE id = (SELECT id FROM media_asset WHERE status = 'QUEUED' ORDER BY created_at, id LIMIT 1 FOR UPDATE SKIP LOCKED)
            RETURNING id, public_id, owner_user_id, purpose, kind, status, detected_type, filter_code, crop_x, crop_y, crop_zoom,
                      storage_prefix, error_code, attempts, strip_audio
            """, Map.of("now", Timestamp.from(now)), (r, i) -> new AssetRow(r.getLong("id"), r.getObject("public_id", UUID.class),
                r.getLong("owner_user_id"), r.getString("purpose"), r.getString("kind"), r.getString("status"),
                r.getString("detected_type"), r.getString("filter_code"),
                r.getBigDecimal("crop_zoom") == null ? null : new Crop(r.getBigDecimal("crop_x"), r.getBigDecimal("crop_y"), r.getBigDecimal("crop_zoom")),
                r.getString("storage_prefix"), r.getString("error_code"), r.getInt("attempts"), r.getBoolean("strip_audio"))).stream().findFirst();
    }

    /** Processamentos interrompidos (reinicio da API) voltam para a fila ate 3 tentativas. */
    public int requeueStale(Instant olderThan, Instant now) {
        var params = Map.<String, Object>of("olderThan", Timestamp.from(olderThan), "now", Timestamp.from(now));
        jdbc.update("""
            UPDATE media_asset SET status = 'FAILED', error_code = 'PROCESSING_ABANDONED', updated_at = :now
            WHERE status = 'PROCESSING' AND updated_at < :olderThan AND attempts >= 3
            """, params);
        return jdbc.update("UPDATE media_asset SET status = 'QUEUED', updated_at = :now WHERE status = 'PROCESSING' AND updated_at < :olderThan", params);
    }

    public void requeue(long id, Instant now) {
        jdbc.update("UPDATE media_asset SET status = 'QUEUED', updated_at = :now WHERE id = :id AND status = 'PROCESSING'",
            Map.of("id", id, "now", Timestamp.from(now)));
    }

    public void markReady(long id, int width, int height, Integer durationMs, Instant capturedAt, String prefix, String renditionsJson,
                          String posterKey, String blurPlaceholder, Instant now) {
        jdbc.update("""
            UPDATE media_asset SET status = 'READY', width = :width, height = :height, duration_ms = :duration, captured_at = :capturedAt,
                   storage_prefix = :prefix, renditions = CAST(:renditions AS jsonb), poster_key = :poster, blur_placeholder = :blur,
                   error_code = NULL, ready_at = :now, updated_at = :now
            WHERE id = :id
            """, new MapSqlParameterSource().addValue("id", id).addValue("width", width).addValue("height", height)
                .addValue("duration", durationMs).addValue("capturedAt", capturedAt == null ? null : Timestamp.from(capturedAt))
                .addValue("prefix", prefix).addValue("renditions", renditionsJson).addValue("poster", posterKey)
                .addValue("blur", blurPlaceholder).addValue("now", Timestamp.from(now)));
    }

    public void markFailed(long id, String errorCode, Instant now) {
        jdbc.update("UPDATE media_asset SET status = 'FAILED', error_code = :error, updated_at = :now WHERE id = :id",
            Map.of("id", id, "error", errorCode, "now", Timestamp.from(now)));
    }

    public Optional<MediaDtos.MediaStatusResponse> status(UUID mediaId, long userId) {
        return jdbc.query("SELECT public_id, kind, status, error_code FROM media_asset WHERE public_id = :id AND owner_user_id = :userId",
            Map.of("id", mediaId, "userId", userId), (r, i) -> new MediaDtos.MediaStatusResponse(r.getObject("public_id", UUID.class),
                r.getString("kind"), r.getString("status"), r.getString("error_code"))).stream().findFirst();
    }

    public List<UploadRow> expiredUploads(Instant now) {
        return jdbc.query("""
            SELECT u.id, u.public_id, a.id asset_id, a.public_id asset_public_id, u.user_id, a.purpose, a.kind,
                   u.total_bytes, u.received_bytes, u.chunk_bytes, u.status, u.expires_at
            FROM media_upload u JOIN media_asset a ON a.id = u.media_asset_id
            WHERE u.status = 'OPEN' AND u.expires_at < :now LIMIT 200
            """, Map.of("now", Timestamp.from(now)), (r, i) -> uploadRow(r));
    }

    public void expireUpload(long uploadId, long assetId, Instant now) {
        var params = Map.<String, Object>of("uploadId", uploadId, "assetId", assetId, "now", Timestamp.from(now));
        jdbc.update("UPDATE media_upload SET status = 'EXPIRED', updated_at = :now WHERE id = :uploadId", params);
        jdbc.update("UPDATE media_asset SET status = 'DELETED', deleted_at = :now, updated_at = :now WHERE id = :assetId", params);
    }

    /** Midias prontas que ninguem usou em 24 h (envio abandonado antes de publicar). */
    public List<AssetRow> orphanedReady(Instant olderThan) {
        return jdbc.query("""
            SELECT a.id, a.public_id, a.owner_user_id, a.purpose, a.kind, a.status, a.detected_type, a.filter_code,
                   a.storage_prefix, a.error_code, a.attempts
            FROM media_asset a
            WHERE a.status IN ('READY','FAILED') AND a.updated_at < :olderThan AND a.purpose IN ('POST','RECON')
              AND NOT EXISTS (SELECT 1 FROM feed_post_media pm WHERE pm.media_asset_id = a.id)
              AND NOT EXISTS (SELECT 1 FROM recon r WHERE r.media_asset_id = a.id)
            LIMIT 200
            """, Map.of("olderThan", Timestamp.from(olderThan)), (r, i) -> new AssetRow(r.getLong("id"), r.getObject("public_id", UUID.class),
                r.getLong("owner_user_id"), r.getString("purpose"), r.getString("kind"), r.getString("status"),
                r.getString("detected_type"), r.getString("filter_code"), null, r.getString("storage_prefix"),
                r.getString("error_code"), r.getInt("attempts"), false));
    }

    public void markDeleted(long id, Instant now) {
        jdbc.update("UPDATE media_asset SET status = 'DELETED', deleted_at = :now, updated_at = :now WHERE id = :id",
            Map.of("id", id, "now", Timestamp.from(now)));
    }

    private static UploadRow uploadRow(java.sql.ResultSet r) throws java.sql.SQLException {
        return new UploadRow(r.getLong("id"), r.getObject("public_id", UUID.class), r.getLong("asset_id"),
            r.getObject("asset_public_id", UUID.class), r.getLong("user_id"), r.getString("purpose"), r.getString("kind"),
            r.getLong("total_bytes"), r.getLong("received_bytes"), r.getInt("chunk_bytes"), r.getString("status"),
            r.getTimestamp("expires_at").toInstant());
    }
}
