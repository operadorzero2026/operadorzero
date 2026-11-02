package br.com.operadorzero.feed;

import br.com.operadorzero.identity.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feed")
public class FeedController {
    private final FeedService service;

    public FeedController(FeedService service) {
        this.service = service;
    }

    @GetMapping
    FeedDtos.FeedPage feed(@AuthenticationPrincipal AuthenticatedUser user,
                           @RequestParam(defaultValue = "for-you") String tab,
                           @RequestParam(required = false) String cursor,
                           @RequestParam(required = false) Integer limit) {
        return service.page(user, tab, cursor, limit);
    }
}
