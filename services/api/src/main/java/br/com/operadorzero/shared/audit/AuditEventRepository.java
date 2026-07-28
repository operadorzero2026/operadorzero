package br.com.operadorzero.shared.audit;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditEventRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public AuditEventRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(long actorUserId, String action, String entityType, UUID entityPublicId, String reason, Instant now) {
        String correlationId = MDC.get("correlationId");
        jdbc.update("""
            INSERT INTO audit_event(actor_user_id, action, entity_type, entity_public_id, reason, correlation_id, occurred_at)
            VALUES (:actorUserId, :action, :entityType, :entityPublicId, :reason, :correlationId, :occurredAt)
            """, new MapSqlParameterSource()
                .addValue("actorUserId", actorUserId)
                .addValue("action", action)
                .addValue("entityType", entityType)
                .addValue("entityPublicId", entityPublicId)
                .addValue("reason", reason)
                .addValue("correlationId", correlationId == null ? "unavailable" : correlationId)
                .addValue("occurredAt", Timestamp.from(now)));
    }
}

