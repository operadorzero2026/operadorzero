package br.com.operadorzero.operation;

import br.com.operadorzero.operation.OperationDtos.OperationFilter;
import br.com.operadorzero.operation.OperationDtos.OperationResponse;
import br.com.operadorzero.operation.OperationDtos.OperationSummary;
import br.com.operadorzero.operation.OperationDtos.SaveOperationRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OperationRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public OperationRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<OperationSummary> search(long userId, OperationFilter filter) {
        return jdbc.query("""
            SELECT o.public_id, o.name, o.description, f.public_id field_public_id, f.name field_name,
                   m.public_id map_public_id, m.name map_name, o.city, o.state_code, o.operation_date,
                   o.presentation_time, o.start_time, o.end_time, o.modality, o.status, o.participant_limit,
                   o.registration_price, COALESCE(pc.total, 0) participant_count, mine.status participant_status,
                   o.organizer_user_id = :userId managed_by_current_user
            FROM airsoft_operation o
            JOIN airsoft_field f ON f.id = o.field_id
            LEFT JOIN field_map m ON m.id = o.map_id
            LEFT JOIN operation_participant mine ON mine.operation_id = o.id AND mine.user_id = :userId
            LEFT JOIN (SELECT operation_id, count(*) total FROM operation_participant
                       WHERE status IN ('APPROVED','CONFIRMED','CHECKED_IN') GROUP BY operation_id) pc ON pc.operation_id = o.id
            WHERE (o.status <> 'DRAFT' OR o.organizer_user_id = :userId)
              AND (:q = '' OR lower(o.name) LIKE :query OR lower(f.name) LIKE :query)
              AND (:city = '' OR lower(o.city) = lower(:city))
              AND (:stateCode = '' OR o.state_code = upper(:stateCode))
              AND (:modality = '' OR o.modality = upper(:modality))
              AND (:status = '' OR o.status = upper(:status))
              AND (CAST(:fromDate AS date) IS NULL OR o.operation_date >= :fromDate)
              AND (CAST(:toDate AS date) IS NULL OR o.operation_date <= :toDate)
            ORDER BY o.operation_date, o.start_time, lower(o.name) LIMIT :limit
            """, params(userId, filter), (row, index) -> new OperationSummary(
                row.getObject("public_id", UUID.class), row.getString("name"), row.getString("description"),
                row.getObject("field_public_id", UUID.class), row.getString("field_name"),
                row.getObject("map_public_id", UUID.class), row.getString("map_name"), row.getString("city"),
                row.getString("state_code"), row.getDate("operation_date").toLocalDate(),
                row.getTime("presentation_time").toLocalTime(), row.getTime("start_time").toLocalTime(),
                row.getTime("end_time").toLocalTime(), row.getString("modality"), row.getString("status"),
                row.getInt("participant_limit"), row.getLong("participant_count"),
                row.getBigDecimal("registration_price"), row.getString("participant_status"),
                row.getBoolean("managed_by_current_user")));
    }

    public Optional<OperationResponse> find(UUID operationId, long userId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT o.*, organizer.public_id organizer_id, profile.callsign organizer_callsign,
                       f.public_id field_public_id, f.name field_name, m.public_id map_public_id, m.name map_name,
                       COALESCE(pc.total, 0) participant_count, mine.status participant_status
                FROM airsoft_operation o JOIN app_user organizer ON organizer.id = o.organizer_user_id
                JOIN operator_profile profile ON profile.user_id = organizer.id
                JOIN airsoft_field f ON f.id = o.field_id LEFT JOIN field_map m ON m.id = o.map_id
                LEFT JOIN operation_participant mine ON mine.operation_id = o.id AND mine.user_id = :userId
                LEFT JOIN (SELECT operation_id, count(*) total FROM operation_participant
                           WHERE status IN ('APPROVED','CONFIRMED','CHECKED_IN') GROUP BY operation_id) pc ON pc.operation_id = o.id
                WHERE o.public_id = :operationId AND (o.status <> 'DRAFT' OR o.organizer_user_id = :userId)
                """, Map.of("operationId", operationId, "userId", userId), (row, index) -> mapResponse(row, userId)));
        } catch (EmptyResultDataAccessException exception) { return Optional.empty(); }
    }

    public Optional<FieldRef> activeField(UUID fieldId) {
        try { return Optional.ofNullable(jdbc.queryForObject(
            "SELECT id, public_id, name, city, state_code FROM airsoft_field WHERE public_id=:id AND status='ACTIVE'",
            Map.of("id", fieldId), (r, i) -> new FieldRef(r.getLong("id"), r.getObject("public_id", UUID.class), r.getString("name"), r.getString("city"), r.getString("state_code")))) ;
        } catch (EmptyResultDataAccessException exception) { return Optional.empty(); }
    }

    public Optional<Long> activeMap(UUID mapId, long fieldId) {
        if (mapId == null) return Optional.empty();
        try { return Optional.ofNullable(jdbc.queryForObject(
            "SELECT id FROM field_map WHERE public_id=:id AND field_id=:fieldId AND status='ACTIVE'",
            Map.of("id", mapId, "fieldId", fieldId), Long.class));
        } catch (EmptyResultDataAccessException exception) { return Optional.empty(); }
    }

    public UUID create(long userId, FieldRef field, Long mapId, SaveOperationRequest request, String modality,
                       String entryMode, Instant now) {
        return jdbc.queryForObject("""
            INSERT INTO airsoft_operation(organizer_user_id, field_id, map_id, name, description, city, state_code,
              operation_date, presentation_time, start_time, end_time, modality, custom_modality, rules,
              participant_limit, team_limit, registration_price, payment_methods, minimum_age, required_equipment,
              fps_limit, entry_mode, approval_required, waiting_list_enabled, created_at, updated_at)
            VALUES (:userId,:fieldId,:mapId,:name,:description,:city,:stateCode,:date,:presentation,:start,:end,
              :modality,:customModality,:rules,:participantLimit,:teamLimit,:price,:paymentMethods,:minimumAge,
              :requiredEquipment,:fpsLimit,:entryMode,:approvalRequired,:waitingListEnabled,:now,:now) RETURNING public_id
            """, saveParams(userId, field, mapId, request, modality, entryMode, now), UUID.class);
    }

    public int updateStatus(UUID operationId, long userId, String status, Instant now) {
        return jdbc.update("""
            UPDATE airsoft_operation SET status=:status, updated_at=:now, version=version+1,
              published_at=CASE WHEN :status IN ('PUBLISHED','REGISTRATION_OPEN') AND published_at IS NULL THEN :now ELSE published_at END
            WHERE public_id=:id AND organizer_user_id=:userId
            """, Map.of("status", status, "now", Timestamp.from(now), "id", operationId, "userId", userId));
    }

    public int publish(UUID operationId, long userId, Instant now) {
        return jdbc.update("""
            UPDATE airsoft_operation
            SET status = 'REGISTRATION_OPEN', published_at = COALESCE(published_at, :now),
                updated_at = :now, version = version + 1
            WHERE public_id = :id AND organizer_user_id = :userId AND status = 'DRAFT'
            """, Map.of("now", Timestamp.from(now), "id", operationId, "userId", userId));
    }

    public int requestParticipation(UUID operationId, long userId, Instant now) {
        return jdbc.update("""
            INSERT INTO operation_participant(operation_id,user_id,status,requested_at,updated_at)
            SELECT id,:userId,CASE WHEN waiting_list_enabled AND
              (SELECT count(*) FROM operation_participant p WHERE p.operation_id=airsoft_operation.id AND p.status IN ('APPROVED','CONFIRMED','CHECKED_IN')) >= participant_limit
              THEN 'WAITING_LIST' WHEN approval_required THEN 'REQUESTED' ELSE 'APPROVED' END,:now,:now
            FROM airsoft_operation WHERE public_id=:id AND status IN ('PUBLISHED','REGISTRATION_OPEN','FULL')
            ON CONFLICT (operation_id,user_id) DO NOTHING
            """, Map.of("id", operationId, "userId", userId, "now", Timestamp.from(now)));
    }

    public int cancelParticipation(UUID operationId, long userId, Instant now) {
        return jdbc.update("""
            UPDATE operation_participant p SET status='CANCELLED',updated_at=:now
            FROM airsoft_operation o WHERE p.operation_id=o.id AND o.public_id=:id AND p.user_id=:userId
              AND p.status IN ('REQUESTED','APPROVED','WAITING_LIST','CONFIRMED')
            """, Map.of("id", operationId, "userId", userId, "now", Timestamp.from(now)));
    }

    private MapSqlParameterSource params(long userId, OperationFilter f) {
        String q = normalize(f.q());
        return new MapSqlParameterSource().addValue("userId", userId).addValue("q", q)
            .addValue("query", "%" + q.toLowerCase() + "%").addValue("city", normalize(f.city()))
            .addValue("stateCode", normalize(f.stateCode())).addValue("modality", normalize(f.modality()))
            .addValue("status", normalize(f.status())).addValue("fromDate", f.from()).addValue("toDate", f.to())
            .addValue("limit", f.limit());
    }

    private MapSqlParameterSource saveParams(long userId, FieldRef f, Long mapId, SaveOperationRequest r,
                                              String modality, String entryMode, Instant now) {
        return new MapSqlParameterSource().addValue("userId", userId).addValue("fieldId", f.id()).addValue("mapId", mapId)
            .addValue("name", normalize(r.name())).addValue("description", normalize(r.description()))
            .addValue("city", f.city()).addValue("stateCode", f.stateCode()).addValue("date", r.operationDate())
            .addValue("presentation", r.presentationTime()).addValue("start", r.startTime()).addValue("end", r.endTime())
            .addValue("modality", modality).addValue("customModality", nullable(r.customModality())).addValue("rules", nullable(r.rules()))
            .addValue("participantLimit", r.participantLimit()).addValue("teamLimit", r.teamLimit())
            .addValue("price", r.registrationPrice()).addValue("paymentMethods", nullable(r.paymentMethods()))
            .addValue("minimumAge", r.minimumAge()).addValue("requiredEquipment", nullable(r.requiredEquipment()))
            .addValue("fpsLimit", r.fpsLimit()).addValue("entryMode", entryMode).addValue("approvalRequired", r.approvalRequired())
            .addValue("waitingListEnabled", r.waitingListEnabled()).addValue("now", Timestamp.from(now));
    }

    private OperationResponse mapResponse(ResultSet r, long userId) throws SQLException {
        return new OperationResponse(r.getObject("public_id", UUID.class), r.getString("name"), r.getString("description"),
            r.getObject("organizer_id", UUID.class), r.getString("organizer_callsign"), r.getObject("field_public_id", UUID.class),
            r.getString("field_name"), r.getObject("map_public_id", UUID.class), r.getString("map_name"), r.getString("city"),
            r.getString("state_code"), r.getDate("operation_date").toLocalDate(), r.getTime("presentation_time").toLocalTime(),
            r.getTime("start_time").toLocalTime(), r.getTime("end_time").toLocalTime(), r.getString("modality"),
            r.getString("custom_modality"), r.getString("rules"), r.getInt("participant_limit"), (Integer) r.getObject("team_limit"),
            r.getBigDecimal("registration_price"), r.getString("payment_methods"), r.getInt("minimum_age"),
            r.getString("required_equipment"), (Integer) r.getObject("fps_limit"), r.getString("entry_mode"),
            r.getBoolean("approval_required"), r.getBoolean("waiting_list_enabled"), r.getString("status"),
            r.getLong("participant_count"), r.getString("participant_status"), r.getLong("organizer_user_id") == userId, r.getLong("version"));
    }
    private String normalize(String value) { return value == null ? "" : value.trim().replaceAll("\\s+", " "); }
    private String nullable(String value) { String normalized = normalize(value); return normalized.isEmpty() ? null : normalized; }
    public record FieldRef(long id, UUID publicId, String name, String city, String stateCode) {}
}
