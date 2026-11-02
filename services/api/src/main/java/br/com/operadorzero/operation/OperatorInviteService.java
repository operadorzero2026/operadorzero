package br.com.operadorzero.operation;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.notification.Notifier;
import br.com.operadorzero.operation.OperatorInviteRepository.InvitableOperation;
import br.com.operadorzero.operation.OperatorInviteRepository.ManagedTeam;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.ratelimit.UserActionLimiter;
import br.com.operadorzero.shared.web.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acoes a partir de um operador encontrado na busca. Convites so entre amigos (amizade aceita), para evitar spam:
 * para a equipe (capitao/gestor, usando o convite de equipe que ja existe) e para jogos que a pessoa organiza ou joga.
 */
@Service
public class OperatorInviteService {
    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    static final int DAILY_GAME_INVITES = 30;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM", Locale.forLanguageTag("pt-BR"));

    public record TeamOption(UUID id, String name, boolean canInvite, String reason) {}
    public record InviteOptions(UUID operatorId, String username, boolean friends, TeamOption team, List<InvitableOperation> operations) {}
    public record InviteToGameRequest(@jakarta.validation.constraints.NotBlank String username) {}

    private final OperatorInviteRepository repository;
    private final Notifier notifier;
    private final UserActionLimiter limiter;
    private final AuditEventRepository audit;
    private final Clock clock;

    @Autowired
    public OperatorInviteService(OperatorInviteRepository repository, Notifier notifier, UserActionLimiter limiter, AuditEventRepository audit) {
        this(repository, notifier, limiter, audit, Clock.systemUTC());
    }

    OperatorInviteService(OperatorInviteRepository repository, Notifier notifier, UserActionLimiter limiter, AuditEventRepository audit, Clock clock) {
        this.repository = repository;
        this.notifier = notifier;
        this.limiter = limiter;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InviteOptions options(AuthenticatedUser user, String username) {
        var target = repository.target(username, user.internalId()).orElseThrow(() -> BusinessException.notFound("Operador não encontrado."));
        boolean friends = repository.friends(user.internalId(), target.userId());
        TeamOption team = repository.managedTeam(user.internalId()).map(managed -> teamOption(managed, friends, target.hasTeam())).orElse(null);
        List<InvitableOperation> operations = friends ? repository.invitableOperations(user.internalId(), target.userId(), today()) : List.of();
        return new InviteOptions(target.operatorId(), target.username(), friends, team, operations);
    }

    @Transactional
    public void inviteToGame(AuthenticatedUser user, UUID operationId, String username) {
        var target = repository.target(username, user.internalId()).orElseThrow(() -> BusinessException.notFound("Operador não encontrado."));
        if (!repository.friends(user.internalId(), target.userId())) {
            throw BusinessException.forbidden("Convide para jogos só quem já é seu amigo.");
        }
        // Le o jogo antes de gravar o convite: depois dele, o jogo deixa de aparecer como convidavel.
        InvitableOperation game = repository.invitableOperations(user.internalId(), target.userId(), today()).stream()
            .filter(op -> op.id().equals(operationId)).findFirst()
            .orElseThrow(() -> BusinessException.conflict("GAME_INVITE_UNAVAILABLE",
                "Este jogo não aceita mais o convite (inscrição fechada, amigo já inscrito ou já convidado)."));
        limiter.check("game-invite", user.internalId(), DAILY_GAME_INVITES, Duration.ofDays(1));
        Instant now = clock.instant();
        if (!repository.insertInvitation(repository.operationId(operationId), user.internalId(), target.userId(), now)) {
            throw BusinessException.conflict("GAME_INVITE_UNAVAILABLE", "Este amigo já foi convidado para este jogo.");
        }
        notifier.toUserAt(target.userId(), "GAME_INVITATION", repository.callsign(user.internalId()) + " convidou você para " + game.name(),
            "Jogo em " + DAY.format(game.date()) + " · " + game.fieldName() + ". Toque para ver a convocação e se alistar.", "Operações", "/convocacao/" + operationId);
        audit.record(user.internalId(), "OPERATION_INVITATION_SENT", "OPERATION", operationId, null, now);
    }

    private static TeamOption teamOption(ManagedTeam team, boolean friends, boolean targetHasTeam) {
        if (!friends) return new TeamOption(team.id(), team.name(), false, "Adicione como amigo para convidar.");
        if (targetHasTeam) return new TeamOption(team.id(), team.name(), false, "Este operador já participa de uma equipe.");
        return new TeamOption(team.id(), team.name(), true, null);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(BRASILIA));
    }
}
