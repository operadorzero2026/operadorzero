package br.com.operadorzero.gamification;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * XP por jogo concluido: depois do horario de termino, quem confirmou presenca ou fez check-in ganha o XP de
 * OPERATION_COMPLETED (uma vez por operacao; o livro-razao impede repetir). Olha so as ultimas semanas.
 */
@Component
public class OperationXpJob {
    private static final Logger log = LoggerFactory.getLogger(OperationXpJob.class);
    private static final String ENDS_AT = "((o.operation_date + o.end_time) AT TIME ZONE 'America/Sao_Paulo')";
    static final Duration LOOKBACK = Duration.ofDays(30);

    record Pending(long userId, long operationId) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final XpService xp;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Autowired
    public OperationXpJob(NamedParameterJdbcTemplate jdbc, XpService xp, TransactionTemplate transactions) {
        this(jdbc, xp, transactions, Clock.systemUTC());
    }

    OperationXpJob(NamedParameterJdbcTemplate jdbc, XpService xp, TransactionTemplate transactions, Clock clock) {
        this.jdbc = jdbc;
        this.xp = xp;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${OPERATION_XP_JOB_INITIAL:PT4M}", fixedDelayString = "${OPERATION_XP_JOB_DELAY:PT30M}")
    public void run() {
        Instant now = clock.instant();
        List<Pending> pending = jdbc.query("""
            SELECT p.user_id, o.id FROM operation_participant p
            JOIN airsoft_operation o ON o.id = p.operation_id
            WHERE o.deleted_at IS NULL AND o.status NOT IN ('DRAFT', 'CANCELLED')
              AND p.status IN ('CONFIRMED', 'CHECKED_IN')
              AND
            """ + " " + ENDS_AT + " < :now AND " + ENDS_AT + """
             > :since
              AND NOT EXISTS (SELECT 1 FROM xp_event e WHERE e.user_id = p.user_id AND e.action_code = 'OPERATION_COMPLETED'
                              AND e.source_type = 'OPERATION' AND e.source_id = o.id)
            ORDER BY o.id LIMIT 500
            """, Map.of("now", Timestamp.from(now), "since", Timestamp.from(now.minus(LOOKBACK))),
            (r, i) -> new Pending(r.getLong(1), r.getLong(2)));
        int awarded = 0;
        for (Pending item : pending) {
            Integer gained = transactions.execute(status -> xp.award(item.userId(), "OPERATION_COMPLETED", "OPERATION", item.operationId(), null));
            if (gained != null && gained > 0) awarded++;
        }
        if (awarded > 0) log.info("XP de jogo concluido concedido a {} participacoes", awarded);
    }
}
