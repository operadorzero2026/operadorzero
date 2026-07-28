package br.com.operadorzero.community;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CommunityDtos {
    private CommunityDtos() {}

    public record CategoryResponse(UUID id, String slug, String name) {}

    public record AuthorResponse(
        UUID id,
        String username,
        String displayName,
        String callsign,
        String city,
        String stateCode,
        String teamName
    ) {}

    public record PostSummaryResponse(
        UUID id,
        AuthorResponse author,
        CategoryResponse category,
        String title,
        String description,
        Instant createdAt,
        int voteCount,
        int commentCount,
        boolean votedByCurrentUser,
        boolean savedByCurrentUser,
        boolean canManage,
        boolean commentsLocked,
        UUID coverImageId
    ) {}

    public record PostResponse(
        UUID id,
        AuthorResponse author,
        CategoryResponse category,
        String title,
        String description,
        Instant createdAt,
        Instant updatedAt,
        int voteCount,
        int commentCount,
        boolean votedByCurrentUser,
        boolean savedByCurrentUser,
        boolean canManage,
        boolean commentsLocked,
        List<UUID> imageIds
    ) {}

    public record PostListResponse(List<PostSummaryResponse> items, int page, int size, long total) {}

    public record CreatePostRequest(
        @NotBlank @Size(max = 80) String categorySlug,
        @NotBlank @Size(min = 3, max = 160) String title,
        @NotBlank @Size(max = 10000) String description,
        @NotNull UUID idempotencyKey
    ) {}

    public record CreateCommentRequest(
        UUID parentCommentId,
        @NotBlank @Size(max = 3000) String description,
        @NotNull UUID idempotencyKey
    ) {}

    public record CommentResponse(
        UUID id,
        UUID postId,
        UUID parentCommentId,
        AuthorResponse author,
        String description,
        Instant createdAt,
        boolean canManage
    ) {}

    public record CommentListResponse(List<CommentResponse> items) {}
    public record ToggleResponse(boolean active, int count) {}
    public record MessageResponse(String message) {}

    public record ReportRequest(
        @NotBlank @Pattern(regexp = "SPAM|HARASSMENT|HATE|THREAT|PERSONAL_DATA|FRAUD|ILLEGAL|OTHER")
        String reason,
        @Size(max = 1000) String details
    ) {}

    public record ModerationStatusRequest(
        @NotBlank @Pattern(regexp = "PUBLISHED|SUSPENDED|DELETED") String status,
        @NotBlank @Size(max = 500) String reason
    ) {}

    public record CommentsLockRequest(boolean locked, @NotBlank @Size(max = 500) String reason) {}

    public record ResolveReportRequest(
        @NotBlank @Pattern(regexp = "REVIEWED|DISMISSED|ACTIONED") String status,
        @NotBlank @Size(max = 1000) String resolution
    ) {}

    public record ReportResponse(
        UUID id,
        String targetType,
        UUID targetId,
        String targetTitle,
        String reporterUsername,
        String reason,
        String details,
        String status,
        Instant createdAt
    ) {}

    public record ReportListResponse(List<ReportResponse> items) {}
}
