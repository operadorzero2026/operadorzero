package br.com.operadorzero.feed;

import br.com.operadorzero.feed.FeedInteractionRepository.CommentRef;
import br.com.operadorzero.feed.FeedInteractionRepository.CommentRow;
import br.com.operadorzero.feed.FeedInteractionRepository.ReportRow;
import br.com.operadorzero.feed.FeedInteractionRepository.ReportTarget;
import br.com.operadorzero.feed.FeedInteractionRepository.VisiblePost;
import br.com.operadorzero.gamification.XpService;
import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.notification.Notifier;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.ratelimit.UserActionLimiter;
import br.com.operadorzero.shared.web.BusinessException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reacoes, salvos, compartilhamentos, seguir, comentarios e denuncias do feed. */
@Service
public class FeedInteractionService {
    static final int COMMENT_PAGE = 30;
    private static final Map<String, String> REACTION_COUNTER = Map.of("ON_TARGET", "on_target", "ELIMINATED", "eliminated");

    public record CommentRequest(@NotBlank @Size(max = 1000) String body, UUID parentId, @NotNull UUID idempotencyKey) {}
    public record ShareRequest(@NotBlank @Pattern(regexp = "WHATSAPP|LINK|NATIVE") String channel) {}
    public record ReportRequest(@NotBlank @Pattern(regexp = "SPAM|HARASSMENT|HATE|THREAT|PERSONAL_DATA|FRAUD|ILLEGAL|OTHER") String reason,
                                @Size(max = 1000) String details) {}
    public record ResolveRequest(@NotBlank @Pattern(regexp = "DISMISS|REMOVE") String action, @Size(max = 1000) String resolution) {}
    public record Counts(long onTarget, long eliminated, long comments, long shares, long saves) {}
    public record CommentResponse(UUID id, UUID parentId, String authorUsername, String authorCallsign, String rankCode, String body,
                                  Instant createdAt, boolean own, boolean canDelete, int replies) {}
    public record CommentPage(List<CommentResponse> items, Instant nextAfter) {}
    public record ShareResponse(String url) {}
    public record FollowStats(long followers, long following, boolean follows) {}
    public record ViewRequest(@jakarta.validation.constraints.PositiveOrZero long watchedMs, boolean completed) {}

    private final FeedInteractionRepository repository;
    private final FeedPostRepository posts;
    private final XpService xp;
    private final Notifier notifier;
    private final UserActionLimiter limiter;
    private final AuditEventRepository audit;
    private final String publicBaseUrl;
    private final Clock clock;

    @Autowired
    public FeedInteractionService(FeedInteractionRepository repository, FeedPostRepository posts, XpService xp, Notifier notifier,
                                  UserActionLimiter limiter, AuditEventRepository audit,
                                  @Value("${app.auth.frontend-base-url:http://localhost:4174}") String publicBaseUrl) {
        this(repository, posts, xp, notifier, limiter, audit, publicBaseUrl, Clock.systemUTC());
    }

    FeedInteractionService(FeedInteractionRepository repository, FeedPostRepository posts, XpService xp, Notifier notifier,
                           UserActionLimiter limiter, AuditEventRepository audit, String publicBaseUrl, Clock clock) {
        this.repository = repository;
        this.posts = posts;
        this.xp = xp;
        this.notifier = notifier;
        this.limiter = limiter;
        this.audit = audit;
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        this.clock = clock;
    }

    // ---- Reacoes, salvos e compartilhar ----

    @Transactional
    public Counts react(AuthenticatedUser user, UUID postId, String kind, boolean on) {
        String counter = REACTION_COUNTER.get(kind);
        if (counter == null) throw BusinessException.badRequest("INVALID_REACTION", "Reação inválida.");
        VisiblePost post = visible(user, postId);
        Instant now = clock.instant();
        if (on) {
            limiter.check("feed-reaction", user.internalId(), 300, Duration.ofHours(1));
            if (repository.addReaction(post.id(), user.internalId(), kind, now)) {
                repository.bump(post.id(), counter, 1, now);
                // Um XP por pessoa e post (as duas reacoes contam juntas); reagir ao proprio post nao rende.
                if (post.authorUserId() != user.internalId()) {
                    xp.award(post.authorUserId(), "REACTION_RECEIVED", "FEED_REACTION", post.id(), user.internalId());
                }
            }
        } else if (repository.removeReaction(post.id(), user.internalId(), kind)) {
            repository.bump(post.id(), counter, -1, now);
        }
        return counts(post.id());
    }

    @Transactional
    public Counts save(AuthenticatedUser user, UUID postId, boolean on) {
        VisiblePost post = visible(user, postId);
        Instant now = clock.instant();
        if (on ? repository.save(post.id(), user.internalId(), now) : repository.unsave(post.id(), user.internalId())) {
            repository.bump(post.id(), "saves", on ? 1 : -1, now);
        }
        return counts(post.id());
    }

