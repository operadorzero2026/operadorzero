package br.com.operadorzero.community;

import br.com.operadorzero.community.CommunityDtos.AuthorResponse;
import br.com.operadorzero.community.CommunityDtos.CategoryResponse;
import br.com.operadorzero.community.CommunityDtos.CommentListResponse;
import br.com.operadorzero.community.CommunityDtos.CommentsLockRequest;
import br.com.operadorzero.community.CommunityDtos.CreateCommentRequest;
import br.com.operadorzero.community.CommunityDtos.CreatePostRequest;
import br.com.operadorzero.community.CommunityDtos.MessageResponse;
import br.com.operadorzero.community.CommunityDtos.ModerationStatusRequest;
import br.com.operadorzero.community.CommunityDtos.PostListResponse;
import br.com.operadorzero.community.CommunityDtos.PostResponse;
import br.com.operadorzero.community.CommunityDtos.ReportListResponse;
import br.com.operadorzero.community.CommunityDtos.ReportRequest;
import br.com.operadorzero.community.CommunityDtos.ResolveReportRequest;
import br.com.operadorzero.community.CommunityDtos.ToggleResponse;
import br.com.operadorzero.identity.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
public class CommunityController {
    private final CommunityService service;

    public CommunityController(CommunityService service) {
        this.service = service;
    }

    @GetMapping("/api/community/categories")
    List<CategoryResponse> categories() {
        return service.categories();
    }

    @GetMapping("/api/community/posts")
    PostListResponse posts(@AuthenticationPrincipal AuthenticatedUser user,
                           @RequestParam(defaultValue = "") @Size(max = 100) String q,
                           @RequestParam(defaultValue = "") @Size(max = 80) String category,
                           @RequestParam(defaultValue = "RECENT") @Size(max = 32) String sort,
                           @RequestParam(defaultValue = "0") @Min(0) int page,
                           @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return service.posts(user, q, category, sort, page, size);
    }

    @GetMapping("/api/community/posts/{postId}")
    PostResponse post(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID postId) {
        return service.post(user, postId);
    }

    @GetMapping("/api/community/posts/{postId}/comments")
    CommentListResponse comments(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID postId) {
        return service.comments(user, postId);
    }

    @GetMapping("/api/community/authors/{username}")
    AuthorResponse author(@PathVariable @Size(max = 30) String username) {
        return service.author(username);
    }

    @GetMapping("/api/community/images/{imageId}")
    ResponseEntity<byte[]> image(@PathVariable UUID imageId) {
        var image = service.image(imageId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
            .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePublic())
            .body(image.data());
    }

    @PostMapping("/api/community/posts")
    @ResponseStatus(HttpStatus.CREATED)
    PostResponse createPost(@AuthenticationPrincipal AuthenticatedUser user,
                            @Valid @RequestBody CreatePostRequest request) {
        return service.createPost(user, request);
    }

    @PostMapping(value = "/api/community/posts/{postId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    MessageResponse addImage(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID postId,
                             @RequestParam("file") MultipartFile file) {
        return service.addImage(user, postId, file);
    }

    @PostMapping("/api/community/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    MessageResponse comment(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID postId,
                            @Valid @RequestBody CreateCommentRequest request) {
        return service.comment(user, postId, request);
    }

    @PostMapping("/api/community/posts/{postId}/vote")
    ToggleResponse vote(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID postId) {
        return service.vote(user, postId);
    }

    @PostMapping("/api/community/posts/{postId}/bookmark")
    ToggleResponse bookmark(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID postId) {
        return service.bookmark(user, postId);
    }

    @PostMapping("/api/community/posts/{postId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    MessageResponse reportPost(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID postId,
                               @Valid @RequestBody ReportRequest request) {
        return service.reportPost(user, postId, request);
    }

    @PostMapping("/api/community/comments/{commentId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    MessageResponse reportComment(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID commentId,
                                  @Valid @RequestBody ReportRequest request) {
        return service.reportComment(user, commentId, request);
    }

    @DeleteMapping("/api/community/posts/{postId}")
    MessageResponse deletePost(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID postId) {
        return service.deletePost(user, postId);
    }

    @DeleteMapping("/api/community/comments/{commentId}")
    MessageResponse deleteComment(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID commentId) {
        return service.deleteComment(user, commentId);
    }

    @GetMapping("/api/admin/community/reports")
    ReportListResponse reports(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.reports(user);
    }

    @PatchMapping("/api/admin/community/posts/{postId}/status")
    MessageResponse moderatePost(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID postId,
                                 @Valid @RequestBody ModerationStatusRequest request) {
        return service.moderatePost(user, postId, request);
    }

    @PatchMapping("/api/admin/community/comments/{commentId}/status")
    MessageResponse moderateComment(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID commentId,
                                    @Valid @RequestBody ModerationStatusRequest request) {
        return service.moderateComment(user, commentId, request);
    }

    @PatchMapping("/api/admin/community/posts/{postId}/comments-lock")
    MessageResponse lockComments(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID postId,
                                 @Valid @RequestBody CommentsLockRequest request) {
        return service.lockComments(user, postId, request);
    }

    @PatchMapping("/api/admin/community/reports/{reportId}")
    MessageResponse resolveReport(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID reportId,
                                  @Valid @RequestBody ResolveReportRequest request) {
        return service.resolveReport(user, reportId, request);
    }
}
