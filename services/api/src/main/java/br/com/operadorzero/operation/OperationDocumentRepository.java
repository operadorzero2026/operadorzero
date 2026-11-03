package br.com.operadorzero.operation;

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
public class OperationDocumentRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public OperationDocumentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** paid: preco maior que zero; participation/paymentConfirmed: a inscricao de quem pede. */
    public record Access(long operationId, long organizerUserId, String status, Instant startsAt, boolean paid,
                         String participation, boolean paymentConfirmed) {}

    public record Doc(long id, UUID publicId, String title, String fileName, long sizeBytes, String storageKey, int version,
                      Instant createdAt, Instant updatedAt, String updatedBy) {}

    public Optional<Access> access(UUID operationId, long viewer) {
        return jdbc.query("""
            SELECT o.id, o.organizer_user_id, o.status,
            """ + OperationRegistrationRepository.STARTS_AT + """
             starts_at, o.registration_price > 0 paid, p.status participation, p.payment_confirmed_at IS NOT NULL payment_confirmed
            FROM airsoft_operation o
            LEFT JOIN operation_participant p ON p.operation_id = o.id AND p.user_id = :viewer
            WHERE o.public_id = :id AND o.deleted_at IS NULL AND (o.status <> 'DRAFT' OR o.organizer_user_id = :viewer)
            """, Map.of("id", operationId, "viewer", viewer), (r, i) -> new Access(r.getLong("id"), r.getLong("organizer_user_id"),
                r.getString("status"), r.getTimestamp("starts_at").toInstant(), r.getBoolean("paid"), r.getString("participation"),
                r.getBoolean("payment_confirmed"))).stream().findFirst();
    }

    public List<Doc> list(long operationId) {
        return jdbc.query("""
            SELECT d.id, d.public_id, d.title, d.file_name, d.size_bytes, d.storage_key, d.version, d.created_at, d.updated_at, up.callsign
            FROM operation_document d LEFT JOIN operator_profile up ON up.user_id = d.updated_by
            WHERE d.operation_id = :id AND d.deleted_at IS NULL ORDER BY d.created_at, d.id
            """, Map.of("id", operationId), (r, i) -> new Doc(r.getLong("id"), r.getObject("public_id", UUID.class), r.getString("title"),
                r.getString("file_name"), r.getLong("size_bytes"), r.getString("storage_key"), r.getInt("version"),
                r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant(), r.getString("callsign")));
    }

    public Optional<Doc> one(long operationId, UUID documentId) {
        return list(operationId).stream().filter(d -> d.publicId().equals(documentId)).findFirst();
    }

    public long count(long operationId) {
        Long value = jdbc.queryForObject("SELECT count(*) FROM operation_document WHERE operation_id = :id AND deleted_at IS NULL",
            Map.of("id", operationId), Long.class);
        return value == null ? 0 : value;
    }

    public UUID insert(long operationId, String title, String fileName, long size, String key, long actor, Instant now) {
        UUID id = jdbc.queryForObject("""
            INSERT INTO operation_document(operation_id, title, file_name, size_bytes, storage_key, created_by, created_at, updated_by, updated_at)
            VALUES (:operation, :title, :fileName, :size, :key, :actor, :now, :actor, :now) RETURNING public_id
            """, params(operationId, title, fileName, size, key, actor, now), UUID.class);
        event(id, "UPLOADED", actor, fileName, now);
        return id;
    }

    /** Troca o arquivo mantendo o mesmo documento (mesmo link para quem tem acesso); a versao sobe. */
    public void replace(long documentId, UUID publicId, String fileName, long size, String key, long actor, Instant now) {
        jdbc.update("""
            UPDATE operation_document SET file_name = :fileName, size_bytes = :size, storage_key = :key, version = version + 1,
                   updated_by = :actor, updated_at = :now WHERE id = :id
            """, new MapSqlParameterSource().addValue("id", documentId).addValue("fileName", fileName).addValue("size", size)
                .addValue("key", key).addValue("actor", actor).addValue("now", Timestamp.from(now)));
        event(publicId, "REPLACED", actor, fileName, now);
    }

    public void delete(long documentId, UUID publicId, long actor, Instant now) {
        jdbc.update("UPDATE operation_document SET deleted_at = :now, updated_by = :actor, updated_at = :now WHERE id = :id",
            Map.of("id", documentId, "actor", actor, "now", Timestamp.from(now)));
        event(publicId, "REMOVED", actor, null, now);
    }

    private void event(UUID documentId, String kind, long actor, String fileName, Instant now) {
        jdbc.update("""
            INSERT INTO operation_document_event(document_id, kind, actor_user_id, file_name, created_at)
            SELECT id, :kind, :actor, :fileName, :now FROM operation_document WHERE public_id = :id
            """, new MapSqlParameterSource().addValue("id", documentId).addValue("kind", kind).addValue("actor", actor)
                .addValue("fileName", fileName).addValue("now", Timestamp.from(now)));
    }

    private static MapSqlParameterSource params(long operationId, String title, String fileName, long size, String key, long actor, Instant now) {
        return new MapSqlParameterSource().addValue("operation", operationId).addValue("title", title).addValue("fileName", fileName)
            .addValue("size", size).addValue("key", key).addValue("actor", actor).addValue("now", Timestamp.from(now));
    }
}
