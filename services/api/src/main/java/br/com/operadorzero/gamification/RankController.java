package br.com.operadorzero.gamification;

import br.com.operadorzero.gamification.RankService.MyRank;
import br.com.operadorzero.gamification.RankService.RankView;
import br.com.operadorzero.gamification.RankService.Settings;
import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operation.OperationDtos.MessageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RankController {
    private final RankService service;

    public RankController(RankService service) {
        this.service = service;
    }

    public record RuleRequest(@NotNull Integer xp, Integer dailyCap, @NotNull BigDecimal samePairFactor, boolean active) {}

    public record TiersRequest(@NotNull Map<String, Integer> minXp) {}

    @GetMapping("/api/me/rank")
    MyRank mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.mine(user);
    }

    @PostMapping("/api/me/rank/promotion-seen")
    MessageResponse seen(@AuthenticationPrincipal AuthenticatedUser user) {
        service.promotionSeen(user);
        return new MessageResponse("Ok.");
    }

    @GetMapping("/api/operators/{username}/rank")
    RankView of(@PathVariable String username) {
        return service.of(username);
    }

    // ---- administrador (ROLE_ADMIN no SecurityConfig) ----

    @GetMapping("/api/admin/gamification")
    Settings settings() {
        return service.settings();
    }

    @PutMapping("/api/admin/gamification/rules/{actionCode}")
    MessageResponse rule(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable String actionCode, @Valid @RequestBody RuleRequest request) {
        service.updateRule(admin, actionCode, request.xp(), request.dailyCap(), request.samePairFactor(), request.active());
        return new MessageResponse("XP atualizado. Vale para as próximas ações.");
    }

    @PutMapping("/api/admin/gamification/tiers")
    MessageResponse tiers(@AuthenticationPrincipal AuthenticatedUser admin, @Valid @RequestBody TiersRequest request) {
        service.updateTiers(admin, request.minXp());
        return new MessageResponse("Faixas das patentes atualizadas.");
    }
}
