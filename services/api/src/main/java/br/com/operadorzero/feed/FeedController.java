package br.com.operadorzero.feed;

import br.com.operadorzero.identity.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feed")
public class FeedController {
    private final FeedService service;
    private final FeedPostService posts;

    public FeedController(FeedService service, FeedPostService posts) {
        this.service = service;
        this.posts = posts;
    }

    @GetMapping
    FeedDtos.FeedPage feed(@AuthenticationPrincipal AuthenticatedUser user,
                           @RequestParam(defaultValue = "for-you") String tab,
                           @RequestParam(required = false) String cursor,
                           @RequestParam(required = false) Integer limit) {
        return service.page(user, tab, cursor, limit);
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    FeedPostService.CreatePostResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                              @Valid @RequestBody FeedPostService.CreatePostRequest request) {
        return posts.create(user, request);
    }

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        posts.delete(user, id);
    }
}
