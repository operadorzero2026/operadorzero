package br.com.operadorzero.gamification;

import br.com.operadorzero.gamification.DecorationService.Balance;
import br.com.operadorzero.gamification.DecorationService.DecorateResponse;
import br.com.operadorzero.gamification.DecorationService.HonorBoard;
import br.com.operadorzero.identity.AuthenticatedUser;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DecorationController {
    private final DecorationService service;

    public DecorationController(DecorationService service) {
        this.service = service;
    }

    @PostMapping("/api/feed/posts/{id}/decorations")
    DecorateResponse decorate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.decorate(user, id);
    }

    @GetMapping("/api/feed/decorations/balance")
    Balance balance(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.balance(user);
    }

    @GetMapping("/api/feed/honor-board")
    HonorBoard honorBoard(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam(defaultValue = "false") boolean previous) {
        return service.honorBoard(user, previous);
    }
}
