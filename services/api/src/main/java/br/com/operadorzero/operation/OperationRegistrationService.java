package br.com.operadorzero.operation;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.notification.Notifier;
import br.com.operadorzero.operation.OperationDtos.MessageResponse;
import br.com.operadorzero.operation.OperationRegistrationDtos.Counts;
import br.com.operadorzero.operation.OperationRegistrationDtos.Overview;
import br.com.operadorzero.operation.OperationRegistrationDtos.Participant;
import br.com.operadorzero.operation.OperationRegistrationDtos.RegisterRequest;
import br.com.operadorzero.operation.OperationRegistrationDtos.RoleRequest;
import br.com.operadorzero.operation.OperationRegistrationRepository.Op;
import br.com.operadorzero.operation.OperationRegistrationRepository.TeamRow;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.web.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de inscricao validadas no servidor: lista unica ou por equipes, funcao principal (Assalto, Sniper, DMR) mais
 * Medico e Operador de Radio opcionais, encerramento no horario de inicio, cancelamento, remocao com motivo e trocas de equipe
 * so com aprovacao do organizador.
 */
@Service
public class OperationRegistrationService {
    static final Set<String> PRIMARY_ROLES = Set.of("ASSAULT", "SNIPER", "DMR");
    /** Status da operacao em que se aceita inscricao (alem do horario e do encerramento manual). */
    static final Set<String> REGISTRATION_STATUSES = Set.of("PUBLISHED", "REGISTRATION_OPEN", "FULL");
    /** Antes do inicio: da para cancelar a propria inscricao mesmo com as inscricoes encerradas pelo organizador. */
    static final Set<String> BEFORE_START = Set.of("PUBLISHED", "REGISTRATION_OPEN", "FULL", "CONFIRMATION_PENDING");
    static final Set<String> ACTIVE = Set.of("REQUESTED", "APPROVED", "WAITING_LIST", "CONFIRMED", "CHECKED_IN");

    private final OperationRegistrationRepository repository;
    private final OperationRepository operations;
    private final AuditEventRepository audit;
    private final Notifier notifier;
    private final Clock clock;

    @Autowired
    public OperationRegistrationService(OperationRegistrationRepository repository, OperationRepository operations,
                                        AuditEventRepository audit, Notifier notifier) {
        this(repository, operations, audit, notifier, Clock.systemUTC());
    }

