package br.com.operadorzero.feed;

import br.com.operadorzero.feed.FeedInteractionService.CommentPage;
import br.com.operadorzero.feed.FeedInteractionService.CommentRequest;
import br.com.operadorzero.feed.FeedInteractionService.Counts;
import br.com.operadorzero.feed.FeedInteractionService.FollowStats;
import br.com.operadorzero.feed.FeedInteractionService.ReportRequest;
import br.com.operadorzero.feed.FeedInteractionService.ResolveRequest;
import br.com.operadorzero.feed.FeedInteractionService.ShareRequest;
import br.com.operadorzero.feed.FeedInteractionService.ShareResponse;
import br.com.operadorzero.identity.AuthenticatedUser;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeedInteractionController {
    private final FeedInteractionService service;

    public FeedInteractionController(FeedInteractionService service) {
        this.service = service;
    }

    @PutMapping("/api/feed/posts/{id}/reactions/{kind}")
    Counts react(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @PathVariable String kind) {
        return service.react(user, id, kind, true);
    }

    @DeleteMapping("/api/feed/posts/{id}/reactions/{kind}")
    Counts unreact(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @PathVariable String kind) {
        return service.react(user, id, kind, false);
    }

    @PostMapping("/api/feed/posts/{id}/views")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void view(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @Valid @RequestBody FeedInteractionService.ViewRequest request) {
        service.view(user, id, request);
    }

    @PutMapping("/api/feed/posts/{id}/save")
    Counts save(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.save(user, id, true);
    }

    @DeleteMapping("/api/feed/posts/{id}/save")
    Counts unsave(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.save(user, id, false);
    }

    @PostMapping("/api/feed/posts/{id}/shares")
    ShareResponse share(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @Valid @RequestBody ShareRequest request) {
        return service.share(user, id, request.channel());
    }

    @GetMapping("/api/feed/posts/{id}/comments")
    CommentPage comments(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                         @RequestParam(required = false) UUID parentId, @RequestParam(required = false) Instant after) {
        return service.comments(user, id, parentId, after);
    }

    @PostMapping("/api/feed/posts/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    Counts comment(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @Valid @RequestBody CommentRequest request) {
        return service.comment(user, id, request);
    }

    @DeleteMapping("/api/feed/comments/{commentId}")
    Counts deleteComment(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID commentId) {
        return service.deleteComment(user, commentId);
    }

    @PostMapping("/api/feed/posts/{id}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reportPost(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @Valid @RequestBody ReportRequest request) {
        service.reportPost(user, id, request);
    }

    @PostMapping("/api/feed/comments/{commentId}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reportComment(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID commentId, @Valid @RequestBody ReportRequest request) {
        service.reportComment(user, commentId, request);
    }

    @PostMapping("/api/recons/{id}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reportRecon(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @Valid @RequestBody ReportRequest request) {
        service.reportRecon(user, id, request);
    }

    @PutMapping("/api/operators/{username}/follow")
    FollowStats follow(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String username) {
        return service.follow(user, username, true);
    }

    @DeleteMapping("/api/operators/{username}/follow")
    FollowStats unfollow(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String username) {
        return service.follow(user, username, false);
    }

    @GetMapping("/api/operators/{username}/follow")
    FollowStats followStats(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String username) {
        return service.stats(user, username);
    }

    @GetMapping("/api/admin/feed/reports")
    List<FeedInteractionRepository.ReportRow> reports() {
        return service.reports();
    }

    @PostMapping("/api/admin/feed/reports/{reportId}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resolve(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID reportId, @Valid @RequestBody ResolveRequest request) {
        service.resolve(user, reportId, request);
    }
}
