package br.com.operadorzero.operation;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Pagina da operacao (link permanente, inclusive depois do jogo) e o historico de operacoes realizadas de cada operador. */
@Repository
public class OperationPageRepository {
    /** Fim do jogo no fuso de Brasilia. */
    static final String ENDS_AT = "((o.operation_date + o.end_time) AT TIME ZONE 'America/Sao_Paulo')";
    private final NamedParameterJdbcTemplate jdbc;

    public OperationPageRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Page(UUID id, String name, String description, String briefing, boolean hasCover, long coverVersion, LocalDate date,
                       LocalTime presentationTime, LocalTime startTime, LocalTime endTime, String fieldName, String city, String stateCode,
                       String locationUrl, BigDecimal latitude, BigDecimal longitude, String modality, String customModality,
                       BigDecimal price, String status, Instant startsAt, Instant endsAt, boolean registrationOpen, String registrationModel,
                       String organizerCallsign, String organizerUsername, long participantCount) {}

    public record History(UUID id, String name, boolean hasCover, long coverVersion, LocalDate date, String organizerCallsign,
                          String teamName, String primaryRole, boolean medic, boolean radio) {}

    public record Cover(String contentType, byte[] data) {}

    public Optional<Page> page(UUID operationId) {
        return jdbc.query("""
            SELECT o.public_id, o.name, o.description, o.briefing, oc.operation_id IS NOT NULL has_cover, COALESCE(oc.version, 0) cover_version,
                   o.operation_date, o.presentation_time, o.start_time, o.end_time, f.name field_name, o.city, o.state_code, f.location_url,
                   f.latitude, f.longitude, o.modality, o.custom_modality, o.registration_price, o.status,
            """ + OperationRegistrationRepository.STARTS_AT + " starts_at, " + ENDS_AT + """
             ends_at, (o.status IN ('PUBLISHED','REGISTRATION_OPEN','FULL') AND o.registration_closed_at IS NULL
                   AND """ + OperationRegistrationRepository.STARTS_AT + """
             > now()) registration_open, o.registration_model, prof.callsign, u.username,
                   (SELECT count(*) FROM operation_participant p WHERE p.operation_id = o.id
                      AND p.status IN ('REQUESTED','APPROVED','WAITING_LIST','CONFIRMED','CHECKED_IN')) participants
            FROM airsoft_operation o
            JOIN airsoft_field f ON f.id = o.field_id
            JOIN app_user u ON u.id = o.organizer_user_id
            JOIN operator_profile prof ON prof.user_id = u.id
            LEFT JOIN operation_cover oc ON oc.operation_id = o.id
            WHERE o.public_id = :id AND o.deleted_at IS NULL AND o.status <> 'DRAFT'
            """, Map.of("id", operationId), (r, i) -> new Page(r.getObject("public_id", UUID.class), r.getString("name"),
                r.getString("description"), r.getString("briefing"), r.getBoolean("has_cover"), r.getLong("cover_version"),
                r.getDate("operation_date").toLocalDate(), time(r.getTime("presentation_time")), time(r.getTime("start_time")),
                time(r.getTime("end_time")), r.getString("field_name"), r.getString("city"), r.getString("state_code"),
                r.getString("location_url"), r.getBigDecimal("latitude"), r.getBigDecimal("longitude"), r.getString("modality"),
                r.getString("custom_modality"), r.getBigDecimal("registration_price"), r.getString("status"),
                instant(r.getTimestamp("starts_at")), instant(r.getTimestamp("ends_at")), r.getBoolean("registration_open"),
                r.getString("registration_model"), r.getString("callsign"), r.getString("username"), r.getLong("participants")))
            .stream().findFirst();
    }

    public Optional<Cover> cover(UUID operationId) {
        return jdbc.query("""
            SELECT c.content_type, c.image_data FROM operation_cover c JOIN airsoft_operation o ON o.id = c.operation_id
            WHERE o.public_id = :id AND o.deleted_at IS NULL AND o.status <> 'DRAFT'
            """, Map.of("id", operationId), (r, i) -> new Cover(r.getString(1), r.getBytes(2))).stream().findFirst();
    }

    /**
     * Operacoes ja jogadas (finalizadas ou que ja passaram do horario de termino) em que a pessoa estava inscrita no fim,
     * mais recentes primeiro. Quem tem bloqueio com o dono do perfil nao ve nada.
     */
    public List<History> history(String username, long viewer) {
        return jdbc.query("""
            SELECT o.public_id, o.name, oc.operation_id IS NOT NULL has_cover, COALESCE(oc.version, 0) cover_version, o.operation_date,
                   org.callsign organizer, ot.name team_name, p.primary_role, p.is_medic, p.is_radio
            FROM app_user u
            JOIN operation_participant p ON p.user_id = u.id AND p.status IN ('APPROVED','CONFIRMED','CHECKED_IN')
            JOIN airsoft_operation o ON o.id = p.operation_id AND o.deleted_at IS NULL AND o.status <> 'CANCELLED'
            JOIN operator_profile org ON org.user_id = o.organizer_user_id
            LEFT JOIN operation_team ot ON ot.id = p.operation_team_id
            LEFT JOIN operation_cover oc ON oc.operation_id = o.id
            WHERE lower(u.username) = lower(:username) AND u.status = 'ACTIVE'
              AND (o.status = 'FINISHED' OR\s""" + ENDS_AT + """
             < now())
              AND NOT EXISTS (SELECT 1 FROM operator_block ob
                  WHERE (ob.blocker_user_id = :viewer AND ob.blocked_user_id = u.id) OR (ob.blocker_user_id = u.id AND ob.blocked_user_id = :viewer))
            ORDER BY o.operation_date DESC, o.start_time DESC LIMIT 60
            """, Map.of("username", username, "viewer", viewer), (r, i) -> new History(r.getObject("public_id", UUID.class),
                r.getString("name"), r.getBoolean("has_cover"), r.getLong("cover_version"), r.getDate("operation_date").toLocalDate(),
                r.getString("organizer"), r.getString("team_name"), r.getString("primary_role"), r.getBoolean("is_medic"), r.getBoolean("is_radio")));
    }

    private static LocalTime time(java.sql.Time value) {
        return value == null ? null : value.toLocalTime();
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
