package br.com.operadorzero.operation;

import br.com.operadorzero.operation.OperationRegistrationDtos.ChangeRequestItem;
import br.com.operadorzero.operation.OperationRegistrationDtos.Participant;
import br.com.operadorzero.operation.OperationRegistrationDtos.RemovedItem;
import br.com.operadorzero.operation.OperationRegistrationDtos.TeamInfo;
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

@Repository
public class OperationRegistrationRepository {
    /** Capacidade tecnica usada como "sem limite" (as colunas de capacidade sao obrigatorias). */
    public static final int UNLIMITED = 100_000;
    /** Inscricao ativa: aparece na lista, ocupa vaga e da acesso ao chat. */
    static final String ACTIVE = "('REQUESTED','APPROVED','WAITING_LIST','CONFIRMED','CHECKED_IN')";
    /** Ocupam vaga (a lista de espera nao). */
    static final String OCCUPYING = "('REQUESTED','APPROVED','CONFIRMED','CHECKED_IN')";
    /** Horario de inicio do jogo no fuso de Brasilia. */
    static final String STARTS_AT = "((o.operation_date + o.start_time) AT TIME ZONE 'America/Sao_Paulo')";

    private final NamedParameterJdbcTemplate jdbc;

    public OperationRegistrationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Op(long id, UUID publicId, long organizerUserId, String name, String model, String status, Instant closedAt,
                     Instant startsAt, boolean approvalRequired, boolean waitingListEnabled, Integer participantLimit,
                     boolean teamChangeEnabled) {}

    public record Mine(long id, String status, String primaryRole, boolean medic, boolean radio, Long teamId, UUID teamPublicId,
                       String teamName, String removalReason, Instant removedAt) {}

    public record Target(long participantId, long userId, String status, Long teamId) {}

    public record TeamRow(long id, UUID publicId, String status, int capacity, boolean hasSquads) {}

    public record ChangeRow(long id, long participantId, Long fromTeamId, long toTeamId, String status, long userId) {}

    private static final String OP_COLUMNS = """
        o.id, o.public_id, o.organizer_user_id, o.name, o.registration_model, o.status, o.registration_closed_at,
        """ + STARTS_AT + """
         starts_at, o.approval_required, o.waiting_list_enabled, o.participant_limit, o.team_change_requests_enabled
        """;

    /** Operacao visivel para quem pede (rascunho so para o organizador). lock serializa decisoes de vaga. */
    public Optional<Op> operation(UUID operationId, long viewer, boolean lock) {
        return jdbc.query("SELECT " + OP_COLUMNS + " FROM airsoft_operation o WHERE o.public_id = :id AND o.deleted_at IS NULL"
                + " AND (o.status <> 'DRAFT' OR o.organizer_user_id = :viewer)" + (lock ? " FOR UPDATE" : ""),
            Map.of("id", operationId, "viewer", viewer), (r, i) -> op(r)).stream().findFirst();
    }

    public Optional<Mine> mine(long operationId, long userId, boolean lock) {
        return jdbc.query("""
            SELECT p.id, p.status, p.primary_role, p.is_medic, p.is_radio, p.operation_team_id, ot.public_id team_public_id,
                   ot.name team_name, p.removal_reason, p.removed_at
            FROM operation_participant p LEFT JOIN operation_team ot ON ot.id = p.operation_team_id
            WHERE p.operation_id = :operation AND p.user_id = :user""" + (lock ? " FOR UPDATE OF p" : ""),
            Map.of("operation", operationId, "user", userId), (r, i) -> new Mine(r.getLong("id"), r.getString("status"),
                r.getString("primary_role"), r.getBoolean("is_medic"), r.getBoolean("is_radio"), (Long) r.getObject("operation_team_id"),
                r.getObject("team_public_id", UUID.class), r.getString("team_name"), r.getString("removal_reason"), instant(r, "removed_at")))
            .stream().findFirst();
    }

