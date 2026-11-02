package br.com.operadorzero.feed;

import br.com.operadorzero.feed.FeedPostRepository.MediaForPost;
import br.com.operadorzero.feed.FeedPostRepository.PostState;
import br.com.operadorzero.feed.FeedPostRepository.UserRef;
import br.com.operadorzero.gamification.XpService;
import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.media.MediaProcessedEvent;
import br.com.operadorzero.notification.Notifier;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.ratelimit.UserActionLimiter;
import br.com.operadorzero.shared.web.BusinessException;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedPostService {
    public record CreatePostRequest(@NotNull UUID mediaId, @Size(max = 2200) String caption, UUID fieldId, UUID teamId,
                                    @Size(max = 10) List<@Size(max = 40) String> taggedUsernames, Boolean publicShare,
                                    @NotNull UUID idempotencyKey) {}

    public record CreatePostResponse(UUID id, String status) {}

    private final FeedPostRepository repository;
    private final XpService xp;
    private final Notifier notifier;
    private final UserActionLimiter limiter;
    private final AuditEventRepository audit;
    private final Clock clock;

    @Autowired
    public FeedPostService(FeedPostRepository repository, XpService xp, Notifier notifier, UserActionLimiter limiter, AuditEventRepository audit) {
        this(repository, xp, notifier, limiter, audit, Clock.systemUTC());
    }

    FeedPostService(FeedPostRepository repository, XpService xp, Notifier notifier, UserActionLimiter limiter, AuditEventRepository audit, Clock clock) {
        this.repository = repository;
        this.xp = xp;
        this.notifier = notifier;
        this.limiter = limiter;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public CreatePostResponse create(AuthenticatedUser user, CreatePostRequest request) {
        var existing = repository.byIdempotency(user.internalId(), request.idempotencyKey());
        if (existing.isPresent()) return new CreatePostResponse(existing.get().publicId(), existing.get().status());
        limiter.check("feed-post", user.internalId(), 10, Duration.ofHours(1));
        MediaForPost media = repository.lockMedia(request.mediaId(), user.internalId())
            .orElseThrow(() -> BusinessException.notFound("Mídia não encontrada."));
        if (!"POST".equals(media.purpose())) throw BusinessException.badRequest("MEDIA_PURPOSE", "Esta mídia não é de um post.");
        if (media.attached()) throw BusinessException.conflict("MEDIA_IN_USE", "Esta mídia já foi publicada.");
        if (List.of("UPLOADING", "FAILED", "DELETED").contains(media.status())) {
            throw BusinessException.badRequest("MEDIA_NOT_USABLE", "O envio do arquivo não foi concluído.");
        }
        String caption = request.caption() == null || request.caption().isBlank() ? null : request.caption().strip();
        Long fieldId = request.fieldId() == null ? null : repository.activeField(request.fieldId())
            .orElseThrow(() -> BusinessException.badRequest("FIELD_NOT_FOUND", "Campo não encontrado."));
        Long teamId = request.teamId() == null ? null : repository.ownActiveTeam(request.teamId(), user.internalId())
            .orElseThrow(() -> BusinessException.forbidden("Você só pode marcar a sua equipe."));
        List<UserRef> mentioned = repository.reachableUsers(FeedText.mentions(caption), user.internalId());
        List<String> taggedNames = request.taggedUsernames() == null ? List.of()
            : request.taggedUsernames().stream().map(name -> name.strip().replaceFirst("^@", "").toLowerCase(Locale.ROOT)).distinct().toList();
        List<UserRef> tagged = repository.reachableUsers(taggedNames, user.internalId());
        Instant now = clock.instant();
        boolean ready = "READY".equals(media.status());
        PostState post = repository.insert(user.internalId(), caption, fieldId, teamId,
            request.publicShare() == null || request.publicShare(), ready, request.idempotencyKey(), now);
        repository.attach(post.id(), media.id(), FeedText.hashtags(caption),
            mentioned.stream().map(UserRef::id).toList(), tagged.stream().map(UserRef::id).toList());
        audit.record(user.internalId(), "FEED_POST_CREATED", "FEED_POST", post.publicId(), null, now);
        if (ready) onPublished(post);
        return new CreatePostResponse(post.publicId(), post.status());
    }

    @Transactional
    public void delete(AuthenticatedUser user, UUID postId) {
        PostState post = repository.lockOwn(postId, user.internalId()).orElseThrow(() -> BusinessException.notFound("Post não encontrado."));
        if ("DELETED".equals(post.status())) return;
        Instant now = clock.instant();
        repository.markDeleted(post.id(), now);
        xp.reverse(post.authorUserId(), "POST_PUBLISHED", "FEED_POST", post.id(), null);
        audit.record(user.internalId(), "FEED_POST_DELETED", "FEED_POST", post.publicId(), null, now);
    }

    /** Roda na transacao do processador: o post so aparece no feed quando o video ja tem todas as versoes. */
    @EventListener
    public void onMediaProcessed(MediaProcessedEvent event) {
        Instant now = clock.instant();
        if (event.ready()) {
            repository.publishWaitingFor(event.assetId(), now).forEach(post -> {
                onPublished(post);
                notifier.toUser(post.authorUserId(), "FEED_POST_PUBLISHED", "Seu post foi publicado", null, "Feed");
            });
        } else {
            repository.failWaitingFor(event.assetId(), now).forEach(post -> notifier.toUser(post.authorUserId(), "FEED_POST_FAILED",
                "Não foi possível publicar seu post", failureMessage(event.errorCode()), "Feed"));
        }
    }

    private void onPublished(PostState post) {
        xp.award(post.authorUserId(), "POST_PUBLISHED", "FEED_POST", post.id(), null);
        List<Long> notify = new ArrayList<>(repository.notifiedUsers(post.id()));
        if (notify.isEmpty()) return;
        String callsign = repository.callsign(post.authorUserId());
        notify.forEach(userId -> notifier.toUser(userId, "FEED_MENTION", callsign + " marcou você em um post", null, "Feed"));
    }

    static String failureMessage(String code) {
        return switch (code == null ? "" : code) {
            case "VIDEO_TOO_LONG" -> "O vídeo passou do tempo máximo permitido.";
            case "ASPECT_NOT_ALLOWED" -> "Use fotos nas proporções 4:5, 1:1 ou 9:16.";
            case "IMAGE_TOO_LARGE" -> "A foto tem resolução grande demais.";
            case "UNSUPPORTED_TYPE", "NO_VIDEO_STREAM", "PROBE_FAILED", "INVALID_VIDEO" -> "O arquivo não é uma foto ou vídeo válido.";
            default -> "Tente enviar o arquivo novamente.";
        };
    }
}