    OperationRegistrationService(OperationRegistrationRepository repository, OperationRepository operations, AuditEventRepository audit,
                                 Notifier notifier, Clock clock) {
        this.repository = repository;
        this.operations = operations;
        this.audit = audit;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Overview overview(AuthenticatedUser user, UUID operationId) {
        Op op = operation(user, operationId, false);
        Instant now = clock.instant();
        boolean managed = manages(op, user);
        List<Participant> participants = repository.participants(op.id());
        var mineRow = repository.mine(op.id(), user.internalId(), false);
        OperationRegistrationDtos.Mine mine = mineRow.map(m -> new OperationRegistrationDtos.Mine(m.status(), m.primaryRole(), m.medic(),
            m.radio(), m.teamPublicId(), m.teamName(), "REMOVED".equals(m.status()) ? m.removalReason() : null,
            "REMOVED".equals(m.status()) ? m.removedAt() : null,
            ACTIVE.contains(m.status()) ? repository.pendingChange(m.id()).orElse(null) : null)).orElse(null);
        return new Overview(op.model(), open(op, now), closedReason(op, now), op.closedAt(), op.startsAt(),
            BEFORE_START.contains(op.status()) && op.startsAt().isAfter(now), op.teamChangeEnabled(), managed,
            "TEAMS".equals(op.model()) ? repository.teams(op.id()) : List.of(), participants, counts(participants), mine,
            managed ? repository.changeRequests(op.id()) : List.of(), managed ? repository.removed(op.id()) : List.of());
    }

    @Transactional
    public MessageResponse register(AuthenticatedUser user, UUID operationId, RegisterRequest request) {
        Op op = operation(user, operationId, true);
        Instant now = clock.instant();
        if (!open(op, now)) throw BusinessException.conflict("REGISTRATION_CLOSED", "Inscrições encerradas.");
        String role = role(request.primaryRole());
        var existing = repository.mine(op.id(), user.internalId(), true);
        if (existing.isPresent()) {
            String status = existing.get().status();
            if ("REMOVED".equals(status)) {
                throw BusinessException.forbidden("Você foi removido desta operação. Para participar novamente, entre em contato com o organizador.");
            }
            if (ACTIVE.contains(status)) throw BusinessException.conflict("ALREADY_REGISTERED", "Você já está inscrito nesta operação.");
        }
        Long teamId = null;
        Long squadId = null;
        boolean full = op.participantLimit() != null && repository.occupiedInOperation(op.id()) >= op.participantLimit();
        if ("TEAMS".equals(op.model())) {
            TeamRow team = repository.team(op.id(), request.operationTeamId())
                .filter(t -> "OPEN".equals(t.status()))
                .orElseThrow(() -> BusinessException.badRequest("TEAM_REQUIRED", "Escolha uma equipe aberta para se inscrever."));
            teamId = team.id();
            full = full || repository.occupiedInTeam(team.id()) >= team.capacity();
            // Estrutura com esquadroes (operacoes antigas): o esquadrao continua obrigatorio quando a equipe tem esquadroes.
            if (team.hasSquads()) {
                long[] squad = repository.squad(team.id(), request.operationSquadId())
                    .orElseThrow(() -> BusinessException.badRequest("SQUAD_REQUIRED", "Escolha um esquadrão aberto desta equipe."));
                squadId = squad[0];
                full = full || squad[2] >= squad[1];
            }
        } else if (request.operationTeamId() != null) {
            throw BusinessException.badRequest("SINGLE_LIST", "Esta operação usa lista única: não é preciso escolher equipe.");
        }
        if (full && !op.waitingListEnabled()) throw BusinessException.conflict("OPERATION_FULL", "Não há vagas disponíveis.");
        String status = full ? "WAITING_LIST" : op.organizerUserId() == user.internalId() || !op.approvalRequired() ? "APPROVED" : "REQUESTED";
        if (repository.register(op.id(), user.internalId(), status, teamId, squadId, role, request.medic(), request.radio(), now) != 1) {
            throw BusinessException.conflict("ALREADY_REGISTERED", "Você já está inscrito nesta operação.");
        }
        repository.event(op.id(), user.internalId(), "REGISTERED", user.internalId(), null, null, teamId, now);
        audit.record(user.internalId(), "OPERATION_PARTICIPATION_REQUESTED", "OPERATION", operationId, status, now);
        return new MessageResponse(switch (status) {
            case "WAITING_LIST" -> "Você entrou na lista de espera.";
            case "REQUESTED" -> "Inscrição enviada. Aguarde a aprovação do organizador.";
            default -> "Inscrição confirmada.";
        });
    }

    /** O jogador muda a propria funcao quando quiser, sem aprovacao, enquanto a operacao nao terminou. */
    @Transactional
    public MessageResponse updateRole(AuthenticatedUser user, UUID operationId, RoleRequest request) {
        Op op = operation(user, operationId, false);
        if (Set.of("FINISHED", "CANCELLED").contains(op.status())) {
            throw BusinessException.conflict("OPERATION_ENDED", "A operação já terminou.");
        }
        String role = role(request.primaryRole());
        var mine = repository.mine(op.id(), user.internalId(), true).filter(m -> ACTIVE.contains(m.status()))
            .orElseThrow(() -> BusinessException.notFound("Você não está inscrito nesta operação."));
        Instant now = clock.instant();
        repository.updateRole(mine.id(), role, request.medic(), request.radio(), now);
        repository.event(op.id(), user.internalId(), "ROLE_CHANGED", user.internalId(),
            role + (request.medic() ? "+MEDIC" : "") + (request.radio() ? "+RADIO" : ""), null, null, now);
        return new MessageResponse("Função atualizada.");
    }

    @Transactional
    public MessageResponse cancel(AuthenticatedUser user, UUID operationId) {
        Op op = operation(user, operationId, true);
        Instant now = clock.instant();
        if (!BEFORE_START.contains(op.status()) || !op.startsAt().isAfter(now)) {
            throw BusinessException.conflict("PARTICIPATION_NOT_CANCELLABLE", "O jogo já começou: a inscrição não pode mais ser cancelada.");
        }
        var mine = repository.mine(op.id(), user.internalId(), true)
            .orElseThrow(() -> BusinessException.conflict("PARTICIPATION_NOT_CANCELLABLE", "Você não está inscrito nesta operação."));
        if (repository.cancel(mine.id(), now) != 1) {
            throw BusinessException.conflict("PARTICIPATION_NOT_CANCELLABLE", "Esta inscrição não pode ser cancelada.");
        }
        repository.cancelPendingChanges(mine.id(), now);
        repository.event(op.id(), user.internalId(), "CANCELLED", user.internalId(), null, mine.teamId(), null, now);
        audit.record(user.internalId(), "OPERATION_PARTICIPATION_CANCELLED", "OPERATION", operationId, null, now);
        operations.promoteWaitingList(op.id(), now);
        return new MessageResponse("Inscrição cancelada.");
    }

    /** Remocao pelo organizador, sempre com motivo; o jogador e avisado e so volta depois de "Autorizar retorno". */
    @Transactional
    public MessageResponse remove(AuthenticatedUser user, UUID operationId, UUID operatorId, String reason) {
        Op op = managed(user, operationId);
        Instant now = clock.instant();
        String why = reason == null ? "" : reason.strip();
        if (why.length() < 3) throw BusinessException.badRequest("REMOVAL_REASON_REQUIRED", "Informe o motivo da remoção.");
        var target = repository.target(op.id(), operatorId).filter(t -> ACTIVE.contains(t.status()))
            .orElseThrow(() -> BusinessException.notFound("Jogador não encontrado na lista ativa."));
        if (target.userId() == op.organizerUserId()) throw BusinessException.badRequest("CANNOT_REMOVE_ORGANIZER", "O organizador não pode ser removido.");
        repository.remove(target.participantId(), user.internalId(), why, now);
        repository.cancelPendingChanges(target.participantId(), now);
        repository.event(op.id(), target.userId(), "REMOVED", user.internalId(), why, target.teamId(), null, now);
        audit.record(user.internalId(), "OPERATION_PARTICIPANT_REMOVED", "OPERATION", operationId, operatorId + ": " + why, now);
        notifier.toUser(target.userId(), "OPERATION_REMOVED", "Você foi removido da operação " + op.name(), "Motivo: " + why, "Operações");
        operations.promoteWaitingList(op.id(), now);
        return new MessageResponse("Jogador removido.");
    }

    @Transactional
    public MessageResponse authorizeReturn(AuthenticatedUser user, UUID operationId, UUID operatorId) {
        Op op = managed(user, operationId);
        Instant now = clock.instant();
        var target = repository.target(op.id(), operatorId).filter(t -> "REMOVED".equals(t.status()))
            .orElseThrow(() -> BusinessException.notFound("Não há remoção pendente para este jogador."));
        repository.authorizeReturn(target.participantId(), user.internalId(), now);
        repository.event(op.id(), target.userId(), "RETURN_AUTHORIZED", user.internalId(), null, null, null, now);
        audit.record(user.internalId(), "OPERATION_PARTICIPANT_RETURN_AUTHORIZED", "OPERATION", operationId, operatorId.toString(), now);
        notifier.toUser(target.userId(), "OPERATION_RETURN_AUTHORIZED", "Seu retorno à operação " + op.name() + " foi autorizado",
            "Você pode se inscrever novamente enquanto as inscrições estiverem abertas.", "Operações");
        return new MessageResponse("Retorno autorizado.");
    }

    @Transactional
    public MessageResponse close(AuthenticatedUser user, UUID operationId) {
        Op op = managed(user, operationId);
        Instant now = clock.instant();
        if (!open(op, now)) throw BusinessException.conflict("REGISTRATION_ALREADY_CLOSED", "As inscrições já estão encerradas.");
        repository.setClosed(op.id(), user.internalId(), now, now);
        repository.event(op.id(), user.internalId(), "REGISTRATION_CLOSED", user.internalId(), null, null, null, now);
        audit.record(user.internalId(), "OPERATION_REGISTRATION_CLOSED", "OPERATION", operationId, null, now);
        return new MessageResponse("Inscrições encerradas.");
    }

    /** Reabre so o que foi encerrado manualmente, e somente antes do horario de inicio. */
    @Transactional
    public MessageResponse reopen(AuthenticatedUser user, UUID operationId) {
        Op op = managed(user, operationId);
        Instant now = clock.instant();
        if (op.closedAt() == null) throw BusinessException.conflict("REGISTRATION_NOT_CLOSED", "As inscrições não foram encerradas manualmente.");
        if (!op.startsAt().isAfter(now) || !REGISTRATION_STATUSES.contains(op.status())) {
            throw BusinessException.conflict("REGISTRATION_REOPEN_TOO_LATE", "Não é possível reabrir depois do início do jogo.");
        }
        repository.setClosed(op.id(), null, null, now);
        repository.event(op.id(), user.internalId(), "REGISTRATION_REOPENED", user.internalId(), null, null, null, now);
        audit.record(user.internalId(), "OPERATION_REGISTRATION_REOPENED", "OPERATION", operationId, null, now);
        return new MessageResponse("Inscrições reabertas.");
    }

    @Transactional
    public MessageResponse setTeamChangeEnabled(AuthenticatedUser user, UUID operationId, boolean enabled) {
        Op op = managed(user, operationId);
        repository.setTeamChangeEnabled(op.id(), enabled, clock.instant());
        audit.record(user.internalId(), "OPERATION_TEAM_CHANGE_SETTING", "OPERATION", operationId, String.valueOf(enabled), clock.instant());
        return new MessageResponse(enabled ? "Pedidos de troca de equipe liberados." : "Novos pedidos de troca de equipe desativados.");
    }

    /** O jogador pede para trocar de equipe; continua na atual ate o organizador decidir. */
    @Transactional
    public MessageResponse requestTeamChange(AuthenticatedUser user, UUID operationId, UUID toTeamId) {
        Op op = operation(user, operationId, true);
        if (!"TEAMS".equals(op.model())) throw BusinessException.badRequest("SINGLE_LIST", "Esta operação usa lista única, sem equipes.");
        if (!op.teamChangeEnabled()) throw BusinessException.conflict("TEAM_CHANGE_DISABLED", "O organizador não está aceitando pedidos de troca agora.");
        if (Set.of("IN_PROGRESS", "FINISHED", "CANCELLED").contains(op.status())) {
            throw BusinessException.conflict("TEAM_CHANGE_CLOSED", "Não é mais possível pedir troca de equipe.");
        }
        var mine = repository.mine(op.id(), user.internalId(), true).filter(m -> ACTIVE.contains(m.status()))
            .orElseThrow(() -> BusinessException.notFound("Você não está inscrito nesta operação."));
        TeamRow target = repository.team(op.id(), toTeamId).filter(t -> "OPEN".equals(t.status()))
            .orElseThrow(() -> BusinessException.badRequest("TEAM_NOT_FOUND", "Escolha uma equipe aberta."));
        if (mine.teamId() != null && mine.teamId() == target.id()) throw BusinessException.badRequest("SAME_TEAM", "Você já está nesta equipe.");
        if (repository.hasPendingChange(mine.id())) throw BusinessException.conflict("TEAM_CHANGE_PENDING", "Você já tem um pedido aguardando o organizador.");
        repository.requestChange(op.id(), mine.id(), mine.teamId(), target.id(), clock.instant());
        return new MessageResponse("Solicitação aguardando aprovação do organizador.");
    }

    @Transactional
    public MessageResponse cancelTeamChange(AuthenticatedUser user, UUID operationId) {
        Op op = operation(user, operationId, true);
        var mine = repository.mine(op.id(), user.internalId(), true)
            .orElseThrow(() -> BusinessException.notFound("Você não está inscrito nesta operação."));
        repository.cancelPendingChanges(mine.id(), clock.instant());
        return new MessageResponse("Pedido de troca cancelado.");
    }

    @Transactional
    public MessageResponse decideTeamChange(AuthenticatedUser user, UUID operationId, UUID requestId, String action) {
        Op op = managed(user, operationId);
        Instant now = clock.instant();
        String decision = action == null ? "" : action.strip().toUpperCase(Locale.ROOT);
        if (!Set.of("APPROVE", "REJECT").contains(decision)) throw BusinessException.badRequest("INVALID_ACTION", "Ação inválida.");
        var change = repository.change(op.id(), requestId).filter(c -> "PENDING".equals(c.status()))
            .orElseThrow(() -> BusinessException.notFound("Pedido não encontrado ou já decidido."));
        if ("REJECT".equals(decision)) {
            repository.decideChange(change.id(), "REJECTED", user.internalId(), now);
            audit.record(user.internalId(), "OPERATION_TEAM_CHANGE_REJECTED", "OPERATION", operationId, requestId.toString(), now);
            return new MessageResponse("Pedido recusado. O jogador continua na equipe atual.");
        }
        if (repository.moveToTeam(change.participantId(), change.fromTeamId(), change.toTeamId(), now) != 1) {
            repository.decideChange(change.id(), "CANCELLED", user.internalId(), now);
            throw BusinessException.conflict("TEAM_CHANGE_STALE", "A inscrição mudou desde o pedido; ele foi descartado.");
        }
        repository.decideChange(change.id(), "APPROVED", user.internalId(), now);
        repository.event(op.id(), change.userId(), "TEAM_CHANGED", user.internalId(), null, change.fromTeamId(), change.toTeamId(), now);
        audit.record(user.internalId(), "OPERATION_TEAM_CHANGE_APPROVED", "OPERATION", operationId, requestId.toString(), now);
        return new MessageResponse("Troca aprovada. As listas foram atualizadas.");
    }

    static Counts counts(List<Participant> participants) {
        long assault = 0, sniper = 0, dmr = 0, medic = 0, radio = 0, none = 0;
        for (Participant p : participants) {
            if ("ASSAULT".equals(p.primaryRole())) assault++;
            else if ("SNIPER".equals(p.primaryRole())) sniper++;
            else if ("DMR".equals(p.primaryRole())) dmr++;
            else none++;
            if (p.medic()) medic++;
            if (p.radio()) radio++;
        }
        return new Counts(participants.size(), assault, sniper, dmr, medic, radio, none);
    }

    static boolean open(Op op, Instant now) {
        return REGISTRATION_STATUSES.contains(op.status()) && op.closedAt() == null && op.startsAt().isAfter(now);
    }

    private static String closedReason(Op op, Instant now) {
        if (open(op, now)) return null;
        if (!op.startsAt().isAfter(now)) return "STARTED";
        if (op.closedAt() != null) return "MANUAL";
        return "STATUS";
    }

    private static String role(String value) {
        String role = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        if (!PRIMARY_ROLES.contains(role)) throw BusinessException.badRequest("PRIMARY_ROLE_REQUIRED", "Escolha a função principal: Assalto, Sniper ou DMR.");
        return role;
    }

    private Op operation(AuthenticatedUser user, UUID operationId, boolean lock) {
        return repository.operation(operationId, user.internalId(), lock).orElseThrow(() -> BusinessException.notFound("Operação não encontrada."));
    }

    private Op managed(AuthenticatedUser user, UUID operationId) {
        Op op = operation(user, operationId, true);
        if (!manages(op, user)) throw BusinessException.forbidden("Só o organizador pode fazer isso.");
        return op;
    }

    private boolean manages(Op op, AuthenticatedUser user) {
        return op.organizerUserId() == user.internalId() || operations.isOperationAdmin(op.id(), user.internalId());
    }
}