    /** Participante escolhido pelo organizador (pelo id publico do perfil). */
    public Optional<Target> target(long operationId, UUID operatorId) {
        return jdbc.query("""
            SELECT p.id, p.user_id, p.status, p.operation_team_id FROM operation_participant p
            JOIN operator_profile prof ON prof.user_id = p.user_id
            WHERE p.operation_id = :operation AND prof.public_id = :operator FOR UPDATE OF p
            """, Map.of("operation", operationId, "operator", operatorId),
            (r, i) -> new Target(r.getLong("id"), r.getLong("user_id"), r.getString("status"), (Long) r.getObject("operation_team_id")))
            .stream().findFirst();
    }

    public Optional<TeamRow> team(long operationId, UUID teamId) {
        if (teamId == null) return Optional.empty();
        return jdbc.query("""
            SELECT t.id, t.public_id, t.status, t.capacity, EXISTS (SELECT 1 FROM operation_squad s WHERE s.operation_team_id = t.id) has_squads
            FROM operation_team t WHERE t.operation_id = :operation AND t.public_id = :team FOR UPDATE
            """, Map.of("operation", operationId, "team", teamId), (r, i) -> new TeamRow(r.getLong("id"),
                r.getObject("public_id", UUID.class), r.getString("status"), r.getInt("capacity"), r.getBoolean("has_squads")))
            .stream().findFirst();
    }

    /** Esquadrao aberto do time, com vaga (ou null se nao existir). */
    public Optional<long[]> squad(long teamId, UUID squadId) {
        if (squadId == null) return Optional.empty();
        return jdbc.query("""
            SELECT s.id, s.capacity, (SELECT count(*) FROM operation_participant p WHERE p.operation_squad_id = s.id AND p.status IN
            """ + OCCUPYING + """
            ) occupied FROM operation_squad s WHERE s.operation_team_id = :team AND s.public_id = :squad AND s.status = 'OPEN' FOR UPDATE
            """, Map.of("team", teamId, "squad", squadId),
            (r, i) -> new long[] {r.getLong("id"), r.getInt("capacity"), r.getLong("occupied")}).stream().findFirst();
    }

    public long occupiedInOperation(long operationId) {
        return count("SELECT count(*) FROM operation_participant WHERE operation_id = :id AND status IN " + OCCUPYING, operationId);
    }

    public long occupiedInTeam(long teamId) {
        return count("SELECT count(*) FROM operation_participant WHERE operation_team_id = :id AND status IN " + OCCUPYING, teamId);
    }

    /**
     * Inscreve ou reinscreve (depois de cancelar, ser recusado ou ter o retorno autorizado). Uma linha por pessoa e operacao:
     * nunca duplica. Dados financeiros (payment_confirmed_at) e de remocao ficam como estavam (historico).
     */
    public int register(long operationId, long userId, String status, Long teamId, Long squadId, String role, boolean medic, boolean radio, Instant now) {
        return jdbc.update("""
            INSERT INTO operation_participant(operation_id, user_id, status, operation_team_id, operation_squad_id, primary_role,
                                              is_medic, is_radio, role_updated_at, requested_at, updated_at)
            VALUES (:operation, :user, :status, :team, :squad, :role, :medic, :radio, :now, :now, :now)
            ON CONFLICT (operation_id, user_id) DO UPDATE SET status = EXCLUDED.status, operation_team_id = EXCLUDED.operation_team_id,
                operation_squad_id = EXCLUDED.operation_squad_id, primary_role = EXCLUDED.primary_role, is_medic = EXCLUDED.is_medic,
                is_radio = EXCLUDED.is_radio, role_updated_at = EXCLUDED.role_updated_at, requested_at = EXCLUDED.requested_at,
                updated_at = EXCLUDED.updated_at
              WHERE operation_participant.status IN ('CANCELLED','REJECTED')
            """, new MapSqlParameterSource().addValue("operation", operationId).addValue("user", userId).addValue("status", status)
                .addValue("team", teamId).addValue("squad", squadId).addValue("role", role).addValue("medic", medic)
                .addValue("radio", radio).addValue("now", Timestamp.from(now)));
    }

