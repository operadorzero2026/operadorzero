package br.com.operadorzero.team;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.team.TeamDtos.CreateTeamRequest;
import br.com.operadorzero.team.TeamDtos.InviteRequest;
import br.com.operadorzero.team.TeamDtos.MessageResponse;
import br.com.operadorzero.team.TeamDtos.TeamResponse;
import br.com.operadorzero.team.TeamDtos.TeamWorkspaceResponse;
import br.com.operadorzero.team.TeamDtos.TransferCaptaincyRequest;
import br.com.operadorzero.team.TeamDtos.UpdateTeamRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teams")
public class TeamController {
    private final TeamService service;

    public TeamController(TeamService service) {
        this.service = service;
    }

    @GetMapping("/workspace")
    TeamWorkspaceResponse workspace(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.workspace(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TeamResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                        @Valid @RequestBody CreateTeamRequest request) {
        return service.create(user, request);
    }

    @PatchMapping("/{teamId}")
    TeamResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID teamId,
                        @Valid @RequestBody UpdateTeamRequest request) {
        return service.update(user, teamId, request);
    }

    @PostMapping("/{teamId}/invitations")
    MessageResponse invite(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID teamId,
                           @Valid @RequestBody InviteRequest request, HttpServletRequest servletRequest) {
        return service.invite(user, teamId, request, servletRequest);
    }

    @PostMapping("/invitations/{invitationId}/accept")
    TeamResponse accept(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID invitationId) {
        return service.acceptInvitation(user, invitationId);
    }

    @PostMapping("/invitations/{invitationId}/decline")
    MessageResponse decline(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID invitationId) {
        return service.declineInvitation(user, invitationId);
    }

    @PostMapping("/leave")
    MessageResponse leave(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.leave(user);
    }

    @PostMapping("/{teamId}/captaincy")
    TeamResponse transferCaptaincy(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID teamId,
                                   @Valid @RequestBody TransferCaptaincyRequest request) {
        return service.transferCaptaincy(user, teamId, request);
    }
}
