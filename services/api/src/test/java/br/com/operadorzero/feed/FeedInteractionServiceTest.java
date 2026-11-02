package br.com.operadorzero.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.operadorzero.feed.FeedInteractionRepository.CommentRef;
import br.com.operadorzero.feed.FeedInteractionRepository.ReportTarget;
import br.com.operadorzero.feed.FeedInteractionRepository.VisiblePost;
import br.com.operadorzero.feed.FeedInteractionService.CommentRequest;
import br.com.operadorzero.feed.FeedInteractionService.ResolveRequest;
import br.com.operadorzero.gamification.XpService;
import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.notification.Notifier;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.ratelimit.UserActionLimiter;
import br.com.operadorzero.shared.web.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FeedInteractionServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    private final FeedInteractionRepository repository = mock(FeedInteractionRepository.class);
    private final FeedPostRepository posts = mock(FeedPostRepository.class);
    private final XpService xp = mock(XpService.class);
    private final Notifier notifier = mock(Notifier.class);
    private final FeedInteractionService service = new FeedInteractionService(repository, posts, xp, notifier, mock(UserActionLimiter.class),
        mock(AuditEventRepository.class), "https://operadorzero.com.br/", Clock.fixed(NOW, ZoneOffset.UTC));
    private final AuthenticatedUser viewer = new AuthenticatedUser(7L, UUID.randomUUID(), "op@example.test", "op", "Op", "OP", List.of("USER"));
    private final UUID postId = UUID.randomUUID();

    @BeforeEach
    void post() {
        when(repository.visiblePost(postId, 7L)).thenReturn(Optional.of(new VisiblePost(11L, postId, 9L, true)));
        when(repository.counts(anyLong())).thenReturn(Map.of("onTarget", 1L, "eliminated", 0L, "comments", 0L, "shares", 0L, "saves", 0L));
        when(posts.callsign(7L)).thenReturn("OP");
    }

    @Test
    void reactingCountsOnceAndGivesXpToTheAuthorButNotForOwnPosts() {
        when(repository.addReaction(11L, 7L, "ON_TARGET", NOW)).thenReturn(true, false);
        service.react(viewer, postId, "ON_TARGET", true);
        service.react(viewer, postId, "ON_TARGET", true);
        verify(repository, times(1)).bump(11L, "on_target", 1, NOW);
        verify(xp, times(1)).award(9L, "REACTION_RECEIVED", "FEED_REACTION", 11L, 7L);

        when(repository.removeReaction(11L, 7L, "ON_TARGET")).thenReturn(true);
        service.react(viewer, postId, "ON_TARGET", false);
        verify(repository).bump(11L, "on_target", -1, NOW);

        UUID own = UUID.randomUUID();
        when(repository.visiblePost(own, 7L)).thenReturn(Optional.of(new VisiblePost(12L, own, 7L, true)));
        when(repository.addReaction(12L, 7L, "ELIMINATED", NOW)).thenReturn(true);
        service.react(viewer, own, "ELIMINATED", true);
        verify(xp, never()).award(eq(7L), anyString(), anyString(), eq(12L), any());
    }

    @Test
    void unknownReactionsAndHiddenPostsAreRejected() {
        assertThatThrownBy(() -> service.react(viewer, postId, "LIKE", true))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("INVALID_REACTION"));
        UUID blocked = UUID.randomUUID();
        when(repository.visiblePost(blocked, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.react(viewer, blocked, "ON_TARGET", true)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.comment(viewer, blocked, new CommentRequest("oi", null, UUID.randomUUID()))).isInstanceOf(BusinessException.class);
    }

    @Test
    void replyingToAReplyStaysOneLevelAndEachPersonIsNotifiedOnce() {
        UUID replyId = UUID.randomUUID();
        // A resposta 21 pertence ao comentario 20, escrito pelo autor do post (9).
        when(repository.comment(replyId)).thenReturn(Optional.of(new CommentRef(21L, replyId, 11L, 9L, 9L, 20L)));
        UUID created = UUID.randomUUID();
        when(repository.insertComment(eq(11L), eq(7L), eq(20L), anyString(), any(), eq(NOW))).thenReturn(created);
        when(repository.commentId(created)).thenReturn(Optional.of(30L));
        when(posts.reachableUsers(List.of("bravo"), 7L)).thenReturn(List.of(new FeedPostRepository.UserRef(5L, "bravo")));

        service.comment(viewer, postId, new CommentRequest("  Boa! @bravo  ", replyId, UUID.randomUUID()));

        verify(repository).insertComment(eq(11L), eq(7L), eq(20L), eq("Boa! @bravo"), any(), eq(NOW));
        verify(notifier, times(1)).toUser(eq(9L), anyString(), anyString(), any(), eq("Feed"));
        verify(notifier).toUser(eq(5L), eq("FEED_MENTION"), anyString(), any(), eq("Feed"));
        verify(xp).award(7L, "COMMENT_PUBLISHED", "FEED_COMMENT", 30L, 9L);
        verify(repository).bump(11L, "comments", 1, NOW);
    }

    @Test
    void onlyTheCommentAuthorOrThePostOwnerCanDeleteAComment() {
        UUID commentId = UUID.randomUUID();
        when(repository.comment(commentId)).thenReturn(Optional.of(new CommentRef(21L, commentId, 11L, 3L, 4L, null)));
        assertThatThrownBy(() -> service.deleteComment(viewer, commentId))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("ACCESS_DENIED"));

        when(repository.comment(commentId)).thenReturn(Optional.of(new CommentRef(21L, commentId, 11L, 3L, 7L, null)));
        when(repository.deleteComment(21L, NOW)).thenReturn(List.of(new FeedInteractionRepository.DeletedComment(21L, 3L),
            new FeedInteractionRepository.DeletedComment(22L, 8L), new FeedInteractionRepository.DeletedComment(23L, 7L)));
        service.deleteComment(viewer, commentId);
        verify(repository).bump(11L, "comments", -3, NOW);
        // O XP do comentario e de cada resposta apagada junto e estornado.
        verify(xp).reverse(3L, "COMMENT_PUBLISHED", "FEED_COMMENT", 21L, 7L);
        verify(xp).reverse(8L, "COMMENT_PUBLISHED", "FEED_COMMENT", 22L, 7L);
    }

    @Test
    void removingReportedContentSuspendsItAndClosesItsReports() {
        UUID reportId = UUID.randomUUID();
        ReportTarget target = new ReportTarget(1L, 11L, null, null);
        when(repository.openReport(reportId)).thenReturn(Optional.of(target));
        AuthenticatedUser moderator = new AuthenticatedUser(2L, UUID.randomUUID(), "mod@example.test", "mod", "Mod", "MOD", List.of("MODERATOR"));

        service.resolve(moderator, reportId, new ResolveRequest("REMOVE", "Conteúdo violento"));

        verify(repository).suspendTarget(target, NOW);
        verify(repository).resolveReports(target, 2L, "ACTIONED", "Conteúdo violento", NOW);
    }

    @Test
    void sharingCountsAndReturnsThePublicLink() {
        assertThat(service.share(viewer, postId, "WHATSAPP").url()).isEqualTo("https://operadorzero.com.br/p/" + postId);
        verify(repository).share(11L, 7L, "WHATSAPP", NOW);
        verify(repository).bump(11L, "shares", 1, NOW);
    }

    @Test
    void watchTimeIsCappedSoAForgottenTabDoesNotInflateTheRanking() {
        when(repository.videoDurationMs(11L)).thenReturn(Optional.of(10_000));
        when(repository.recordView(11L, 7L, 30_000L, true, NOW)).thenReturn(new FeedInteractionRepository.ViewDelta(true, true));
        service.view(viewer, postId, new FeedInteractionService.ViewRequest(3_600_000L, true));
        verify(repository).addViewMetrics(11L, true, 30_000L, true, NOW);

        // Foto (sem duracao): limite de 60 s.
        when(repository.videoDurationMs(11L)).thenReturn(Optional.empty());
        when(repository.recordView(11L, 7L, 60_000L, false, NOW)).thenReturn(new FeedInteractionRepository.ViewDelta(false, false));
        service.view(viewer, postId, new FeedInteractionService.ViewRequest(90_000L, false));
        verify(repository).addViewMetrics(11L, false, 60_000L, false, NOW);
    }

    @Test
    void followingNotifiesOnlyOnTheFirstFollow() {
        when(repository.followable("bravo", 7L)).thenReturn(Optional.of(new FeedInteractionRepository.UserRef(5L, "bravo")));
        when(repository.follow(7L, 5L, NOW)).thenReturn(true, false);
        when(repository.followStats("bravo", 7L)).thenReturn(Map.of("followers", 1L, "following", 0L, "follows", true));
        service.follow(viewer, "bravo", true);
        service.follow(viewer, "bravo", true);
        verify(notifier, times(1)).toUser(eq(5L), eq("NEW_FOLLOWER"), anyString(), any(), eq("Perfil"));
    }
}
