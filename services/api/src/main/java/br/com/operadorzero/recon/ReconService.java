package br.com.operadorzero.recon;

import br.com.operadorzero.feed.FeedDtos.MediaResponse;
import br.com.operadorzero.feed.FeedRepository.MediaRow;
import br.com.operadorzero.feed.FeedService;
import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.media.MediaProcessedEvent;
import br.com.operadorzero.notification.Notifier;
import br.com.operadorzero.recon.ReconDtos.AuthorRecons;
import br.com.operadorzero.recon.ReconDtos.CreateReconRequest;
import br.com.operadorzero.recon.ReconDtos.DossierDetail;
import br.com.operadorzero.recon.ReconDtos.DossierSummary;
import br.com.operadorzero.recon.ReconDtos.ReconBar;
import br.com.operadorzero.recon.ReconDtos.ReconCreated;
import br.com.operadorzero.recon.ReconDtos.ReconItem;
import br.com.operadorzero.recon.ReconRepository.ReconRow;
import br.com.operadorzero.recon.ReconRepository.ReconState;
import br.com.operadorzero.shared.ratelimit.UserActionLimiter;
import br.com.operadorzero.shared.web.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recons: somem em 24 h, visiveis so para quem segue o autor; um Recon guardado num Dossie fica no perfil. */
@Service
public class ReconService {
    static final Duration LIFETIME = Duration.ofHours(24);
    static final int MAX_DOSSIERS = 20;
    static final int MAX_DOSSIER_RECONS = 100;

    private final ReconRepository repository;
    private final FeedService media;
    private final Notifier notifier;
    private final UserActionLimiter limiter;
    private final Clock clock;

    @Autowired
    public ReconService(ReconRepository repository, FeedService media, Notifier notifier, UserActionLimiter limiter) {
        this(repository, media, notifier, limiter, Clock.systemUTC());
    }

    ReconService(ReconRepository repository, FeedService media, Notifier notifier, UserActionLimiter limiter, Clock clock) {
        this.repository = repository;
        this.media = media;
        this.notifier = notifier;
        this.limiter = limiter;
        this.clock = clock;
    }

    @Transactional
    public ReconCreated create(AuthenticatedUser user, CreateReconRequest request) {
        limiter.check("recon", user.internalId(), 30, Duration.ofDays(1));
        var asset = repository.lockMedia(request.mediaId(), user.internalId())
            .orElseThrow(() -> BusinessException.notFound("Mídia não encontrada."));
        if (!"RECON".equals(asset.purpose())) throw BusinessException.badRequest("MEDIA_PURPOSE", "Esta mídia não é de um Recon.");
        if (asset.used()) throw BusinessException.conflict("MEDIA_IN_USE", "Este Recon já foi publicado.");
        if (List.of("UPLOADING", "FAILED", "DELETED").contains(asset.status())) {
            throw BusinessException.badRequest("MEDIA_NOT_USABLE", "O envio do arquivo não foi concluído.");
        }
        String overlay = request.overlayText() == null || request.overlayText().isBlank() ? null : request.overlayText().strip();
        Instant now = clock.instant();
        ReconState recon = repository.insert(user.internalId(), asset.id(), overlay, "READY".equals(asset.status()), now, now.plus(LIFETIME));
        return new ReconCreated(recon.publicId(), recon.status());
    }

    @Transactional(readOnly = true)
    public ReconBar bar(AuthenticatedUser user) {
        Instant now = clock.instant();
        return new ReconBar(repository.bar(user.internalId(), now), repository.ownProcessing(user.internalId(), now));
    }

    @Transactional(readOnly = true)
    public AuthorRecons ofAuthor(AuthenticatedUser user, String username) {
        var author = repository.author(username).orElseThrow(() -> BusinessException.notFound("Operador não encontrado."));
        List<ReconItem> items = repository.activeOf(author.id(), user.internalId(), clock.instant()).stream().map(this::item).toList();
        return new AuthorRecons(author.username(), author.callsign(), author.rankCode(), author.id() == user.internalId(), items);
    }

    /** 410 quando o Recon ja saiu do ar: o visualizador mostra "Recon encerrado". */
    @Transactional
    public void markViewed(AuthenticatedUser user, UUID reconId) {
        if (!repository.markViewed(reconId, user.internalId(), clock.instant())) throw ended();
    }

