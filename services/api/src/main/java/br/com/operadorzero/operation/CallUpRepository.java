package br.com.operadorzero.operation;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Convocacoes do feed: jogos com inscricao aberta, montados a partir das operacoes que ja existem. */
@Repository
public class CallUpRepository {
    /** Status em que a inscricao existente aceita pedidos (mesma regra de OperationRepository.requestParticipation). */
    static final List<String> OPEN_STATUSES = List.of("PUBLISHED", "REGISTRATION_OPEN", "FULL");
    private static final String ACTIVE = "('REQUESTED','APPROVED','CONFIRMED','CHECKED_IN')";

    private final NamedParameterJdbcTemplate jdbc;

    public CallUpRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record CallUpRow(long id, UUID publicId, String name, LocalDate date, LocalTime presentationTime, LocalTime startTime,
                            String city, String stateCode, String fieldName, String locationUrl, BigDecimal latitude, BigDecimal longitude,
                            String modality, String customModality, String gameSize, String status, long occupied, Integer limit,
                            BigDecimal price, boolean approvalRequired, boolean waitingListEnabled, String viewerStatus, double score) {}
    public record Squad(UUID id, String name, int capacity, long occupied) {}
    public record Team(UUID id, String name, int capacity, long occupied, List<Squad> squads) {}

    /**
     * Jogos dos proximos 45 dias com inscricao aberta, priorizando campos proximos ou ja frequentados e datas mais perto.
     * pontuacao = (1 + mesmo estado 1 + mesma cidade 1 + campo onde ja jogou 1,5) / (1 + dias ate o jogo / 7)
     */
    public List<CallUpRow> ranked(long viewer, LocalDate today, int limit) {
        return jdbc.query(select("""
            WHERE o.deleted_at IS NULL AND o.status IN (:open) AND o.operation_date BETWEEN :today AND :until
            ORDER BY score DESC, o.operation_date, o.start_time, o.public_id
            LIMIT :limit
            """), new MapSqlParameterSource().addValue("viewer", viewer).addValue("open", OPEN_STATUSES)
                .addValue("today", Date.valueOf(today)).addValue("until", Date.valueOf(today.plusDays(45))).addValue("limit", limit),
            (r, i) -> row(r));
    }

    /** Uma Convocacao (link compartilhado); viewer -1 quando quem abre nao entrou. */
    public List<CallUpRow> one(UUID operationId, long viewer, LocalDate today) {
        return jdbc.query(select("""
            WHERE o.deleted_at IS NULL AND o.public_id = :id AND o.operation_date >= :today
              AND o.status IN ('PUBLISHED','REGISTRATION_OPEN','FULL','CONFIRMATION_PENDING')
            """), new MapSqlParameterSource().addValue("viewer", viewer).addValue("id", operationId).addValue("today", Date.valueOf(today)),
            (r, i) -> row(r));
    }

    /** Lados (times) e pelotoes abertos, com ocupacao, para escolher onde se alistar. */
    public Map<Long, List<Team>> teams(List<Long> operationIds) {
        Map<Long, List<Team>> result = new LinkedHashMap<>();
        if (operationIds.isEmpty()) return result;
        Map<Long, List<Squad>> squads = new LinkedHashMap<>();
        jdbc.query("SELECT s.operation_team_id, s.public_id, s.name, s.capacity, "
            + "(SELECT count(*) FROM operation_participant p WHERE p.operation_squad_id = s.id AND p.status IN " + ACTIVE + ") occupied "
            + "FROM operation_squad s JOIN operation_team t ON t.id = s.operation_team_id "
            + "WHERE t.operation_id IN (:ids) AND s.status = 'OPEN' ORDER BY s.sort_order", Map.of("ids", operationIds), r -> {
                squads.computeIfAbsent(r.getLong(1), key -> new ArrayList<>()).add(new Squad(r.getObject(2, UUID.class), r.getString(3), r.getInt(4), r.getLong(5)));
            });
        jdbc.query("SELECT t.operation_id, t.id, t.public_id, t.name, t.capacity, "
            + "(SELECT count(*) FROM operation_participant p WHERE p.operation_team_id = t.id AND p.status IN " + ACTIVE + ") occupied "
            + "FROM operation_team t WHERE t.operation_id IN (:ids) AND t.status = 'OPEN' ORDER BY t.sort_order", Map.of("ids", operationIds), r -> {
                result.computeIfAbsent(r.getLong(1), key -> new ArrayList<>()).add(new Team(r.getObject(3, UUID.class), r.getString(4), r.getInt(5),
                    r.getLong(6), squads.getOrDefault(r.getLong(2), List.of())));
            });
        return result;
    }

    private static String select(String where) {
        return """
            WITH me AS (SELECT city, state_code FROM operator_profile WHERE user_id = :viewer),
            played AS (SELECT DISTINCT o.field_id FROM operation_participant op JOIN airsoft_operation o ON o.id = op.operation_id
                       WHERE op.user_id = :viewer AND op.status IN ('CONFIRMED','CHECKED_IN'))
            SELECT o.id, o.public_id, o.name, o.operation_date, o.presentation_time, o.start_time, o.city, o.state_code,
                   f.name field_name, f.location_url, f.latitude, f.longitude, o.modality, o.custom_modality, o.game_size, o.status,
                   (SELECT count(*) FROM operation_participant p WHERE p.operation_id = o.id AND p.status IN """ + ACTIVE + """
                   ) occupied, o.participant_limit, o.registration_price, o.approval_required, o.waiting_list_enabled,
                   (SELECT p.status FROM operation_participant p WHERE p.operation_id = o.id AND p.user_id = :viewer
                      AND p.status NOT IN ('CANCELLED','REJECTED')) viewer_status,
                   (1 + CASE WHEN o.state_code = me.state_code THEN 1 ELSE 0 END
                      + CASE WHEN o.state_code = me.state_code AND lower(o.city) = lower(me.city) THEN 1 ELSE 0 END
                      + CASE WHEN o.field_id IN (SELECT field_id FROM played) THEN 1.5 ELSE 0 END)
                   / (1 + GREATEST(0, o.operation_date - CAST(:today AS date)) / 7.0) score
            FROM airsoft_operation o
            JOIN airsoft_field f ON f.id = o.field_id
            LEFT JOIN me ON TRUE
            """ + where;
    }

    private static CallUpRow row(ResultSet r) throws SQLException {
        java.sql.Time presentation = r.getTime("presentation_time");
        return new CallUpRow(r.getLong("id"), r.getObject("public_id", UUID.class), r.getString("name"), r.getDate("operation_date").toLocalDate(),
            presentation == null ? null : presentation.toLocalTime(), r.getTime("start_time").toLocalTime(), r.getString("city"),
            r.getString("state_code"), r.getString("field_name"), r.getString("location_url"), r.getBigDecimal("latitude"),
            r.getBigDecimal("longitude"), r.getString("modality"), r.getString("custom_modality"), r.getString("game_size"), r.getString("status"),
            r.getLong("occupied"), (Integer) r.getObject("participant_limit"), r.getBigDecimal("registration_price"),
            r.getBoolean("approval_required"), r.getBoolean("waiting_list_enabled"), r.getString("viewer_status"), r.getDouble("score"));
    }
}
