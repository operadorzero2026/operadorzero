package br.com.operadorzero.team;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.team.TeamDtos.CreateTeamRequest;
import br.com.operadorzero.team.TeamDtos.InviteRequest;
import br.com.operadorzero.team.TeamDtos.MessageResponse;
import br.com.operadorzero.team.TeamDtos.TeamResponse;
import br.com.operadorzero.team.TeamDtos.TeamSummaryResponse;
import br.com.operadorzero.team.TeamDtos.TeamWorkspaceResponse;
import br.com.operadorzero.team.TeamDtos.TransferCaptaincyRequest;
import br.com.operadorzero.team.TeamDtos.UpdateTeamRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/teams")
public class TeamController {
    private final TeamService service;

    public TeamController(TeamService service) {
        this.service = service;
    }

    @GetMapping
    TeamDtos.TeamDirectoryResponse directory(@RequestParam(defaultValue = "") @Size(max = 80) String q,
                                            @RequestParam(defaultValue = "") @Size(max = 2) String stateCode,
                                            @RequestParam(defaultValue = "") @Size(max = 80) String city,
                                            @RequestParam(defaultValue = "50") int limit) {
        return service.directory(q, stateCode, city, limit);
    }

    @GetMapping("/{teamId}/profile")
    TeamDtos.TeamProfileResponse profile(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID teamId) {
        return service.profile(user, teamId);
    }

    @PostMapping("/{teamId}/join-requests")
    @ResponseStatus(HttpStatus.CREATED)
    MessageResponse requestToJoin(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID teamId,
                                  @Valid @RequestBody TeamDtos.JoinRequestCreate request, HttpServletRequest servletRequest) {
        return service.requestToJoin(user, teamId, request, servletRequest);
    }

    @PostMapping("/{teamId}/join-requests/cancel")
    MessageResponse cancelJoinRequest(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID teamId) {
        return service.cancelJoinRequest(user, teamId);
    }

    @PostMapping("/join-requests/{requestId}/accept")
    TeamResponse acceptJoinRequest(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID requestId) {
        return service.respondJoinRequest(user, requestId, true);
    }

    @PostMapping("/join-requests/{requestId}/decline")
    TeamResponse declineJoinRequest(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID requestId) {
        return service.respondJoinRequest(user, requestId, false);
    }

    @DeleteMapping("/{teamId}/members/{operatorId}")
    TeamResponse removeMember(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID teamId,
                              @PathVariable UUID operatorId) {
        return service.removeMember(user, teamId, operatorId);
    }

    @PatchMapping("/{teamId}/members/{operatorId}/role")
    TeamResponse changeMemberRole(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID teamId,
                                  @PathVariable UUID operatorId, @Valid @RequestBody TeamDtos.ChangeMemberRoleRequest request) {
        return service.changeMemberRole(user, teamId, operatorId, request);
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

    @GetMapping("/summary")
    TeamSummaryResponse summary() {
        return service.summary();
    }

    @PostMapping(value = "/{teamId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void saveLogo(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID teamId,
                  @RequestParam("file") MultipartFile file) {
        service.saveLogo(user, teamId, file);
    }

    @GetMapping("/{teamId}/logo")
    ResponseEntity<byte[]> logo(@PathVariable UUID teamId) {
        TeamRepository.LogoRow logo = service.logo(teamId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(logo.contentType()))
            .cacheControl(CacheControl.noStore()).body(logo.imageData());
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
