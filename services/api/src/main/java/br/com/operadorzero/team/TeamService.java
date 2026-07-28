package br.com.operadorzero.team;

import br.com.operadorzero.identity.AuthRateLimiter;
import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operator.BrazilLocationValidator;
import br.com.operadorzero.operator.BrazilLocationValidator.Location;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.web.BusinessException;
import br.com.operadorzero.team.TeamDtos.CreateTeamRequest;
import br.com.operadorzero.team.TeamDtos.InvitationResponse;
import br.com.operadorzero.team.TeamDtos.InviteRequest;
import br.com.operadorzero.team.TeamDtos.MessageResponse;
import br.com.operadorzero.team.TeamDtos.TeamResponse;
import br.com.operadorzero.team.TeamDtos.TeamWorkspaceResponse;
import br.com.operadorzero.team.TeamDtos.TransferCaptaincyRequest;
import br.com.operadorzero.team.TeamDtos.UpdateTeamRequest;
import br.com.operadorzero.team.TeamRepository.InvitationRow;
import br.com.operadorzero.team.TeamRepository.MembershipRow;
import br.com.operadorzero.team.TeamRepository.TeamRow;
import br.com.operadorzero.team.TeamRepository.UserRow;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamService {
    private static final Set<String> RECRUITMENT = Set.of("OPEN", "INVITE_ONLY", "CLOSED");
    private static final Set<String> INVITABLE_ROLES = Set.of("MANAGER", "MEMBER");
    private static final Set<String> MANAGEMENT_ROLES = Set.of("CAPTAIN", "MANAGER");

    private final TeamRepository repository;
    private final BrazilLocationValidator locations;
    private final AuditEventRepository audit;
    private final AuthRateLimiter rateLimiter;
    private final Clock clock;

    @Autowired
    public TeamService(TeamRepository repository, BrazilLocationValidator locations,
                       AuditEventRepository audit, AuthRateLimiter rateLimiter) {
        this(repository, locations, audit, rateLimiter, Clock.systemUTC());
    }

    TeamService(TeamRepository repository, BrazilLocationValidator locations,
                AuditEventRepository audit, AuthRateLimiter rateLimiter, Clock clock) {
        this.repository = repository;
        this.locations = locations;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TeamWorkspaceResponse workspace(AuthenticatedUser user) {
        TeamResponse team = repository.activeMembership(user.internalId())
            .map(this::response)
            .orElse(null);
        List<InvitationResponse> invitations = repository.receivedInvitations(user.internalId(), clock.instant());
        return new TeamWorkspaceResponse(team, invitations);
    }

    @Transactional
    public TeamResponse create(AuthenticatedUser user, CreateTeamRequest request) {
        if (repository.activeMembership(user.internalId()).isPresent()) {
            throw BusinessException.conflict("ACTIVE_TEAM_EXISTS", "Você já participa de uma equipe ativa.");
        }
        Location location = locations.validate(request.stateCode(), request.city());
        String recruitment = recruitment(request.recruitmentStatus());
        Instant now = clock.instant();
        try {
            long teamId = repository.createTeam(user.internalId(), normalize(request.name()), upper(request.acronym()),
                location.city(), location.stateCode(), normalize(request.gameStyle()), request.ownsField(),
                nullable(request.description()), recruitment, now);
            repository.addMember(teamId, user.internalId(), "CAPTAIN", null, now);
            repository.history(teamId, user.internalId(), "TEAM_CREATED", null, "CAPTAIN", user.internalId(), null, now);
            MembershipRow membership = repository.activeMembership(user.internalId())
                .orElseThrow(() -> BusinessException.conflict("TEAM_CREATION_FAILED", "Não foi possível concluir a criação da equipe."));
            audit.record(user.internalId(), "TEAM_CREATED", "TEAM", membership.teamPublicId(), null, now);
            return response(membership);
        } catch (DataIntegrityViolationException exception) {
            throw BusinessException.conflict("TEAM_CONFLICT", "Já existe uma equipe ativa com este nome ou seu vínculo mudou.");
        }
    }

    @Transactional
    public TeamResponse update(AuthenticatedUser user, UUID teamId, UpdateTeamRequest request) {
        MembershipRow membership = managedMembership(user.internalId(), teamId);
        Location location = locations.validate(request.stateCode(), request.city());
        String recruitment = recruitment(request.recruitmentStatus());
        try {
            int updated = repository.updateTeam(membership.teamId(), request.version(), normalize(request.name()),
                upper(request.acronym()), location.city(), location.stateCode(), normalize(request.gameStyle()),
                request.ownsField(), nullable(request.description()), recruitment, clock.instant());
            if (updated != 1) {
                throw BusinessException.conflict("TEAM_CHANGED", "A equipe foi atualizada em outro acesso. Recarregue e tente novamente.");
            }
        } catch (DataIntegrityViolationException exception) {
            throw BusinessException.conflict("TEAM_NAME_UNAVAILABLE", "Este nome de equipe não está disponível.");
        }
        audit.record(user.internalId(), "TEAM_UPDATED", "TEAM", teamId, null, clock.instant());
        return response(membership);
    }

    @Transactional
    public MessageResponse invite(AuthenticatedUser user, UUID teamId, InviteRequest request,
                                  HttpServletRequest servletRequest) {
        MembershipRow membership = managedMembership(user.internalId(), teamId);
        String proposedRole = upper(request.proposedRole());
        if (!INVITABLE_ROLES.contains(proposedRole)) {
            throw BusinessException.badRequest("INVALID_TEAM_ROLE", "Selecione uma função válida para o convite.");
        }
        if ("MANAGER".equals(membership.role()) && "MANAGER".equals(proposedRole)) {
            throw BusinessException.forbidden("Somente o capitão pode convidar outro gestor.");
        }
        UserRow target = repository.findActiveUser(request.operatorId())
            .orElseThrow(() -> BusinessException.notFound("Operador não encontrado."));
        if (target.id() == user.internalId()) {
            throw BusinessException.badRequest("SELF_INVITATION", "Você não pode convidar a si mesmo.");
        }
        if (repository.activeMembership(target.id()).isPresent()) {
            throw BusinessException.conflict("OPERATOR_HAS_TEAM", "Este operador já participa de uma equipe.");
        }
        rateLimiter.check("team-invitation", servletRequest, Long.toString(user.internalId()), 20, Duration.ofDays(1));
        Instant now = clock.instant();
        try {
            UUID invitationId = repository.createInvitation(membership.teamId(), target.id(), user.internalId(),
                proposedRole, nullable(request.message()), now, now.plus(Duration.ofDays(15)));
            audit.record(user.internalId(), "TEAM_INVITATION_CREATED", "TEAM_INVITATION", invitationId, null, now);
            return new MessageResponse("Convite enviado.");
        } catch (DataIntegrityViolationException exception) {
            throw BusinessException.conflict("INVITATION_ALREADY_PENDING", "Já existe um convite pendente para este operador.");
        }
    }

    @Transactional
    public TeamResponse acceptInvitation(AuthenticatedUser user, UUID invitationId) {
        InvitationRow invitation = invitation(invitationId, user.internalId());
        Instant now = clock.instant();
        if (invitation.expiresAt().isBefore(now)) {
            repository.respondInvitation(invitation.id(), "EXPIRED", now);
            throw BusinessException.conflict("INVITATION_EXPIRED", "Este convite expirou.");
        }
        if (repository.activeMembership(user.internalId()).isPresent()) {
            throw BusinessException.conflict("ACTIVE_TEAM_EXISTS", "Você já participa de uma equipe ativa.");
        }
        try {
            repository.addMember(invitation.teamId(), user.internalId(), invitation.proposedRole(), invitation.inviterUserId(), now);
            repository.respondInvitation(invitation.id(), "ACCEPTED", now);
            repository.cancelOtherInvitations(user.internalId(), invitation.id(), now);
            repository.history(invitation.teamId(), user.internalId(), "JOINED", null, invitation.proposedRole(),
                user.internalId(), "Convite aceito", now);
        } catch (DataIntegrityViolationException exception) {
            throw BusinessException.conflict("MEMBERSHIP_CONFLICT", "Seu vínculo de equipe mudou. Atualize a página.");
        }
        audit.record(user.internalId(), "TEAM_INVITATION_ACCEPTED", "TEAM_INVITATION", invitation.publicId(), null, now);
        MembershipRow membership = repository.activeMembership(user.internalId())
            .orElseThrow(() -> BusinessException.conflict("MEMBERSHIP_CONFLICT", "Não foi possível carregar seu novo vínculo."));
        return response(membership);
    }

    @Transactional
    public MessageResponse declineInvitation(AuthenticatedUser user, UUID invitationId) {
        InvitationRow invitation = invitation(invitationId, user.internalId());
        repository.respondInvitation(invitation.id(), "DECLINED", clock.instant());
        audit.record(user.internalId(), "TEAM_INVITATION_DECLINED", "TEAM_INVITATION", invitation.publicId(), null, clock.instant());
        return new MessageResponse("Convite recusado.");
    }

    @Transactional
    public MessageResponse leave(AuthenticatedUser user) {
        MembershipRow membership = repository.activeMembership(user.internalId())
            .orElseThrow(() -> BusinessException.notFound("Você não participa de uma equipe ativa."));
        if ("CAPTAIN".equals(membership.role())) {
            throw BusinessException.conflict("CAPTAIN_TRANSFER_REQUIRED", "Transfira a capitania antes de sair da equipe.");
        }
        Instant now = clock.instant();
        repository.leave(membership.teamId(), user.internalId(), now);
        repository.history(membership.teamId(), user.internalId(), "LEFT", membership.role(), null,
            user.internalId(), "Saída voluntária", now);
        audit.record(user.internalId(), "TEAM_LEFT", "TEAM", membership.teamPublicId(), null, now);
        return new MessageResponse("Você saiu da equipe.");
    }

    @Transactional
    public TeamResponse transferCaptaincy(AuthenticatedUser user, UUID teamId, TransferCaptaincyRequest request) {
        MembershipRow actor = repository.activeMembership(user.internalId(), teamId)
            .orElseThrow(() -> BusinessException.notFound("Equipe não encontrada."));
        if (!"CAPTAIN".equals(actor.role())) {
            throw BusinessException.forbidden("Somente o capitão pode transferir a capitania.");
        }
        MembershipRow target = repository.memberByOperator(actor.teamId(), request.operatorId())
            .orElseThrow(() -> BusinessException.notFound("Integrante não encontrado nesta equipe."));
        if (target.userId() == user.internalId()) {
            throw BusinessException.badRequest("INVALID_CAPTAIN", "Selecione outro integrante.");
        }
        Instant now = clock.instant();
        repository.transferCaptaincy(actor.teamId(), user.internalId(), target.userId(), now);
        repository.history(actor.teamId(), user.internalId(), "CAPTAINCY_TRANSFERRED", "CAPTAIN", "MANAGER",
            user.internalId(), nullable(request.reason()), now);
        repository.history(actor.teamId(), target.userId(), "CAPTAINCY_TRANSFERRED", target.role(), "CAPTAIN",
            user.internalId(), nullable(request.reason()), now);
        audit.record(user.internalId(), "TEAM_CAPTAINCY_TRANSFERRED", "TEAM", teamId, nullable(request.reason()), now);
        MembershipRow refreshed = repository.activeMembership(user.internalId(), teamId)
            .orElseThrow(() -> BusinessException.notFound("Equipe não encontrada."));
        return response(refreshed);
    }

    private InvitationRow invitation(UUID invitationId, long userId) {
        InvitationRow invitation = repository.lockInvitation(invitationId)
            .orElseThrow(() -> BusinessException.notFound("Convite não encontrado."));
        if (invitation.inviteeUserId() != userId) {
            throw BusinessException.forbidden("Este convite pertence a outro operador.");
        }
        if (!"PENDING".equals(invitation.status())) {
            throw BusinessException.conflict("INVITATION_NOT_PENDING", "Este convite já foi respondido.");
        }
        return invitation;
    }

    private MembershipRow managedMembership(long userId, UUID teamId) {
        MembershipRow membership = repository.activeMembership(userId, teamId)
            .orElseThrow(() -> BusinessException.notFound("Equipe não encontrada."));
        if (!MANAGEMENT_ROLES.contains(membership.role())) {
            throw BusinessException.forbidden("Você não possui permissão para administrar esta equipe.");
        }
        return membership;
    }

    private TeamResponse response(MembershipRow membership) {
        TeamRow team = repository.findTeam(membership.teamId())
            .orElseThrow(() -> BusinessException.notFound("Equipe não encontrada."));
        return new TeamResponse(team.publicId(), team.name(), team.acronym(), team.city(), team.stateCode(),
            team.gameStyle(), team.ownsField(), team.description(), team.recruitmentStatus(), membership.role(),
            repository.members(team.id()), team.version());
    }

    private String recruitment(String value) {
        String normalized = upper(value);
        if (!RECRUITMENT.contains(normalized)) {
            throw BusinessException.badRequest("INVALID_RECRUITMENT_STATUS", "Selecione um status de recrutamento válido.");
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String nullable(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? null : normalized;
    }

    private String upper(String value) {
        return normalize(value).toUpperCase(Locale.ROOT);
    }
}