    public int updateRole(long participantId, String role, boolean medic, boolean radio, Instant now) {
        return jdbc.update("""
            UPDATE operation_participant SET primary_role = :role, is_medic = :medic, is_radio = :radio, role_updated_at = :now, updated_at = :now
            WHERE id = :id AND status IN
            """ + ACTIVE, new MapSqlParameterSource().addValue("id", participantId).addValue("role", role).addValue("medic", medic)
                .addValue("radio", radio).addValue("now", Timestamp.from(now)));
    }

    /** Cancelamento pelo proprio jogador: sai da lista, perde a equipe (e o chat), mantem o historico financeiro. */
    public int cancel(long participantId, Instant now) {
        return jdbc.update("""
            UPDATE operation_participant SET status = 'CANCELLED', operation_team_id = NULL, operation_squad_id = NULL, updated_at = :now
            WHERE id = :id AND status IN ('REQUESTED','APPROVED','WAITING_LIST','CONFIRMED')
            """, Map.of("id", participantId, "now", Timestamp.from(now)));
    }

    public int remove(long participantId, long organizer, String reason, Instant now) {
        return jdbc.update("""
            UPDATE operation_participant SET status = 'REMOVED', operation_team_id = NULL, operation_squad_id = NULL, removed_at = :now,
                   removed_by = :organizer, removal_reason = :reason, return_authorized_at = NULL, return_authorized_by = NULL, updated_at = :now
            WHERE id = :id AND status IN
            """ + ACTIVE, new MapSqlParameterSource().addValue("id", participantId).addValue("organizer", organizer)
                .addValue("reason", reason).addValue("now", Timestamp.from(now)));
    }

    /** Libera o retorno: a inscricao passa a "cancelada" e a pessoa pode se inscrever de novo enquanto houver inscricoes abertas. */
    public int authorizeReturn(long participantId, long organizer, Instant now) {
        return jdbc.update("""
            UPDATE operation_participant SET status = 'CANCELLED', return_authorized_at = :now, return_authorized_by = :organizer, updated_at = :now
            WHERE id = :id AND status = 'REMOVED'
            """, Map.of("id", participantId, "organizer", organizer, "now", Timestamp.from(now)));
    }

    public int setClosed(long operationId, Long by, Instant closedAt, Instant now) {
        return jdbc.update("""
            UPDATE airsoft_operation SET registration_closed_at = :closedAt, registration_closed_by = :by, updated_at = :now, version = version + 1
            WHERE id = :id
            """, new MapSqlParameterSource().addValue("id", operationId).addValue("by", by)
                .addValue("closedAt", closedAt == null ? null : Timestamp.from(closedAt)).addValue("now", Timestamp.from(now)));
    }

    public void setTeamChangeEnabled(long operationId, boolean enabled, Instant now) {
        jdbc.update("UPDATE airsoft_operation SET team_change_requests_enabled = :enabled, updated_at = :now WHERE id = :id",
            Map.of("id", operationId, "enabled", enabled, "now", Timestamp.from(now)));
    }

    public void event(long operationId, long participantUserId, String kind, Long actor, String reason, Long fromTeam, Long toTeam, Instant now) {
        jdbc.update("""
            INSERT INTO operation_participant_event(operation_id, participant_user_id, kind, actor_user_id, reason, from_team_id, to_team_id, created_at)
            VALUES (:operation, :user, :kind, :actor, :reason, :fromTeam, :toTeam, :now)
            """, new MapSqlParameterSource().addValue("operation", operationId).addValue("user", participantUserId).addValue("kind", kind)
                .addValue("actor", actor).addValue("reason", reason).addValue("fromTeam", fromTeam).addValue("toTeam", toTeam)
                .addValue("now", Timestamp.from(now)));
    }

    // ---- Troca de equipe ----

    public UUID requestChange(long operationId, long participantId, Long fromTeam, long toTeam, Instant now) {
        return jdbc.queryForObject("""
            INSERT INTO operation_team_change_request(operation_id, participant_id, from_team_id, to_team_id, created_at)
            VALUES (:operation, :participant, :from, :to, :now) RETURNING public_id
            """, new MapSqlParameterSource().addValue("operation", operationId).addValue("participant", participantId)
                .addValue("from", fromTeam).addValue("to", toTeam).addValue("now", Timestamp.from(now)), UUID.class);
    }

