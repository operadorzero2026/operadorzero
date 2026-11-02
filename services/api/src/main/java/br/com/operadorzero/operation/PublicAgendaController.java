package br.com.operadorzero.operation;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Agenda aberta da pagina inicial: so operacoes publicadas e futuras, sem dados de organizadores ou participantes. */
@RestController
@RequestMapping("/api/public/operations")
public class PublicAgendaController {
    private static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");
    private static final int LIMIT = 12;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public PublicAgendaController(NamedParameterJdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    PublicAgendaController(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @GetMapping
    ResponseEntity<PublicAgendaResponse> upcoming() {
        List<PublicOperation> items = jdbc.query("""
            SELECT o.public_id, o.name, o.operation_date, o.start_time, o.city, o.state_code, f.name field_name,
                   o.modality, o.status, o.participant_limit, o.registration_price,
                   (SELECT count(*) FROM operation_participant p WHERE p.operation_id = o.id
                      AND p.status IN ('APPROVED','CONFIRMED','CHECKED_IN')) participant_count
            FROM airsoft_operation o JOIN airsoft_field f ON f.id = o.field_id
            WHERE o.deleted_at IS NULL AND o.operation_date >= :today
              AND o.status IN ('PUBLISHED','REGISTRATION_OPEN','FULL','CONFIRMATION_PENDING')
            ORDER BY o.operation_date, o.start_time, lower(o.name) LIMIT :limit
            """, Map.of("today", Date.valueOf(LocalDate.now(clock.withZone(BRAZIL))), "limit", LIMIT),
            (row, index) -> new PublicOperation(row.getObject("public_id", UUID.class), row.getString("name"),
                row.getDate("operation_date").toLocalDate(), row.getTime("start_time").toLocalTime(), row.getString("city"),
                row.getString("state_code"), row.getString("field_name"), row.getString("modality"), row.getString("status"),
                row.getLong("participant_count"), (Integer) row.getObject("participant_limit"),
                row.getBigDecimal("registration_price")));
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
            .body(new PublicAgendaResponse(items));
    }

    public record PublicOperation(UUID id, String name, LocalDate operationDate, LocalTime startTime, String city,
                                  String stateCode, String fieldName, String modality, String status,
                                  long participantCount, Integer participantLimit, BigDecimal registrationPrice) {}

    public record PublicAgendaResponse(List<PublicOperation> items) {}
}
