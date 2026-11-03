package br.com.operadorzero.operation;

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

/** Chat geral da operacao (canal GENERAL): mensagens, comunicados fixados, silenciamentos e registro de moderacao. */
@Repository
public class OperationChatRepository {
    /** Quem conversa: organizador, administradores da operacao e inscritos aprovados. */
    static final String MEMBER_STATUSES = "('APPROVED','CONFIRMED','CHECKED_IN')";
    private final NamedParameterJdbcTemplate jdbc;

    public OperationChatRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Context(long operationId, long channelId, long organizerUserId, String status, Instant endsAt, boolean admin,
                          String participation) {}

    public record Message(UUID id, UUID authorId, String callsign, String displayName, boolean hasPhoto, long photoVersion, String body,
                          String status, Instant createdAt, Instant pinnedAt, long authorUserId) {}

    public record Muted(UUID operatorId, String callsign, Instant until, Instant createdAt) {}

    public Optional<Context> context(UUID operationId, long viewer) {
        return jdbc.query("""
            SELECT o.id, c.id channel_id, o.organizer_user_id, o.status,
            """ + OperationPageRepository.ENDS_AT + """
             ends_at, EXISTS (SELECT 1 FROM operation_role_assignment r WHERE r.operation_id = o.id AND r.user_id = :viewer
                    AND r.role = 'OPERATION_ADMIN') admin, p.status participation
            FROM airsoft_operation o
            JOIN operation_chat_channel c ON c.operation_id = o.id AND c.channel_type = 'GENERAL'
            LEFT JOIN operation_participant p ON p.operation_id = o.id AND p.user_id = :viewer
            WHERE o.public_id = :id AND o.deleted_at IS NULL AND (o.status <> 'DRAFT' OR o.organizer_user_id = :viewer)
            """, Map.of("id", operationId, "viewer", viewer), (r, i) -> new Context(r.getLong("id"), r.getLong("channel_id"),
                r.getLong("organizer_user_id"), r.getString("status"), r.getTimestamp("ends_at").toInstant(), r.getBoolean("admin"),
                r.getString("participation"))).stream().findFirst();
    }

    private static final String MESSAGE_COLUMNS = """
        SELECT m.public_id, p.public_id author_id, p.callsign, p.display_name, ph.operator_profile_id IS NOT NULL has_photo,
               COALESCE(EXTRACT(EPOCH FROM ph.updated_at)::bigint, 0) photo_version,
               CASE WHEN m.status IN ('DELETED','HIDDEN') THEN '' ELSE m.body END body, m.status, m.created_at, m.pinned_at, m.author_user_id
        FROM operation_chat_message m
        JOIN operator_profile p ON p.user_id = m.author_user_id
        LEFT JOIN operator_profile_photo ph ON ph.operator_profile_id = p.id
        """;

    /** Pagina de mensagens (mais recentes primeiro): antes de um momento (historico) ou depois de um (novidades). */
    public List<Message> messages(long channelId, Instant before, Instant after, int limit) {
        var params = new MapSqlParameterSource().addValue("channel", channelId).addValue("limit", limit)
            .addValue("before", before == null ? null : Timestamp.from(before)).addValue("after", after == null ? null : Timestamp.from(after));
        return jdbc.query(MESSAGE_COLUMNS + """
            WHERE m.channel_id = :channel
              AND (CAST(:before AS timestamptz) IS NULL OR m.created_at < :before)
              AND (CAST(:after AS timestamptz) IS NULL OR m.created_at > :after OR m.updated_at > :after)
            ORDER BY m.created_at DESC, m.id DESC LIMIT :limit
            """, params, (r, i) -> message(r));
    }

    public List<Message> pinned(long channelId) {
        return jdbc.query(MESSAGE_COLUMNS + """
            WHERE m.channel_id = :channel AND m.pinned_at IS NOT NULL AND m.status IN ('VISIBLE','EDITED')
            ORDER BY m.pinned_at DESC LIMIT 5
            """, Map.of("channel", channelId), (r, i) -> message(r));
    }

    /** Idempotente: repetir o mesmo envio (rede instavel) nao duplica a mensagem. */
    public UUID insert(long channelId, long author, String body, UUID idempotencyKey, Instant now) {
        return jdbc.queryForObject("""
            INSERT INTO operation_chat_message(channel_id, author_user_id, body, official, idempotency_key, created_at, updated_at)
            VALUES (:channel, :author, :body, FALSE, :key, :now, :now)
            ON CONFLICT (author_user_id, idempotency_key) DO UPDATE SET updated_at = operation_chat_message.updated_at
            RETURNING public_id
            """, new MapSqlParameterSource().addValue("channel", channelId).addValue("author", author).addValue("body", body)
                .addValue("key", idempotencyKey).addValue("now", Timestamp.from(now)), UUID.class);
    }

    public int delete(long channelId, UUID messageId, Instant now) {
        return jdbc.update("""
            UPDATE operation_chat_message SET status = 'DELETED', deleted_at = :now, updated_at = :now, pinned_at = NULL
            WHERE public_id = :message AND channel_id = :channel AND status <> 'DELETED'
            """, Map.of("message", messageId, "channel", channelId, "now", Timestamp.from(now)));
    }