    public boolean hasPendingChange(long participantId) {
        return count("SELECT count(*) FROM operation_team_change_request WHERE participant_id = :id AND status = 'PENDING'", participantId) > 0;
    }

    public Optional<ChangeRow> change(long operationId, UUID requestId) {
        return jdbc.query("""
            SELECT c.id, c.participant_id, c.from_team_id, c.to_team_id, c.status, p.user_id
            FROM operation_team_change_request c JOIN operation_participant p ON p.id = c.participant_id
            WHERE c.operation_id = :operation AND c.public_id = :id FOR UPDATE OF c
            """, Map.of("operation", operationId, "id", requestId), (r, i) -> new ChangeRow(r.getLong("id"), r.getLong("participant_id"),
                (Long) r.getObject("from_team_id"), r.getLong("to_team_id"), r.getString("status"), r.getLong("user_id"))).stream().findFirst();
    }

    public int decideChange(long changeId, String status, Long by, Instant now) {
        return jdbc.update("""
            UPDATE operation_team_change_request SET status = :status, decided_at = :now, decided_by = :by WHERE id = :id AND status = 'PENDING'
            """, new MapSqlParameterSource().addValue("id", changeId).addValue("status", status).addValue("by", by).addValue("now", Timestamp.from(now)));
    }

    /** Pedidos pendentes ficam sem efeito quando a pessoa cancela, e removida ou ja foi movida. */
    public void cancelPendingChanges(long participantId, Instant now) {
        jdbc.update("""
            UPDATE operation_team_change_request SET status = 'CANCELLED', decided_at = :now WHERE participant_id = :id AND status = 'PENDING'
            """, Map.of("id", participantId, "now", Timestamp.from(now)));
    }

    /** Aplica a troca aprovada: so se o jogador ainda estiver ativo na equipe de origem. Sai do esquadrao antigo. */
    public int moveToTeam(long participantId, Long fromTeam, long toTeam, Instant now) {
        return jdbc.update("""
            UPDATE operation_participant SET operation_team_id = :to, operation_squad_id = NULL, updated_at = :now
            WHERE id = :id AND status IN
            """ + ACTIVE + " AND operation_team_id IS NOT DISTINCT FROM :from",
            new MapSqlParameterSource().addValue("id", participantId).addValue("from", fromTeam).addValue("to", toTeam)
                .addValue("now", Timestamp.from(now)));
    }

    // ---- Leitura ----

    public List<TeamInfo> teams(long operationId) {
        return jdbc.query("""
            SELECT t.public_id, t.name, t.acronym, t.color, t.capacity,
                   (SELECT count(*) FROM operation_participant p WHERE p.operation_team_id = t.id AND p.status IN
            """ + ACTIVE + """
            ) total FROM operation_team t WHERE t.operation_id = :id ORDER BY t.sort_order
            """, Map.of("id", operationId), (r, i) -> new TeamInfo(r.getObject("public_id", UUID.class), r.getString("name"),
                r.getString("acronym"), r.getString("color"), r.getInt("capacity") >= UNLIMITED ? null : r.getInt("capacity"), r.getLong("total")));
    }

    /** Inscritos ativos por ordem de inscricao, com funcao e equipe. */
    public List<Participant> participants(long operationId) {
        return jdbc.query("""
            SELECT prof.public_id, u.username, prof.callsign, prof.display_name, p.status, p.requested_at, p.primary_role, p.is_medic,
                   p.is_radio, ot.public_id team_id, ot.name team_name, ot.color team_color,
                   row_number() OVER (ORDER BY p.requested_at, p.id) position
            FROM operation_participant p
            JOIN app_user u ON u.id = p.user_id
            JOIN operator_profile prof ON prof.user_id = p.user_id
            LEFT JOIN operation_team ot ON ot.id = p.operation_team_id
            WHERE p.operation_id = :id AND p.status IN
            """ + ACTIVE + " ORDER BY p.requested_at, p.id", Map.of("id", operationId), (r, i) -> new Participant(
                r.getObject("public_id", UUID.class), r.getString("username"), r.getString("callsign"), r.getString("display_name"),
                r.getString("status"), instant(r, "requested_at"), r.getInt("position"), r.getString("primary_role"),
                r.getBoolean("is_medic"), r.getBoolean("is_radio"), r.getObject("team_id", UUID.class), r.getString("team_name"),
                r.getString("team_color")));
    }

