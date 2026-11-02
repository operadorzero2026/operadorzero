package br.com.operadorzero.operation;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** O que quem esta logado pode oferecer a um operador encontrado na busca: equipe e jogos. */
@Repository
public class OperatorInviteRepository {
    private static final String ACTIVE_PARTICIPATION = "('REQUESTED','APPROVED','WAITING_LIST','CONFIRMED','CHECKED_IN')";
    private final NamedParameterJdbcTemplate jdbc;

    public OperatorInviteRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Target(long userId, UUID operatorId, String username, String callsign, boolean hasTeam) {}
    public record ManagedTeam(UUID id, String name, String role) {}
    public record InvitableOperation(UUID id, String name, LocalDate date, String fieldName) {}

    public Optional<Target> target(String username, long viewer) {
        return jdbc.query("""
            SELECT u.id, p.public_id, u.username, p.callsign,
                   EXISTS (SELECT 1 FROM team_member tm WHERE tm.user_id = u.id AND tm.left_at IS NULL) has_team
            FROM app_user u JOIN operator_profile p ON p.user_id = u.id
            WHERE lower(u.username) = lower(:username) AND u.status = 'ACTIVE' AND u.id <> :viewer
              AND NOT EXISTS (SELECT 1 FROM operator_block ob
                  WHERE (ob.blocker_user_id = :viewer AND ob.blocked_user_id = u.id)
                     OR (ob.blocker_user_id = u.id AND ob.blocked_user_id = :viewer))
            """, Map.of("username", username, "viewer", viewer), (r, i) -> new Target(r.getLong(1), r.getObject(2, UUID.class),
                r.getString(3), r.getString(4), r.getBoolean(5))).stream().findFirst();
    }

    public boolean friends(long a, long b) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM operator_friendship f WHERE f.status = 'ACCEPTED'
              AND LEAST(f.requester_user_id, f.addressee_user_id) = LEAST(:a, :b)
              AND GREATEST(f.requester_user_id, f.addressee_user_id) = GREATEST(:a, :b))
            """, Map.of("a", a, "b", b), Boolean.class));
    }

    /** Equipe em que quem esta logado e capitao ou gestor (os unicos que podem convidar). */
    public Optional<ManagedTeam> managedTeam(long viewer) {
        return jdbc.query("""
            SELECT t.public_id, t.name, tm.role FROM team_member tm JOIN team t ON t.id = tm.team_id AND t.status = 'ACTIVE'
            WHERE tm.user_id = :viewer AND tm.left_at IS NULL AND tm.role IN ('CAPTAIN','MANAGER')
            """, Map.of("viewer", viewer), (r, i) -> new ManagedTeam(r.getObject(1, UUID.class), r.getString(2), r.getString(3)))
            .stream().findFirst();
    }

    /**
     * Jogos futuros com inscricao aberta que a pessoa organiza ou dos quais participa, em que o amigo ainda nao esta
     * inscrito nem foi convidado.
     */
    public List<InvitableOperation> invitableOperations(long viewer, long target, LocalDate today) {
        return jdbc.query("SELECT o.public_id, o.name, o.operation_date, f.name field_name FROM airsoft_operation o "
            + "JOIN airsoft_field f ON f.id = o.field_id "
            + "WHERE o.deleted_at IS NULL AND o.status IN ('PUBLISHED','REGISTRATION_OPEN','FULL') AND o.operation_date >= :today "
            + "AND (o.organizer_user_id = :viewer OR EXISTS (SELECT 1 FROM operation_participant p WHERE p.operation_id = o.id "
            + "     AND p.user_id = :viewer AND p.status IN " + ACTIVE_PARTICIPATION + ")) "
            + "AND NOT EXISTS (SELECT 1 FROM operation_participant p WHERE p.operation_id = o.id AND p.user_id = :target "
            + "     AND p.status IN " + ACTIVE_PARTICIPATION + ") "
            + "AND NOT EXISTS (SELECT 1 FROM operation_invitation i WHERE i.operation_id = o.id AND i.invitee_user_id = :target) "
            + "ORDER BY o.operation_date, o.start_time LIMIT 20",
            new MapSqlParameterSource().addValue("viewer", viewer).addValue("target", target).addValue("today", Date.valueOf(today)),
            (r, i) -> new InvitableOperation(r.getObject(1, UUID.class), r.getString(2), r.getDate(3).toLocalDate(), r.getString(4)));
    }

    public long operationId(UUID operationId) {
        return jdbc.queryForObject("SELECT id FROM airsoft_operation WHERE public_id = :id", Map.of("id", operationId), Long.class);
    }

    public boolean insertInvitation(long operationId, long inviter, long invitee, Instant now) {
        return jdbc.update("""
            INSERT INTO operation_invitation(operation_id, inviter_user_id, invitee_user_id, created_at)
            VALUES (:operation, :inviter, :invitee, :now) ON CONFLICT DO NOTHING
            """, Map.of("operation", operationId, "inviter", inviter, "invitee", invitee, "now", Timestamp.from(now))) > 0;
    }

    public String callsign(long userId) {
        return jdbc.query("SELECT callsign FROM operator_profile WHERE user_id = :id", Map.of("id", userId), (r, i) -> r.getString(1))
            .stream().findFirst().orElse("Um amigo");
    }
}