    public int pin(long channelId, UUID messageId, Long actor, Instant now) {
        return jdbc.update("""
            UPDATE operation_chat_message SET pinned_at = :pinned, pinned_by = :actor, updated_at = :now
            WHERE public_id = :message AND channel_id = :channel AND status IN ('VISIBLE','EDITED')
            """, new MapSqlParameterSource().addValue("message", messageId).addValue("channel", channelId).addValue("actor", actor)
                .addValue("pinned", actor == null ? null : Timestamp.from(now)).addValue("now", Timestamp.from(now)));
    }

    /** Silenciamento em vigor: Optional vazio se nao ha; o Instant interno e nulo quando e por tempo indeterminado. */
    public Optional<Optional<Instant>> activeMute(long operationId, long userId, Instant now) {
        return jdbc.query("""
            SELECT until FROM operation_chat_mute WHERE operation_id = :operation AND user_id = :user AND lifted_at IS NULL
              AND (until IS NULL OR until > :now) ORDER BY until DESC NULLS FIRST LIMIT 1
            """, Map.of("operation", operationId, "user", userId, "now", Timestamp.from(now)),
            (r, i) -> Optional.ofNullable(r.getTimestamp(1)).map(Timestamp::toInstant)).stream().findFirst();
    }

    public void mute(long operationId, long userId, Instant until, long actor, Instant now) {
        lift(operationId, userId, actor, now);
        jdbc.update("""
            INSERT INTO operation_chat_mute(operation_id, user_id, until, created_by, created_at) VALUES (:operation, :user, :until, :actor, :now)
            """, new MapSqlParameterSource().addValue("operation", operationId).addValue("user", userId)
                .addValue("until", until == null ? null : Timestamp.from(until)).addValue("actor", actor).addValue("now", Timestamp.from(now)));
    }

    public int lift(long operationId, long userId, long actor, Instant now) {
        return jdbc.update("""
            UPDATE operation_chat_mute SET lifted_at = :now, lifted_by = :actor
            WHERE operation_id = :operation AND user_id = :user AND lifted_at IS NULL AND (until IS NULL OR until > :now)
            """, Map.of("operation", operationId, "user", userId, "actor", actor, "now", Timestamp.from(now)));
    }

    public List<Muted> muted(long operationId, Instant now) {
        return jdbc.query("""
            SELECT p.public_id, p.callsign, m.until, m.created_at FROM operation_chat_mute m JOIN operator_profile p ON p.user_id = m.user_id
            WHERE m.operation_id = :operation AND m.lifted_at IS NULL AND (m.until IS NULL OR m.until > :now) ORDER BY m.created_at DESC
            """, Map.of("operation", operationId, "now", Timestamp.from(now)), (r, i) -> new Muted(r.getObject(1, UUID.class), r.getString(2),
                r.getTimestamp(3) == null ? null : r.getTimestamp(3).toInstant(), r.getTimestamp(4).toInstant()));
    }

    /** Membro do chat (para silenciar ou mostrar a foto): organizador ou inscrito aprovado. Devolve o id interno. */
    public Optional<Long> member(long operationId, UUID operatorId) {
        return jdbc.query("""
            SELECT u.id FROM operator_profile p JOIN app_user u ON u.id = p.user_id JOIN airsoft_operation o ON o.id = :operation
            WHERE p.public_id = :operator AND (o.organizer_user_id = u.id OR EXISTS (SELECT 1 FROM operation_participant x
                  WHERE x.operation_id = o.id AND x.user_id = u.id AND x.status IN
            """ + MEMBER_STATUSES + "))", Map.of("operation", operationId, "operator", operatorId), (r, i) -> r.getLong(1)).stream().findFirst();
    }

    public Optional<OperationPageRepository.Cover> photo(UUID operatorId) {
        return jdbc.query("""
            SELECT ph.content_type, ph.image_data FROM operator_profile_photo ph JOIN operator_profile p ON p.id = ph.operator_profile_id
            WHERE p.public_id = :operator
            """, Map.of("operator", operatorId), (r, i) -> new OperationPageRepository.Cover(r.getString(1), r.getBytes(2))).stream().findFirst();
    }

    /** Toda acao do organizador fica registrada (quem, quando, alvo e detalhe). */
    public void log(long operationId, long actor, String targetType, UUID target, String action, String reason, Instant now) {
        jdbc.update("""
            INSERT INTO operation_moderation_action(operation_id, actor_user_id, target_type, target_public_id, action, reason, created_at)
            VALUES (:operation, :actor, :type, :target, :action, :reason, :now)
            """, new MapSqlParameterSource().addValue("operation", operationId).addValue("actor", actor).addValue("type", targetType)
                .addValue("target", target).addValue("action", action).addValue("reason", reason).addValue("now", Timestamp.from(now)));
    }

    private static Message message(ResultSet r) throws SQLException {
        Timestamp pinned = r.getTimestamp("pinned_at");
        return new Message(r.getObject("public_id", UUID.class), r.getObject("author_id", UUID.class), r.getString("callsign"),
            r.getString("display_name"), r.getBoolean("has_photo"), r.getLong("photo_version"), r.getString("body"), r.getString("status"),
            r.getTimestamp("created_at").toInstant(), pinned == null ? null : pinned.toInstant(), r.getLong("author_user_id"));
    }
}
