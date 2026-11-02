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
import br.com.operadorzero.recon.ReconDtos.CreateReconRequest;
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
}
