package br.com.operadorzero.operation;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operation.OperatorInviteService.InviteOptions;
import br.com.operadorzero.operation.OperatorInviteService.InviteToGameRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OperatorInviteController {
    private final OperatorInviteService service;

    public OperatorInviteController(OperatorInviteService service) {
        this.service = service;
    }

    @GetMapping("/api/operators/{username}/invite-options")
    InviteOptions options(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String username) {
        return service.options(user, username);
    }

    @PostMapping("/api/operations/{id}/invitations")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void invite(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @Valid @RequestBody InviteToGameRequest request) {
        service.inviteToGame(user, id, request.username());
    }
}
