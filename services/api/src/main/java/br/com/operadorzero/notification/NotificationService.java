package br.com.operadorzero.notification;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.shared.web.BusinessException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Grava avisos na mesma transacao da acao que os gera: se a acao falhar, o aviso nao existe. */
@Service
public class NotificationService implements Notifier {
    private static final int PAGE = 50;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public NotificationService(NamedParameterJdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    NotificationService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void toUser(long userId, String kind, String title, String body, String targetView) {
        insert("SELECT CAST(:userId AS bigint)", params(kind, title, body, targetView).addValue("userId", userId));
    }

    @Override
    public void toUserAt(long userId, String kind, String title, String body, String targetView, String targetPath) {
        insert("SELECT CAST(:userId AS bigint)", params(kind, title, body, targetView).addValue("userId", userId)
            .addValue("targetPath", targetPath));
    }

    @Override
    public void toOperator(UUID operatorId, String kind, String title, String body, String targetView) {
        insert("SELECT user_id FROM operator_profile WHERE public_id = :operatorId",
            params(kind, title, body, targetView).addValue("operatorId", operatorId));
    }

    @Override
    public void toTeamManagers(long teamId, String kind, String title, String body, String targetView) {
        insert("SELECT user_id FROM team_member WHERE team_id = :teamId AND left_at IS NULL AND role IN ('CAPTAIN','MANAGER')",
            params(kind, title, body, targetView).addValue("teamId", teamId));
    }

    @Override
    public void toOperationParticipants(UUID operationId, String kind, String title, String body, String targetView) {
        insert("""
            SELECT p.user_id FROM operation_participant p JOIN airsoft_operation o ON o.id = p.operation_id
            WHERE o.public_id = :operationId AND p.user_id <> o.organizer_user_id
              AND p.status IN ('REQUESTED','APPROVED','WAITING_LIST','CONFIRMED','CHECKED_IN')
            """, params(kind, title, body, targetView).addValue("operationId", operationId));
    }

    @Transactional(readOnly = true)
    public NotificationPage list(AuthenticatedUser user) {
        List<NotificationResponse> items = jdbc.query("""
            SELECT public_id, kind, title, body, target_view, target_path, created_at, read_at FROM user_notification
            WHERE user_id = :userId ORDER BY created_at DESC, id DESC LIMIT :limit
            """, Map.of("userId", user.internalId(), "limit", PAGE), (row, index) -> new NotificationResponse(
                row.getObject("public_id", UUID.class), row.getString("kind"), row.getString("title"), row.getString("body"),
                row.getString("target_view"), row.getString("target_path"), row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("read_at") == null ? null : row.getTimestamp("read_at").toInstant()));
        return new NotificationPage(items, unread(user));
    }

    @Transactional(readOnly = true)
    public long unread(AuthenticatedUser user) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM user_notification WHERE user_id = :userId AND read_at IS NULL",
            Map.of("userId", user.internalId()), Long.class);
        return count == null ? 0 : count;
    }

    @Transactional
    public void markRead(AuthenticatedUser user, UUID id) {
        int updated = jdbc.update("""
            UPDATE user_notification SET read_at = COALESCE(read_at, :now) WHERE public_id = :id AND user_id = :userId
            """, Map.of("now", Timestamp.from(clock.instant()), "id", id, "userId", user.internalId()));
        if (updated != 1) {
            throw BusinessException.notFound("Notificação não encontrada.");
        }
    }

    @Transactional
    public void markAllRead(AuthenticatedUser user) {
        jdbc.update("UPDATE user_notification SET read_at = :now WHERE user_id = :userId AND read_at IS NULL",
            Map.of("now", Timestamp.from(clock.instant()), "userId", user.internalId()));
    }

    private void insert(String recipients, MapSqlParameterSource params) {
        if (!params.hasValue("targetPath")) params.addValue("targetPath", null);
        jdbc.update("INSERT INTO user_notification(user_id, kind, title, body, target_view, target_path, created_at) "
            + "SELECT r.user_id, :kind, :title, :body, :targetView, :targetPath, :now FROM (" + recipients + ") AS r(user_id)", params);
    }

    private MapSqlParameterSource params(String kind, String title, String body, String targetView) {
        Instant now = clock.instant();
        return new MapSqlParameterSource().addValue("kind", kind).addValue("title", truncate(title, 160))
            .addValue("body", body == null ? null : truncate(body, 500)).addValue("targetView", targetView)
            .addValue("now", Timestamp.from(now));
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    public record NotificationResponse(UUID id, String kind, String title, String body, String targetView, String targetPath,
                                       Instant createdAt, Instant readAt) {}

    public record NotificationPage(List<NotificationResponse> items, long unread) {}
}
