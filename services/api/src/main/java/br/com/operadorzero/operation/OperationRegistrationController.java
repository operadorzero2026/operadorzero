package br.com.operadorzero.operation;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operation.OperationDtos.MessageResponse;
import br.com.operadorzero.operation.OperationRegistrationDtos.Overview;
import br.com.operadorzero.operation.OperationRegistrationDtos.RegisterRequest;
import br.com.operadorzero.operation.OperationRegistrationDtos.RemoveRequest;
import br.com.operadorzero.operation.OperationRegistrationDtos.RoleRequest;
import br.com.operadorzero.operation.OperationRegistrationDtos.SettingsRequest;
import br.com.operadorzero.operation.OperationRegistrationDtos.TeamChangeDecision;
import br.com.operadorzero.operation.OperationRegistrationDtos.TeamChangeRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operations/{id}")
public class OperationRegistrationController {
    private final OperationRegistrationService service;

    public OperationRegistrationController(OperationRegistrationService service) {
        this.service = service;
    }

    @GetMapping("/registration")
    Overview overview(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.overview(user, id);
    }

    @PostMapping("/participation")
    @ResponseStatus(HttpStatus.CREATED)
    MessageResponse register(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @Valid @RequestBody RegisterRequest request) {
        return service.register(user, id, request);
    }

    @PostMapping("/participation/cancel")
    MessageResponse cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.cancel(user, id);
    }

    @PutMapping("/participation/role")
    MessageResponse role(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
        return service.updateRole(user, id, request);
    }

    @PostMapping("/participants/{operatorId}/remove")
    MessageResponse remove(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @PathVariable UUID operatorId,
                           @Valid @RequestBody RemoveRequest request) {
        return service.remove(user, id, operatorId, request.reason());
    }

    @PostMapping("/participants/{operatorId}/authorize-return")
    MessageResponse authorizeReturn(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @PathVariable UUID operatorId) {
        return service.authorizeReturn(user, id, operatorId);
    }

    @PostMapping("/registration/close")
    MessageResponse close(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.close(user, id);
    }

    @PostMapping("/registration/reopen")
    MessageResponse reopen(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.reopen(user, id);
    }

    @PatchMapping("/registration/settings")
    MessageResponse settings(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @RequestBody SettingsRequest request) {
        return service.setTeamChangeEnabled(user, id, request.teamChangeRequestsEnabled());
    }

    @PostMapping("/team-changes")
    @ResponseStatus(HttpStatus.CREATED)
    MessageResponse requestChange(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @Valid @RequestBody TeamChangeRequest request) {
        return service.requestTeamChange(user, id, request.toTeamId());
    }

    @DeleteMapping("/team-changes/mine")
    MessageResponse cancelChange(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.cancelTeamChange(user, id);
    }

    @PostMapping("/team-changes/{requestId}/decision")
    MessageResponse decide(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @PathVariable UUID requestId,
                           @Valid @RequestBody TeamChangeDecision request) {
        return service.decideTeamChange(user, id, requestId, request.action());
    }
}
