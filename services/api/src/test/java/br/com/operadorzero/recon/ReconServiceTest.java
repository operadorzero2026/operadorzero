package br.com.operadorzero.recon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.operadorzero.feed.FeedService;
import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.media.MediaProcessedEvent;
import br.com.operadorzero.media.MediaStorage;
import br.com.operadorzero.notification.Notifier;
import br.com.operadorzero.recon.ReconComposition.Element;
import br.com.operadorzero.recon.ReconDtos.CreateReconRequest;
import br.com.operadorzero.recon.ReconRepository.Author;
import br.com.operadorzero.recon.ReconRepository.MentionTarget;
import br.com.operadorzero.recon.ReconRepository.ReconRow;
import br.com.operadorzero.recon.ReconRepository.SingleRecon;
import br.com.operadorzero.feed.FeedRepository.MediaRow;
import br.com.operadorzero.recon.ReconRepository.ExpiredMedia;
import br.com.operadorzero.recon.ReconRepository.MediaForRecon;
import br.com.operadorzero.recon.ReconRepository.ReconState;
import br.com.operadorzero.shared.ratelimit.UserActionLimiter;
import br.com.operadorzero.shared.web.BusinessException;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;

class ReconServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    private final ReconRepository repository = mock(ReconRepository.class);
    private final Notifier notifier = mock(Notifier.class);
    private final ReconService service = new ReconService(repository, mock(FeedService.class), notifier, mock(UserActionLimiter.class),
        Clock.fixed(NOW, ZoneOffset.UTC));
    private final AuthenticatedUser user = new AuthenticatedUser(7L, UUID.randomUUID(), "op@example.test", "op", "Op", "OP", List.of("USER"));
    private final UUID mediaId = UUID.randomUUID();

    @Test
    void aReconExpiresTwentyFourHoursAfterItWasCreated() {
        when(repository.lockMedia(mediaId, 7L)).thenReturn(Optional.of(new MediaForRecon(3L, "RECON", "READY", false)));
        when(repository.insert(anyLong(), anyLong(), any(), anyBoolean(), any(), any()))
            .thenReturn(new ReconState(1L, UUID.randomUUID(), 7L, "ACTIVE", NOW.plusSeconds(86_400)));

        service.create(user, new CreateReconRequest(mediaId, "  Patrulha no setor B  "));

        verify(repository).insert(7L, 3L, "Patrulha no setor B", true, NOW, NOW.plusSeconds(24 * 3600));
    }

    @Test
    void aVideoStillProcessingWaitsAndIsActivatedWhenReady() {
        when(repository.lockMedia(mediaId, 7L)).thenReturn(Optional.of(new MediaForRecon(3L, "RECON", "PROCESSING", false)));
        when(repository.insert(anyLong(), anyLong(), any(), anyBoolean(), any(), any()))
            .thenReturn(new ReconState(1L, UUID.randomUUID(), 7L, "PROCESSING", NOW.plusSeconds(86_400)));
        assertThat(service.create(user, new CreateReconRequest(mediaId, null)).status()).isEqualTo("PROCESSING");
        verify(repository).insert(eq(7L), eq(3L), eq(null), eq(false), any(), any());

        service.onMediaProcessed(new MediaProcessedEvent(3L, mediaId, 7L, "RECON", true, null));
        verify(repository).activateFor(3L, NOW);
        // Eventos de midia de post nao mexem em Recons.
        service.onMediaProcessed(new MediaProcessedEvent(4L, mediaId, 7L, "POST", true, null));
        verify(repository, never()).activateFor(4L, NOW);
    }

    @Test
    void failedProcessingNotifiesTheAuthor() {
        when(repository.failFor(3L)).thenReturn(List.of(new ReconState(1L, UUID.randomUUID(), 7L, "FAILED", NOW)));
        service.onMediaProcessed(new MediaProcessedEvent(3L, mediaId, 7L, "RECON", false, "VIDEO_TOO_LONG"));
        verify(notifier).toUser(eq(7L), eq("RECON_FAILED"), any(), any(), eq("Feed"));
    }

    @Test
    void postMediaCannotBecomeARecon() {
        when(repository.lockMedia(mediaId, 7L)).thenReturn(Optional.of(new MediaForRecon(3L, "POST", "READY", false)));
        assertThatThrownBy(() -> service.create(user, new CreateReconRequest(mediaId, null)))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("MEDIA_PURPOSE"));
    }

    @Test
    void viewingAReconThatLeftTheAirAnswersGone() {
        UUID reconId = UUID.randomUUID();
        when(repository.markViewed(reconId, 7L, NOW)).thenReturn(false);
        assertThatThrownBy(() -> service.markViewed(user, reconId)).isInstanceOfSatisfying(BusinessException.class, e -> {
            assertThat(e.status()).isEqualTo(HttpStatus.GONE);
            assertThat(e.code()).isEqualTo("RECON_ENDED");
        });
    }

    @Test
    void onlyOwnReconsWithMediaCanBeSavedAndDossiersAreLimited() {
        UUID dossierId = UUID.randomUUID();
        UUID reconId = UUID.randomUUID();
        when(repository.ownDossier(dossierId, 7L)).thenReturn(Optional.of(5L));
        when(repository.archivableRecon(reconId, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addToDossier(user, dossierId, reconId))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("RECON_NOT_ARCHIVABLE"));

        when(repository.ownDossier(dossierId, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addToDossier(user, dossierId, reconId)).isInstanceOf(BusinessException.class);

        when(repository.dossierCount(7L)).thenReturn(ReconService.MAX_DOSSIERS);
        assertThatThrownBy(() -> service.createDossier(user, "Operações"))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("DOSSIER_LIMIT"));
    }

    @Test
    void expiryJobDeletesOnlyTheMediaTheRepositoryMarksAsDisposable() throws IOException {
        MediaStorage storage = mock(MediaStorage.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(invocation -> repository.expire(NOW));
        when(repository.mediaToDiscard(200)).thenReturn(List.of(new ExpiredMedia(9L, "v/ab/00000000-0000-0000-0000-000000000000")));

        new ReconExpiryJob(repository, storage, transactions, Clock.fixed(NOW, ZoneOffset.UTC)).run();

        verify(repository).expire(NOW);
        verify(storage).delete("v/ab/00000000-0000-0000-0000-000000000000");
        verify(repository).markMediaDeleted(9L, NOW);
    }

    private static Element mention(String username) {
        return new Element("m1", "MENTION", 0.3, 0.6, 1.2, 10.0, 1, null, null, null, null, null, null, username, "ZERO", null, null, null, null);
    }

    @Test
    void mentionedOperatorsAreValidatedStoredAndNotifiedOnce() {
        UUID ghost = UUID.randomUUID();
        ReconState state = new ReconState(1L, UUID.randomUUID(), 7L, "ACTIVE", NOW.plusSeconds(86_400));
        when(repository.lockMedia(mediaId, 7L)).thenReturn(Optional.of(new MediaForRecon(3L, "RECON", "READY", false)));
        when(repository.mentionTargets(List.of("ghost"), 7L)).thenReturn(List.of(new MentionTarget(9L, ghost, "Ghost")));
        when(repository.insert(anyLong(), anyLong(), any(), anyBoolean(), any(), any())).thenReturn(state);
        when(repository.claimMentionNotifications(1L, NOW)).thenReturn(List.of(9L));
        when(repository.authorById(7L)).thenReturn(Optional.of(new Author(7L, "op", "Falcão", "RECRUTA")));

        service.create(user, new CreateReconRequest(mediaId, null, List.of(mention("@ghost")), true, null));

        verify(repository).saveComposition(eq(1L), org.mockito.ArgumentMatchers.contains("\"operatorId\":\"" + ghost + "\""), eq(true), eq(null));
        verify(repository).insertMentions(1L, List.of(9L), NOW);
        verify(notifier).toUserAt(9L, "RECON_MENTION", "Você foi mencionado em um Recon!", "Falcão mencionou você em um Recon. Toque para ver.",
            "Feed", "/recon/" + state.publicId());
    }

    @Test
    void blockedOrUnknownOperatorsCannotBeMentioned() {
        when(repository.lockMedia(mediaId, 7L)).thenReturn(Optional.of(new MediaForRecon(3L, "RECON", "READY", false)));
        when(repository.mentionTargets(List.of("fantasma"), 7L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(user, new CreateReconRequest(mediaId, null, List.of(mention("fantasma")), null, null)))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("MENTION_UNAVAILABLE"));
        verify(repository, never()).insert(anyLong(), anyLong(), any(), anyBoolean(), any(), any());
    }

    @Test
    void repeatingTheSamePublishReturnsTheExistingReconWithoutNotifyingAgain() {
        UUID existing = UUID.randomUUID();
        when(repository.lockMedia(mediaId, 7L)).thenReturn(Optional.of(new MediaForRecon(3L, "RECON", "READY", true, existing, "ACTIVE")));

        var created = service.create(user, new CreateReconRequest(mediaId, null, List.of(mention("ghost")), null, null));

        assertThat(created.id()).isEqualTo(existing);
        verify(repository, never()).insert(anyLong(), anyLong(), any(), anyBoolean(), any(), any());
        verify(notifier, never()).toUserAt(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void aVideoReconNotifiesMentionsOnlyWhenItGoesLive() {
        ReconState live = new ReconState(1L, UUID.randomUUID(), 7L, "ACTIVE", NOW.plusSeconds(86_400));
        when(repository.activateFor(3L, NOW)).thenReturn(List.of(live));
        when(repository.claimMentionNotifications(1L, NOW)).thenReturn(List.of(9L));
        when(repository.authorById(7L)).thenReturn(Optional.of(new Author(7L, "op", "Falcão", "RECRUTA")));

        service.onMediaProcessed(new MediaProcessedEvent(3L, mediaId, 7L, "RECON", true, null));

        verify(notifier).toUserAt(eq(9L), eq("RECON_MENTION"), any(), any(), eq("Feed"), eq("/recon/" + live.publicId()));
    }

    @Test
    void mentionOfAnOperatorNoLongerReachableIsShownButNotClickable() {
        UUID reconId = UUID.randomUUID();
        UUID ghost = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        String json = "[{\"id\":\"m1\",\"type\":\"MENTION\",\"x\":0.5,\"y\":0.5,\"scale\":1.0,\"rotation\":0.0,\"z\":1,"
            + "\"username\":\"ghost\",\"style\":\"ZERO\",\"operatorId\":\"" + ghost + "\"},"
            + "{\"id\":\"m2\",\"type\":\"MENTION\",\"x\":0.5,\"y\":0.7,\"scale\":1.0,\"rotation\":0.0,\"z\":2,"
            + "\"username\":\"sumido\",\"style\":\"MINIMAL\",\"operatorId\":\"" + gone + "\"}]";
        ReconRow row = new ReconRow(1L, reconId, null, NOW, NOW.plusSeconds(3600), false, json, false,
            new MediaRow(UUID.randomUUID(), "IMAGE", 1080, 1920, null, "i/ab/x", "[]", null, null));
        when(repository.single(reconId, 7L, NOW)).thenReturn(Optional.of(new SingleRecon(5L, row)));
        when(repository.authorById(5L)).thenReturn(Optional.of(new Author(5L, "autor", "Lobo", "RECRUTA")));
        when(repository.availableMentions(1L, 7L)).thenReturn(Map.of(ghost, "Ghost_Novo"));

        var result = service.one(user, reconId);

        List<Element> elements = result.items().getFirst().elements();
        assertThat(elements.get(0).available()).isTrue();
        assertThat(elements.get(0).username()).isEqualTo("Ghost_Novo");
        assertThat(elements.get(1).available()).isFalse();
        assertThat(elements.get(1).operatorId()).isNull();
    }

    @Test
    void aReconThatLeftTheAirCannotBeOpenedFromTheNotification() {
        UUID reconId = UUID.randomUUID();
        when(repository.single(reconId, 7L, NOW)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.one(user, reconId))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.GONE));
    }

}