    @Transactional
    public ShareResponse share(AuthenticatedUser user, UUID postId, String channel) {
        VisiblePost post = visible(user, postId);
        limiter.check("feed-share", user.internalId(), 60, Duration.ofHours(1));
        Instant now = clock.instant();
        repository.share(post.id(), user.internalId(), channel, now);
        repository.bump(post.id(), "shares", 1, now);
        return new ShareResponse(shareUrl(post.publicId()));
    }

    public String shareUrl(UUID postId) {
        return publicBaseUrl + "/p/" + postId;
    }

    // ---- Visualizacoes ----

    /**
     * Uma passagem pelo post (enviada ao sair dele). O tempo e limitado a 3x a duracao do video (ou 60 s em fotos)
     * para que uma aba esquecida aberta nao infle o "% assistido" do ranking.
     */
    @Transactional
    public void view(AuthenticatedUser user, UUID postId, ViewRequest request) {
        VisiblePost post = visible(user, postId);
        limiter.check("feed-view", user.internalId(), 2000, Duration.ofHours(1));
        long cap = repository.videoDurationMs(post.id()).map(duration -> duration * 3L).orElse(60_000L);
        long watched = Math.min(request.watchedMs(), cap);
        Instant now = clock.instant();
        var delta = repository.recordView(post.id(), user.internalId(), watched, request.completed(), now);
        repository.addViewMetrics(post.id(), delta.firstView(), watched, delta.newCompletion(), now);
    }

    // ---- Seguir ----

    @Transactional
    public FollowStats follow(AuthenticatedUser user, String username, boolean on) {
        if (on) {
            var target = repository.followable(username, user.internalId())
                .orElseThrow(() -> BusinessException.notFound("Operador não encontrado."));
            limiter.check("follow", user.internalId(), 100, Duration.ofHours(1));
            if (repository.follow(user.internalId(), target.id(), clock.instant())) {
                notifier.toUser(target.id(), "NEW_FOLLOWER", posts.callsign(user.internalId()) + " começou a seguir você", null, "Perfil");
            }
        } else {
            repository.unfollow(user.internalId(), username);
        }
        return stats(user, username);
    }

    @Transactional(readOnly = true)
    public FollowStats stats(AuthenticatedUser user, String username) {
        Map<String, Object> row = repository.followStats(username, user.internalId());
        return new FollowStats(((Number) row.get("followers")).longValue(), ((Number) row.get("following")).longValue(),
            Boolean.TRUE.equals(row.get("follows")));
    }

    // ---- Comentarios ----

    @Transactional(readOnly = true)
    public CommentPage comments(AuthenticatedUser user, UUID postId, UUID parentId, Instant after) {
        VisiblePost post = visible(user, postId);
        Long parent = parentId == null ? null : repository.commentId(parentId).orElseThrow(() -> BusinessException.notFound("Comentário não encontrado."));
        List<CommentRow> rows = repository.comments(post.id(), parent, user.internalId(), post.authorUserId(), after, COMMENT_PAGE + 1);
        boolean more = rows.size() > COMMENT_PAGE;
        List<CommentRow> page = more ? rows.subList(0, COMMENT_PAGE) : rows;
        return new CommentPage(page.stream().map(row -> new CommentResponse(row.publicId(), row.parentId(), row.authorUsername(),
            row.authorCallsign(), row.rankCode(), row.body(), row.createdAt(), row.own(), row.canDelete(), row.replies())).toList(),
            more ? page.getLast().createdAt() : null);
    }

    @Transactional
    public Counts comment(AuthenticatedUser user, UUID postId, CommentRequest request) {
        VisiblePost post = visible(user, postId);
        if (repository.idForComment(user.internalId(), request.idempotencyKey()).isPresent()) return counts(post.id());
        limiter.check("feed-comment", user.internalId(), 30, Duration.ofMinutes(10));
        Long parentId = null;
        CommentRef parent = null;
        if (request.parentId() != null) {
            parent = repository.comment(request.parentId()).filter(ref -> ref.postId() == post.id())
                .orElseThrow(() -> BusinessException.notFound("Comentário não encontrado."));
            // Respostas ficam em um nivel so: responder uma resposta vira resposta do mesmo comentario.
            parentId = parent.parentId() != null ? parent.parentId() : parent.id();
        }
        Instant now = clock.instant();
        String body = request.body().strip();
        UUID commentId = repository.insertComment(post.id(), user.internalId(), parentId, body, request.idempotencyKey(), now);
        if (commentId == null) return counts(post.id());
        long internalId = repository.commentId(commentId).orElseThrow();
        repository.bump(post.id(), "comments", 1, now);
        if (post.authorUserId() != user.internalId()) {
            xp.award(user.internalId(), "COMMENT_PUBLISHED", "FEED_COMMENT", internalId, post.authorUserId());
        }
        String callsign = posts.callsign(user.internalId());
        Set<Long> notified = new HashSet<>(Set.of(user.internalId()));
        if (parent != null && notified.add(parent.authorUserId())) {
            notifier.toUser(parent.authorUserId(), "FEED_REPLY", callsign + " respondeu seu comentário", excerpt(body), "Feed");
        }
        if (notified.add(post.authorUserId())) {
            notifier.toUser(post.authorUserId(), "FEED_COMMENT", callsign + " comentou seu post", excerpt(body), "Feed");
        }
        for (var mentioned : posts.reachableUsers(FeedText.mentions(body), user.internalId())) {
            if (notified.add(mentioned.id())) {
                notifier.toUser(mentioned.id(), "FEED_MENTION", callsign + " mencionou você em um comentário", excerpt(body), "Feed");
            }
        }
        return counts(post.id());
    }

