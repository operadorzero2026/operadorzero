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
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.image.SafeRasterImageProcessor;
import br.com.operadorzero.shared.web.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CommunityService {
    private static final Set<String> REPORT_REASONS = Set.of(
        "SPAM", "HARASSMENT", "HATE", "THREAT", "PERSONAL_DATA", "FRAUD", "ILLEGAL", "OTHER");
    private final CommunityRepository repository;
    private final AuditEventRepository audit;
    private final Clock clock;

    @Autowired
    public CommunityService(CommunityRepository repository, AuditEventRepository audit) {
        this(repository, audit, Clock.systemUTC());
    }

    CommunityService(CommunityRepository repository, AuditEventRepository audit, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        return repository.categories();
    }

    @Transactional(readOnly = true)
    public PostListResponse posts(AuthenticatedUser user, String query, String category, String sort, int page, int size) {
        long userId = user == null ? 0L : user.internalId();
        boolean moderator = isModerator(user);
        String orderBy = switch (normalize(sort)) {
            case "SOCIAL" -> """
                CASE
                  WHEN p.author_user_id = :userId THEN 0
                  WHEN EXISTS (SELECT 1 FROM operator_friendship f WHERE f.status='ACCEPTED' AND ((f.requester_user_id=:userId AND f.addressee_user_id=p.author_user_id) OR (f.addressee_user_id=:userId AND f.requester_user_id=p.author_user_id))) THEN 1
                  WHEN EXISTS (SELECT 1 FROM team_member viewer_tm JOIN team_member author_tm ON author_tm.team_id=viewer_tm.team_id AND author_tm.left_at IS NULL WHERE viewer_tm.user_id=:userId AND viewer_tm.left_at IS NULL AND author_tm.user_id=p.author_user_id) THEN 2
                  WHEN EXISTS (SELECT 1 FROM operator_profile viewer_op WHERE viewer_op.user_id=:userId AND viewer_op.state_code=op.state_code AND viewer_op.city=op.city) THEN 3
                  WHEN EXISTS (SELECT 1 FROM operator_profile viewer_op WHERE viewer_op.user_id=:userId AND viewer_op.state_code=op.state_code) THEN 4
                  ELSE 5 END,
                (COALESCE(votes.total,0) + COALESCE(comments.total,0) * 2) DESC, p.created_at DESC, p.id DESC
                """;
            case "MOST_COMMENTED" -> "comment_count DESC, p.created_at DESC, p.id DESC";
            case "TOP" -> "vote_count DESC, p.created_at DESC, p.id DESC";
            default -> "p.created_at DESC, p.id DESC";
        };
        String normalizedQuery = query == null ? "" : query.trim();
        String normalizedCategory = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        return new PostListResponse(
            repository.search(userId, moderator, normalizedQuery, normalizedCategory, orderBy, size, page * size),
            page, size, repository.count(normalizedQuery, normalizedCategory));
    }

    @Transactional(readOnly = true)
    public PostResponse post(AuthenticatedUser user, UUID postId) {
        return repository.find(postId, user == null ? 0L : user.internalId(), isModerator(user))
            .orElseThrow(() -> BusinessException.notFound("Publicação não encontrada."));
    }

    @Transactional(readOnly = true)
    public AuthorResponse author(String username) {
        return repository.author(username)
            .orElseThrow(() -> BusinessException.notFound("Operador não encontrado."));
    }

    @Transactional
    public PostResponse createPost(AuthenticatedUser user, CreatePostRequest request) {
        String title = clean(request.title());
        String body = clean(request.description());
        UUID id = repository.createPost(user.internalId(), normalizeSlug(request.categorySlug()), title, body,
            request.idempotencyKey(), clock.instant());
        if (id == null) {
            throw BusinessException.badRequest("INVALID_COMMUNITY_CATEGORY", "Selecione uma categoria válida.");
        }
        audit.record(user.internalId(), "COMMUNITY_POST_CREATED", "COMMUNITY_POST", id, null, clock.instant());
        return post(user, id);
    }

    @Transactional
    public MessageResponse addImage(AuthenticatedUser user, UUID postId, MultipartFile file) {
        var image = SafeRasterImageProcessor.process(file);
        UUID imageId = repository.addImage(postId, user.internalId(), image.contentType(), image.data(), clock.instant());
        if (imageId == null) {
            throw BusinessException.conflict("COMMUNITY_IMAGE_LIMIT", "A publicação não foi encontrada ou já possui quatro imagens.");
        }
        audit.record(user.internalId(), "COMMUNITY_POST_IMAGE_ADDED", "COMMUNITY_POST", postId, null, clock.instant());
        return new MessageResponse("Imagem adicionada.");
    }

    @Transactional(readOnly = true)
    public CommunityRepository.MediaRow image(UUID imageId) {
        return repository.media(imageId)
            .orElseThrow(() -> BusinessException.notFound("Imagem não encontrada."));
    }

    @Transactional(readOnly = true)
    public CommentListResponse comments(AuthenticatedUser user, UUID postId) {
        post(user, postId);
        return new CommentListResponse(repository.comments(postId, user == null ? 0L : user.internalId(), isModerator(user)));
    }

    @Transactional
    public MessageResponse comment(AuthenticatedUser user, UUID postId, CreateCommentRequest request) {
        String body = clean(request.description());
        UUID commentId = repository.createComment(postId, request.parentCommentId(), user.internalId(), body,
            request.idempotencyKey(), clock.instant());
        if (commentId == null) {
            throw BusinessException.conflict("COMMENTS_UNAVAILABLE", "Os comentários estão bloqueados ou a publicação não está disponível.");
        }
        audit.record(user.internalId(), "COMMUNITY_COMMENT_CREATED", "COMMUNITY_COMMENT", commentId, null, clock.instant());
        return new MessageResponse("Comentário publicado.");
    }

    @Transactional
    public MessageResponse deletePost(AuthenticatedUser user, UUID postId) {
        if (!repository.deletePost(postId, user.internalId(), isModerator(user), clock.instant())) {
            throw BusinessException.notFound("Publicação não encontrada ou sem permissão para excluir.");
        }
        audit.record(user.internalId(), "COMMUNITY_POST_DELETED", "COMMUNITY_POST", postId, null, clock.instant());
        return new MessageResponse("Publicação excluída.");
    }

    @Transactional
    public MessageResponse deleteComment(AuthenticatedUser user, UUID commentId) {
        if (!repository.deleteComment(commentId, user.internalId(), isModerator(user), clock.instant())) {
            throw BusinessException.notFound("Comentário não encontrado ou sem permissão para excluir.");
        }
        audit.record(user.internalId(), "COMMUNITY_COMMENT_DELETED", "COMMUNITY_COMMENT", commentId, null, clock.instant());
        return new MessageResponse("Comentário excluído.");
    }

    @Transactional
    public ToggleResponse vote(AuthenticatedUser user, UUID postId) {
        boolean active;
        if (repository.removeVote(postId, user.internalId())) {
            active = false;
        } else {
            active = repository.addVote(postId, user.internalId(), clock.instant());
            if (!active) throw BusinessException.notFound("Publicação não encontrada.");
        }
        audit.record(user.internalId(), active ? "COMMUNITY_POST_VOTED" : "COMMUNITY_POST_VOTE_REMOVED",
            "COMMUNITY_POST", postId, null, clock.instant());
        return new ToggleResponse(active, repository.voteCount(postId));
    }

    @Transactional
    public ToggleResponse bookmark(AuthenticatedUser user, UUID postId) {
        boolean active;
        if (repository.removeBookmark(postId, user.internalId())) {
            active = false;
        } else {
            active = repository.addBookmark(postId, user.internalId(), clock.instant());
            if (!active) throw BusinessException.notFound("Publicação não encontrada.");
        }
        audit.record(user.internalId(), active ? "COMMUNITY_POST_SAVED" : "COMMUNITY_POST_UNSAVED",
            "COMMUNITY_POST", postId, null, clock.instant());
        return new ToggleResponse(active, 0);
    }

    @Transactional
    public MessageResponse reportPost(AuthenticatedUser user, UUID postId, ReportRequest request) {
        String reason = reportReason(request.reason());
        if (!repository.reportPost(postId, user.internalId(), reason, nullable(request.details()), clock.instant())) {
            throw BusinessException.conflict("REPORT_ALREADY_OPEN", "Você já possui uma denúncia aberta para esta publicação.");
        }
        audit.record(user.internalId(), "COMMUNITY_POST_REPORTED", "COMMUNITY_POST", postId, null, clock.instant());
        return new MessageResponse("Denúncia recebida para análise.");
    }

    @Transactional
    public MessageResponse reportComment(AuthenticatedUser user, UUID commentId, ReportRequest request) {
        String reason = reportReason(request.reason());
        if (!repository.reportComment(commentId, user.internalId(), reason, nullable(request.details()), clock.instant())) {
            throw BusinessException.conflict("REPORT_ALREADY_OPEN", "Você já possui uma denúncia aberta para este comentário.");
        }
        audit.record(user.internalId(), "COMMUNITY_COMMENT_REPORTED", "COMMUNITY_COMMENT", commentId, null, clock.instant());
        return new MessageResponse("Denúncia recebida para análise.");
    }

    @Transactional(readOnly = true)
    public ReportListResponse reports(AuthenticatedUser user) {
        requireModerator(user);
        return new ReportListResponse(repository.reports());
    }

    @Transactional
    public MessageResponse moderatePost(AuthenticatedUser user, UUID postId, ModerationStatusRequest request) {
        requireModerator(user);
        if (!repository.moderatePost(postId, normalize(request.status()), clock.instant())) {
            throw BusinessException.notFound("Publicação não encontrada.");
        }
        audit.record(user.internalId(), "COMMUNITY_POST_MODERATED", "COMMUNITY_POST", postId,
            clean(request.reason()), clock.instant());
        return new MessageResponse("Situação da publicação atualizada.");
    }

    @Transactional
    public MessageResponse moderateComment(AuthenticatedUser user, UUID commentId, ModerationStatusRequest request) {
        requireModerator(user);
        if (!repository.moderateComment(commentId, normalize(request.status()), clock.instant())) {
            throw BusinessException.notFound("Comentário não encontrado.");
        }
        audit.record(user.internalId(), "COMMUNITY_COMMENT_MODERATED", "COMMUNITY_COMMENT", commentId,
            clean(request.reason()), clock.instant());
        return new MessageResponse("Situação do comentário atualizada.");
    }

    @Transactional
    public MessageResponse lockComments(AuthenticatedUser user, UUID postId, CommentsLockRequest request) {
        requireModerator(user);
        if (!repository.lockComments(postId, request.locked(), clock.instant())) {
            throw BusinessException.notFound("Publicação não encontrada.");
        }
        audit.record(user.internalId(), request.locked() ? "COMMUNITY_COMMENTS_LOCKED" : "COMMUNITY_COMMENTS_UNLOCKED",
            "COMMUNITY_POST", postId, clean(request.reason()), clock.instant());
        return new MessageResponse(request.locked() ? "Comentários bloqueados." : "Comentários liberados.");
    }

    @Transactional
    public MessageResponse resolveReport(AuthenticatedUser user, UUID reportId, ResolveReportRequest request) {
        requireModerator(user);
        if (!repository.resolveReport(reportId, user.internalId(), normalize(request.status()),
                clean(request.resolution()), clock.instant())) {
            throw BusinessException.conflict("REPORT_NOT_OPEN", "A denúncia não está mais aberta.");
        }
        audit.record(user.internalId(), "COMMUNITY_REPORT_RESOLVED", "COMMUNITY_REPORT", reportId,
            clean(request.resolution()), clock.instant());
        return new MessageResponse("Denúncia analisada.");
    }

    private boolean isModerator(AuthenticatedUser user) {
        return user != null && user.roles().stream().anyMatch(role -> role.equals("ADMIN") || role.equals("MODERATOR"));
    }

    private void requireModerator(AuthenticatedUser user) {
        if (!isModerator(user)) throw BusinessException.forbidden("Acesso restrito à moderação.");
    }

    private String reportReason(String value) {
        String normalized = normalize(value);
        if (!REPORT_REASONS.contains(normalized)) {
            throw BusinessException.badRequest("INVALID_REPORT_REASON", "Selecione um motivo válido.");
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeSlug(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String clean(String value) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.indexOf('\0') >= 0) {
            throw BusinessException.badRequest("INVALID_TEXT", "O texto contém caracteres inválidos.");
        }
        return cleaned;
    }

    private String nullable(String value) {
        if (value == null) return null;
        String cleaned = clean(value);
        return cleaned.isEmpty() ? null : cleaned;
    }
}