    @Transactional
    public void delete(AuthenticatedUser user, UUID reconId) {
        ReconState recon = repository.lockOwn(reconId, user.internalId()).orElseThrow(() -> BusinessException.notFound("Recon não encontrado."));
        if (!"DELETED".equals(recon.status())) repository.markDeleted(recon.id());
    }

    @EventListener
    public void onMediaProcessed(MediaProcessedEvent event) {
        if (!"RECON".equals(event.purpose())) return;
        if (event.ready()) {
            repository.activateFor(event.assetId(), clock.instant());
        } else {
            repository.failFor(event.assetId()).forEach(recon -> notifier.toUser(recon.authorUserId(), "RECON_FAILED",
                "Não foi possível publicar seu Recon", "Tente enviar o arquivo novamente.", "Feed"));
        }
    }

    // ---- Dossies ----

    @Transactional
    public DossierSummary createDossier(AuthenticatedUser user, String title) {
        if (repository.dossierCount(user.internalId()) >= MAX_DOSSIERS) {
            throw BusinessException.conflict("DOSSIER_LIMIT", "Você já tem " + MAX_DOSSIERS + " Dossiês.");
        }
        UUID id = repository.createDossier(user.internalId(), title.strip(), clock.instant());
        return new DossierSummary(id, title.strip(), null, 0);
    }

    @Transactional
    public void addToDossier(AuthenticatedUser user, UUID dossierId, UUID reconId) {
        long dossier = repository.ownDossier(dossierId, user.internalId()).orElseThrow(() -> BusinessException.notFound("Dossiê não encontrado."));
        long recon = repository.archivableRecon(reconId, user.internalId())
            .orElseThrow(() -> BusinessException.badRequest("RECON_NOT_ARCHIVABLE", "Só é possível guardar seus Recons que ainda têm mídia."));
        if (repository.dossierSize(dossier) >= MAX_DOSSIER_RECONS) {
            throw BusinessException.conflict("DOSSIER_FULL", "Este Dossiê já tem " + MAX_DOSSIER_RECONS + " Recons.");
        }
        repository.addToDossier(dossier, recon, clock.instant());
    }

    @Transactional
    public void removeFromDossier(AuthenticatedUser user, UUID dossierId, UUID reconId) {
        long dossier = repository.ownDossier(dossierId, user.internalId()).orElseThrow(() -> BusinessException.notFound("Dossiê não encontrado."));
        repository.removeFromDossier(dossier, reconId);
    }

    @Transactional
    public void deleteDossier(AuthenticatedUser user, UUID dossierId) {
        repository.deleteDossier(repository.ownDossier(dossierId, user.internalId())
            .orElseThrow(() -> BusinessException.notFound("Dossiê não encontrado.")));
    }

    @Transactional(readOnly = true)
    public List<DossierSummary> dossiersOf(AuthenticatedUser user, String username) {
        var owner = repository.author(username).orElseThrow(() -> BusinessException.notFound("Operador não encontrado."));
        return repository.dossiersOf(owner.id(), user.internalId()).stream().map(row -> {
            String cover = null;
            if (row.coverPrefix() != null) {
                MediaResponse response = media.media(new MediaRow(null, "IMAGE", 0, 0, null, row.coverPrefix(), row.coverRenditions(), row.coverKey(), null));
                cover = response.posterUrl() != null ? response.posterUrl() : response.images().isEmpty() ? null : response.images().getFirst().url();
            }
            return new DossierSummary(row.id(), row.title(), cover, row.count());
        }).toList();
    }

    @Transactional(readOnly = true)
    public DossierDetail dossier(AuthenticatedUser user, UUID dossierId) {
        var header = repository.dossier(dossierId, user.internalId()).orElseThrow(() -> BusinessException.notFound("Dossiê não encontrado."));
        return new DossierDetail(header.publicId(), header.title(), header.ownerUsername(), header.ownerId() == user.internalId(),
            repository.dossierRecons(header.id()).stream().map(this::item).toList());
    }

    private ReconItem item(ReconRow row) {
        return new ReconItem(row.publicId(), row.overlayText(), media.media(row.media()), row.publishedAt(), row.expiresAt(), row.seen());
    }

    private static BusinessException ended() {
        return BusinessException.gone("RECON_ENDED", "Recon encerrado.");
    }
}
