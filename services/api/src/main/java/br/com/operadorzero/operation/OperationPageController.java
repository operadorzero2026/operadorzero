package br.com.operadorzero.operation;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operation.OperationPageRepository.History;
import br.com.operadorzero.operation.OperationPageRepository.Page;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Pagina permanente da operacao (link compartilhado) e "Operacoes realizadas" do perfil. */
@RestController
public class OperationPageController {
    public record PageResponse(UUID id, String name, String description, String briefing, String coverUrl, LocalDate date,
                               LocalTime presentationTime, LocalTime startTime, LocalTime endTime, String fieldName, String city,
                               String stateCode, String directionsUrl, String modality, String customModality, BigDecimal price,
                               String status, Instant startsAt, Instant endsAt, boolean finished, boolean registrationOpen,
                               String registrationModel, String organizerCallsign, String organizerUsername, long participantCount,
                               String shareUrl) {}

    public record HistoryItem(UUID id, String name, String coverUrl, LocalDate date, String organizerCallsign, String teamName,
                              String primaryRole, boolean medic, boolean radio) {}

    private final OperationPageRepository repository;
    private final String baseUrl;
    private final Clock clock;

    @Autowired
    public OperationPageController(OperationPageRepository repository, @Value("${app.auth.frontend-base-url:http://localhost:4174}") String baseUrl) {
        this(repository, baseUrl, Clock.systemUTC());
    }

    OperationPageController(OperationPageRepository repository, String baseUrl, Clock clock) {
        this.repository = repository;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.clock = clock;
    }

    @GetMapping("/api/public/operations/{id}")
    ResponseEntity<PageResponse> page(@PathVariable UUID id) {
        return repository.page(id).map(page -> ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(response(page)))
            .orElse(ResponseEntity.notFound().build());
    }

    /** Capa publica (previa do WhatsApp e pagina compartilhada). O endereco muda com a versao, entao pode ficar em cache. */
    @GetMapping("/api/public/operations/{id}/cover")
    ResponseEntity<byte[]> cover(@PathVariable UUID id) {
        return repository.cover(id).map(cover -> ResponseEntity.ok().contentType(MediaType.parseMediaType(cover.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic()).header("X-Content-Type-Options", "nosniff").body(cover.data()))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/operators/{username}/operations")
    List<HistoryItem> history(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String username) {
        return repository.history(username, user.internalId()).stream().map(this::history).toList();
    }

    PageResponse response(Page p) {
        boolean finished = "FINISHED".equals(p.status()) || (p.endsAt() != null && p.endsAt().isBefore(clock.instant()));
        return new PageResponse(p.id(), p.name(), p.description(), p.briefing(), coverUrl(p.id(), p.hasCover(), p.coverVersion()), p.date(),
            p.presentationTime(), p.startTime(), p.endTime(), p.fieldName(), p.city(), p.stateCode(), directions(p), p.modality(),
            p.customModality(), p.price(), p.status(), p.startsAt(), p.endsAt(), finished, p.registrationOpen() && !finished,
            p.registrationModel(), p.organizerCallsign(), p.organizerUsername(), p.participantCount(), baseUrl + "/c/" + p.id());
    }

    String absoluteCover(Page p) {
        String path = coverUrl(p.id(), p.hasCover(), p.coverVersion());
        return path == null ? null : baseUrl + path;
    }

    private HistoryItem history(History h) {
        return new HistoryItem(h.id(), h.name(), coverUrl(h.id(), h.hasCover(), h.coverVersion()), h.date(), h.organizerCallsign(),
            h.teamName(), h.primaryRole(), h.medic(), h.radio());
    }

    private static String coverUrl(UUID id, boolean hasCover, long version) {
        return hasCover ? "/api/public/operations/" + id + "/cover?v=" + version : null;
    }

    private static String directions(Page p) {
        if (p.locationUrl() != null && p.locationUrl().startsWith("https://")) return p.locationUrl();
        if (p.latitude() != null && p.longitude() != null) {
            return "https://www.google.com/maps/search/?api=1&query=" + p.latitude().toPlainString() + "," + p.longitude().toPlainString();
        }
        return null;
    }
}
