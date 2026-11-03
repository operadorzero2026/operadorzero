package br.com.operadorzero.recon;

import br.com.operadorzero.feed.FeedDtos.MediaResponse;
import br.com.operadorzero.feed.FeedRepository.MediaRow;
import br.com.operadorzero.feed.FeedService;
import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.media.MediaProcessedEvent;
import br.com.operadorzero.notification.Notifier;
import br.com.operadorzero.recon.ReconComposition.Element;
import br.com.operadorzero.recon.ReconDtos.AuthorRecons;
import br.com.operadorzero.recon.ReconDtos.CreateReconRequest;
import br.com.operadorzero.recon.ReconDtos.DossierDetail;
import br.com.operadorzero.recon.ReconDtos.DossierSummary;
import br.com.operadorzero.recon.ReconDtos.ReconBar;
import br.com.operadorzero.recon.ReconDtos.ReconCreated;
import br.com.operadorzero.recon.ReconDtos.ReconItem;
import br.com.operadorzero.recon.ReconRepository.MentionTarget;
import br.com.operadorzero.recon.ReconRepository.ReconRow;
import br.com.operadorzero.recon.ReconRepository.ReconState;
import br.com.operadorzero.shared.ratelimit.UserActionLimiter;
import br.com.operadorzero.shared.web.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(ReconService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<Element>> ELEMENTS = new TypeReference<>() {};

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
        // Mesma publicacao repetida (rede caiu antes da resposta): devolve o Recon ja criado, sem duplicar nem reavisar.
        if (asset.used() && asset.existingRecon() != null) return new ReconCreated(asset.existingRecon(), asset.existingStatus());
        if (asset.used()) throw BusinessException.conflict("MEDIA_IN_USE", "Este Recon já foi publicado.");
        if (List.of("UPLOADING", "FAILED", "DELETED").contains(asset.status())) {
            throw BusinessException.badRequest("MEDIA_NOT_USABLE", "O envio do arquivo não foi concluído.");
        }
        List<Element> elements = ReconComposition.normalize(request.elements());
        List<String> wanted = ReconComposition.mentions(elements);
        Map<String, MentionTarget> targets = repository.mentionTargets(wanted, user.internalId()).stream()
            .collect(Collectors.toMap(target -> target.username().toLowerCase(Locale.ROOT), Function.identity()));
        for (String username : wanted) {
            if (!targets.containsKey(username)) throw BusinessException.badRequest("MENTION_UNAVAILABLE", "Não é possível marcar @" + username + ".");
        }
        List<Element> stored = elements.stream().map(element -> {
            if (!"MENTION".equals(element.type())) return element;
            MentionTarget target = targets.get(element.username());
            return ReconComposition.withMention(element, target.username(), target.operatorId(), null);
        }).toList();
        Long dossier = request.dossierId() == null ? null : repository.ownDossier(request.dossierId(), user.internalId())
            .orElseThrow(() -> BusinessException.notFound("Dossiê não encontrado."));
        String overlay = request.overlayText() == null || request.overlayText().isBlank() ? null : request.overlayText().strip();
        Instant now = clock.instant();
        ReconState recon = repository.insert(user.internalId(), asset.id(), overlay, "READY".equals(asset.status()), now, now.plus(LIFETIME));
        if (!stored.isEmpty() || Boolean.TRUE.equals(request.audioMuted()) || dossier != null) {
            repository.saveComposition(recon.id(), stored.isEmpty() ? null : write(stored), Boolean.TRUE.equals(request.audioMuted()), dossier);
        }
        repository.insertMentions(recon.id(), targets.values().stream().map(MentionTarget::userId).toList(), now);
        if ("ACTIVE".equals(recon.status())) onActive(recon);
        return new ReconCreated(recon.publicId(), recon.status());
    }

    /** Recon especifico (link da notificacao de mencao). 410 quando saiu do ar ou nao e visivel para quem abriu. */
    @Transactional(readOnly = true)
    public AuthorRecons one(AuthenticatedUser user, UUID reconId) {
        var single = repository.single(reconId, user.internalId(), clock.instant())
            .orElseThrow(() -> BusinessException.gone("RECON_ENDED", "Este Recon não está mais disponível."));
        var author = repository.authorById(single.authorId()).orElseThrow(ReconService::ended);
        return new AuthorRecons(author.username(), author.callsign(), author.rankCode(), author.id() == user.internalId(),
            List.of(item(single.row(), user.internalId())));
    }

    /** Ao entrar no ar: avisa quem foi marcado (uma vez so) e guarda no Dossie escolhido. */
    private void onActive(ReconState recon) {
        List<Long> mentioned = repository.claimMentionNotifications(recon.id(), clock.instant());
        if (!mentioned.isEmpty()) {
            String callsign = repository.authorById(recon.authorUserId()).map(author -> author.callsign()).orElse("Um operador");
            mentioned.forEach(userId -> notifier.toUserAt(userId, "RECON_MENTION", "Você foi mencionado em um Recon!",
                callsign + " mencionou você em um Recon. Toque para ver.", "Feed", "/recon/" + recon.publicId()));
        }
        repository.autoDossier(recon.id()).ifPresent(dossier -> {
            if (repository.dossierSize(dossier) < MAX_DOSSIER_RECONS) repository.addToDossier(dossier, recon.id(), clock.instant());
        });
    }

    @Transactional(readOnly = true)
    public ReconBar bar(AuthenticatedUser user) {
        Instant now = clock.instant();
        return new ReconBar(repository.bar(user.internalId(), now), repository.ownProcessing(user.internalId(), now));
    }

    @Transactional(readOnly = true)
    public AuthorRecons ofAuthor(AuthenticatedUser user, String username) {
        var author = repository.author(username).orElseThrow(() -> BusinessException.notFound("Operador não encontrado."));
        List<ReconItem> items = repository.activeOf(author.id(), user.internalId(), clock.instant()).stream()
            .map(row -> item(row, user.internalId())).toList();
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
            repository.activateFor(event.assetId(), clock.instant()).stream().filter(recon -> "ACTIVE".equals(recon.status())).forEach(this::onActive);
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
            repository.dossierRecons(header.id()).stream().map(row -> item(row, user.internalId())).toList());
    }

    private ReconItem item(ReconRow row, long viewer) {
        return new ReconItem(row.publicId(), row.overlayText(), media.media(row.media()), row.publishedAt(), row.expiresAt(), row.seen(),
            elements(row, viewer), row.audioMuted());
    }

    /** Mencoes voltam com o username atual; available=false quando a conta saiu do ar ou ha bloqueio com quem esta vendo. */
    private List<Element> elements(ReconRow row, long viewer) {
        if (row.elementsJson() == null) return List.of();
        List<Element> parsed;
        try {
            parsed = JSON.readValue(row.elementsJson(), ELEMENTS);
        } catch (JsonProcessingException exception) {
            log.warn("recon_elements_unreadable recon={}", row.publicId(), exception);
            return List.of();
        }
        if (parsed.stream().noneMatch(element -> "MENTION".equals(element.type()))) return parsed;
        Map<UUID, String> available = repository.availableMentions(row.id(), viewer);
        List<Element> result = new ArrayList<>();
        for (Element element : parsed) {
            if (!"MENTION".equals(element.type())) { result.add(element); continue; }
            String current = element.operatorId() == null ? null : available.get(element.operatorId());
            result.add(current == null ? ReconComposition.withMention(element, element.username(), null, false)
                : ReconComposition.withMention(element, current, element.operatorId(), true));
        }
        return result;
    }

    private static String write(List<Element> elements) {
        try {
            return JSON.writeValueAsString(elements);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static BusinessException ended() {
        return BusinessException.gone("RECON_ENDED", "Recon encerrado.");
    }
}