    /** O autor do comentario ou o dono do post podem apagar. */
    @Transactional
    public Counts deleteComment(AuthenticatedUser user, UUID commentId) {
        CommentRef comment = repository.comment(commentId).orElseThrow(() -> BusinessException.notFound("Comentário não encontrado."));
        if (comment.authorUserId() != user.internalId() && comment.postAuthorUserId() != user.internalId()) {
            throw BusinessException.forbidden("Você não pode apagar este comentário.");
        }
        Instant now = clock.instant();
        var removed = repository.deleteComment(comment.id(), now);
        repository.bump(comment.postId(), "comments", -removed.size(), now);
        removed.forEach(deleted -> xp.reverse(deleted.authorUserId(), "COMMENT_PUBLISHED", "FEED_COMMENT", deleted.id(), comment.postAuthorUserId()));
        return counts(comment.postId());
    }

    // ---- Denuncias ----

    @Transactional
    public void reportPost(AuthenticatedUser user, UUID postId, ReportRequest request) {
        VisiblePost post = visible(user, postId);
        limiter.check("feed-report", user.internalId(), 10, Duration.ofHours(1));
        repository.reportPost(user.internalId(), post.id(), request.reason(), blankToNull(request.details()), clock.instant());
    }

    @Transactional
    public void reportComment(AuthenticatedUser user, UUID commentId, ReportRequest request) {
        CommentRef comment = repository.comment(commentId).orElseThrow(() -> BusinessException.notFound("Comentário não encontrado."));
        limiter.check("feed-report", user.internalId(), 10, Duration.ofHours(1));
        repository.reportComment(user.internalId(), comment.id(), request.reason(), blankToNull(request.details()), clock.instant());
    }

    @Transactional
    public void reportRecon(AuthenticatedUser user, UUID reconId, ReportRequest request) {
        long recon = repository.visibleRecon(reconId, user.internalId()).orElseThrow(() -> BusinessException.notFound("Recon não encontrado."));
        limiter.check("feed-report", user.internalId(), 10, Duration.ofHours(1));
        repository.reportRecon(user.internalId(), recon, request.reason(), blankToNull(request.details()), clock.instant());
    }

    @Transactional(readOnly = true)
    public List<ReportRow> reports() {
        return repository.feedReports();
    }

    /** REMOVE suspende o conteudo (sai do feed) e fecha todas as denuncias abertas dele. */
    @Transactional
    public void resolve(AuthenticatedUser moderator, UUID reportId, ResolveRequest request) {
        ReportTarget target = repository.openReport(reportId).orElseThrow(() -> BusinessException.notFound("Denúncia não encontrada ou já analisada."));
        Instant now = clock.instant();
        boolean remove = "REMOVE".equals(request.action());
        if (remove) repository.suspendTarget(target, now);
        repository.resolveReports(target, moderator.internalId(), remove ? "ACTIONED" : "DISMISSED", blankToNull(request.resolution()), now);
        audit.record(moderator.internalId(), remove ? "FEED_CONTENT_REMOVED" : "FEED_REPORT_DISMISSED", "COMMUNITY_REPORT", reportId,
            blankToNull(request.resolution()), now);
    }

    private VisiblePost visible(AuthenticatedUser user, UUID postId) {
        return repository.visiblePost(postId, user.internalId()).orElseThrow(() -> BusinessException.notFound("Post não encontrado."));
    }

    private Counts counts(long postId) {
        Map<String, Long> c = repository.counts(postId);
        return new Counts(c.get("onTarget"), c.get("eliminated"), c.get("comments"), c.get("shares"), c.get("saves"));
    }

    private static String excerpt(String body) {
        return body.length() > 120 ? body.substring(0, 117) + "…" : body;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
