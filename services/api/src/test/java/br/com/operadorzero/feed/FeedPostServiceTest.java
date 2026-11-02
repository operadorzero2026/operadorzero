package br.com.operadorzero.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.operadorzero.feed.FeedPostRepository.MediaForPost;
import br.com.operadorzero.feed.FeedPostRepository.PostState;
import br.com.operadorzero.feed.FeedPostRepository.UserRef;
import br.com.operadorzero.feed.FeedPostService.CreatePostRequest;
import br.com.operadorzero.gamification.XpService;
import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.media.MediaProcessedEvent;
import br.com.operadorzero.notification.Notifier;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.ratelimit.UserActionLimiter;
import br.com.operadorzero.shared.web.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FeedPostServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    private final FeedPostRepository repository = mock(FeedPostRepository.class);
    private final XpService xp = mock(XpService.class);
    private final Notifier notifier = mock(Notifier.class);
    private final FeedPostService service = new FeedPostService(repository, xp, notifier, mock(UserActionLimiter.class),
        mock(AuditEventRepository.class), Clock.fixed(NOW, ZoneOffset.UTC));
    private final AuthenticatedUser user = new AuthenticatedUser(7L, UUID.randomUUID(), "op@example.test", "op", "Op", "OP", List.of("USER"));
    private final UUID mediaId = UUID.randomUUID();

    @Test
    void aReadyMediaIsPublishedAtOnceWithXpHashtagsAndMentionNotifications() {
        when(repository.lockMedia(mediaId, 7L)).thenReturn(Optional.of(new MediaForPost(3L, "POST", "READY", false)));
        when(repository.reachableUsers(List.of("bravo"), 7L)).thenReturn(List.of(new UserRef(9L, "bravo")));
        PostState post = new PostState(11L, UUID.randomUUID(), 7L, "PUBLISHED");
        when(repository.insert(eq(7L), anyString(), any(), any(), eq(true), eq(true), any(), eq(NOW))).thenReturn(post);
        when(repository.notifiedUsers(11L)).thenReturn(List.of(9L));
        when(repository.callsign(7L)).thenReturn("ALFA");

        var response = service.create(user, request("Entrada no #CQB com @bravo. #Milsim #cqb"));

        assertThat(response.status()).isEqualTo("PUBLISHED");
        verify(repository).attach(11L, 3L, List.of("cqb", "milsim"), List.of(9L), List.of());
        verify(xp).award(7L, "POST_PUBLISHED", "FEED_POST", 11L, null);
        verify(notifier).toUser(eq(9L), eq("FEED_MENTION"), eq("ALFA marcou você em um post"), any(), eq("Feed"));
    }

    @Test
    void aVideoStillProcessingWaitsAndOnlyEarnsXpWhenPublished() {
        when(repository.lockMedia(mediaId, 7L)).thenReturn(Optional.of(new MediaForPost(3L, "POST", "PROCESSING", false)));
        PostState post = new PostState(11L, UUID.randomUUID(), 7L, "PROCESSING");
        when(repository.insert(eq(7L), any(), any(), any(), anyBoolean(), eq(false), any(), eq(NOW))).thenReturn(post);

        assertThat(service.create(user, request(null)).status()).isEqualTo("PROCESSING");
        verify(xp, never()).award(anyLong(), anyString(), anyString(), anyLong(), any());

        when(repository.publishWaitingFor(3L, NOW)).thenReturn(List.of(new PostState(11L, post.publicId(), 7L, "PUBLISHED")));
        service.onMediaProcessed(new MediaProcessedEvent(3L, mediaId, 7L, "POST", true, null));
        verify(xp).award(7L, "POST_PUBLISHED", "FEED_POST", 11L, null);
        verify(notifier).toUser(eq(7L), eq("FEED_POST_PUBLISHED"), any(), any(), eq("Feed"));
    }

    @Test
    void failedProcessingMarksThePostAndTellsTheAuthorWhy() {
        when(repository.failWaitingFor(3L, NOW)).thenReturn(List.of(new PostState(11L, UUID.randomUUID(), 7L, "FAILED")));
        service.onMediaProcessed(new MediaProcessedEvent(3L, mediaId, 7L, "POST", false, "VIDEO_TOO_LONG"));
        verify(notifier).toUser(7L, "FEED_POST_FAILED", "Não foi possível publicar seu post", "O vídeo passou do tempo máximo permitido.", "Feed");
    }

    @Test
    void rejectsMediaOfAnotherKindAlreadyUsedOrFromAnotherTeam() {
        when(repository.lockMedia(mediaId, 7L)).thenReturn(Optional.of(new MediaForPost(3L, "RECON", "READY", false)));
        assertThatThrownBy(() -> service.create(user, request(null)))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("MEDIA_PURPOSE"));

        when(repository.lockMedia(mediaId, 7L)).thenReturn(Optional.of(new MediaForPost(3L, "POST", "READY", true)));
        assertThatThrownBy(() -> service.create(user, request(null)))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("MEDIA_IN_USE"));

        when(repository.lockMedia(mediaId, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(user, request(null))).isInstanceOf(BusinessException.class);

        UUID otherTeam = UUID.randomUUID();
        when(repository.lockMedia(mediaId, 7L)).thenReturn(Optional.of(new MediaForPost(3L, "POST", "READY", false)));
        when(repository.ownActiveTeam(otherTeam, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(user, new CreatePostRequest(mediaId, null, null, otherTeam, null, null, UUID.randomUUID())))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("ACCESS_DENIED"));
        verify(repository, never()).insert(anyLong(), any(), any(), any(), anyBoolean(), anyBoolean(), any(), any());
    }

    @Test
    void retryingWithTheSameKeyReturnsTheSamePost() {
        UUID key = UUID.randomUUID();
        PostState existing = new PostState(11L, UUID.randomUUID(), 7L, "PUBLISHED");
        when(repository.byIdempotency(7L, key)).thenReturn(Optional.of(existing));
        var response = service.create(user, new CreatePostRequest(mediaId, null, null, null, null, null, key));
        assertThat(response.id()).isEqualTo(existing.publicId());
        verify(repository, never()).lockMedia(any(), anyLong());
    }

    @Test
    void extractsNormalizedHashtagsAndMentions() {
        assertThat(FeedText.hashtags("#Cêrco no #CQB e #cqb, email a#b")).containsExactly("cerco", "cqb");
        assertThat(FeedText.mentions("valeu @bravo.k9. e @Charlie! nao conta: x@y.com")).containsExactly("bravo.k9", "charlie");
    }

    private CreatePostRequest request(String caption) {
        return new CreatePostRequest(mediaId, caption, null, null, null, null, UUID.randomUUID());
    }
}