    public Optional<OperationRegistrationDtos.PendingChange> pendingChange(long participantId) {
        return jdbc.query("""
            SELECT c.public_id, t.public_id team_id, t.name, c.created_at FROM operation_team_change_request c
            JOIN operation_team t ON t.id = c.to_team_id WHERE c.participant_id = :id AND c.status = 'PENDING'
            """, Map.of("id", participantId), (r, i) -> new OperationRegistrationDtos.PendingChange(r.getObject("public_id", UUID.class),
                r.getObject("team_id", UUID.class), r.getString("name"), instant(r, "created_at"))).stream().findFirst();
    }

    /** Pendentes primeiro, depois as 30 decisoes mais recentes. */
    public List<ChangeRequestItem> changeRequests(long operationId) {
        return jdbc.query("""
            SELECT c.public_id, prof.public_id operator_id, prof.callsign, ft.name from_name, tt.name to_name, c.created_at, c.status, c.decided_at
            FROM operation_team_change_request c
            JOIN operation_participant p ON p.id = c.participant_id
            JOIN operator_profile prof ON prof.user_id = p.user_id
            LEFT JOIN operation_team ft ON ft.id = c.from_team_id
            LEFT JOIN operation_team tt ON tt.id = c.to_team_id
            WHERE c.operation_id = :id
            ORDER BY (c.status = 'PENDING') DESC, COALESCE(c.decided_at, c.created_at) DESC LIMIT 40
            """, Map.of("id", operationId), (r, i) -> new ChangeRequestItem(r.getObject("public_id", UUID.class),
                r.getObject("operator_id", UUID.class), r.getString("callsign"), r.getString("from_name"), r.getString("to_name"),
                instant(r, "created_at"), r.getString("status"), instant(r, "decided_at")));
    }

    /** Removidos (ainda bloqueados) e liberados, com quem removeu e o motivo. */
    public List<RemovedItem> removed(long operationId) {
        return jdbc.query("""
            SELECT prof.public_id, prof.callsign, p.removal_reason, p.removed_at, byp.callsign removed_by, p.return_authorized_at
            FROM operation_participant p
            JOIN operator_profile prof ON prof.user_id = p.user_id
            LEFT JOIN operator_profile byp ON byp.user_id = p.removed_by
            WHERE p.operation_id = :id AND p.removed_at IS NOT NULL
            ORDER BY (p.status = 'REMOVED') DESC, p.removed_at DESC
            """, Map.of("id", operationId), (r, i) -> new RemovedItem(r.getObject("public_id", UUID.class), r.getString("callsign"),
                r.getString("removal_reason"), instant(r, "removed_at"), r.getString("removed_by"), instant(r, "return_authorized_at")));
    }

    private long count(String sql, long id) {
        Long value = jdbc.queryForObject(sql, Map.of("id", id), Long.class);
        return value == null ? 0 : value;
    }

    private static Op op(ResultSet r) throws SQLException {
        return new Op(r.getLong("id"), r.getObject("public_id", UUID.class), r.getLong("organizer_user_id"), r.getString("name"),
            r.getString("registration_model"), r.getString("status"), instant(r, "registration_closed_at"), instant(r, "starts_at"),
            r.getBoolean("approval_required"), r.getBoolean("waiting_list_enabled"), (Integer) r.getObject("participant_limit"),
            r.getBoolean("team_change_requests_enabled"));
    }

    private static Instant instant(ResultSet r, String column) throws SQLException {
        Timestamp value = r.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
