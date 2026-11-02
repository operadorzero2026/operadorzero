package br.com.operadorzero.operation;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operation.CallUpRepository.CallUpRow;
import br.com.operadorzero.operation.CallUpRepository.Team;
import br.com.operadorzero.shared.web.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Convocacao: card de jogo no feed, com vagas e o botao ALISTAR (que usa a inscricao ja existente). */
@Service
public class CallUpService {
    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    /** A partir de quantas vagas restantes o card destaca "Ultimas vagas". */
    static final int LAST_SPOTS = 3;
    static final int MAX_LIMIT = 20;

    public record CallUp(UUID id, String name, LocalDate date, LocalTime presentationTime, LocalTime startTime, String city, String stateCode,
                         String fieldName, String directionsUrl, String modality, String customModality, String gameSize, String status,
                         long occupied, Integer limit, Integer remaining, boolean lastSpots, BigDecimal price, boolean approvalRequired,
                         boolean waitingListEnabled, String viewerStatus, List<Team> teams, String shareUrl) {}

    public record CallUpPage(List<CallUp> items) {}

    private final CallUpRepository repository;
    private final String baseUrl;
    private final Clock clock;

    @Autowired
    public CallUpService(CallUpRepository repository, @Value("${app.auth.frontend-base-url:http://localhost:4174}") String baseUrl) {
        this(repository, baseUrl, Clock.systemUTC());
    }

    CallUpService(CallUpRepository repository, String baseUrl, Clock clock) {
        this.repository = repository;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CallUpPage forFeed(AuthenticatedUser user, Integer limit) {
        int size = limit == null ? 10 : Math.max(1, Math.min(MAX_LIMIT, limit));
        return new CallUpPage(map(repository.ranked(user.internalId(), today(), size), true));
    }

    @Transactional(readOnly = true)
    public CallUp one(AuthenticatedUser user, UUID operationId) {
        return single(operationId, user == null ? -1 : user.internalId(), user != null)
            .orElseThrow(() -> BusinessException.notFound("Convocação não encontrada."));
    }

    public Optional<CallUp> single(UUID operationId, long viewer, boolean withViewer) {
        return map(repository.one(operationId, viewer, today()), withViewer).stream().findFirst();
    }

    public String shareUrl(UUID operationId) {
        return baseUrl + "/c/" + operationId;
    }

    List<CallUp> map(List<CallUpRow> rows, boolean withViewer) {
        Map<Long, List<Team>> teams = repository.teams(rows.stream().map(CallUpRow::id).toList());
        return rows.stream().map(row -> {
            Integer remaining = row.limit() == null ? null : (int) Math.max(0, row.limit() - row.occupied());
            boolean lastSpots = remaining != null && remaining > 0 && remaining <= LAST_SPOTS;
            return new CallUp(row.publicId(), row.name(), row.date(), row.presentationTime(), row.startTime(), row.city(), row.stateCode(),
                row.fieldName(), directions(row), row.modality(), row.customModality(), row.gameSize(), row.status(), row.occupied(),
                row.limit(), remaining, lastSpots, row.price(), row.approvalRequired(), row.waitingListEnabled(),
                withViewer ? row.viewerStatus() : null, teams.getOrDefault(row.id(), List.of()), shareUrl(row.publicId()));
        }).toList();
    }

    /** Link do GPS cadastrado no campo ou, se houver coordenadas, uma busca no mapa do celular. */
    static String directions(CallUpRow row) {
        if (row.locationUrl() != null && row.locationUrl().startsWith("https://")) return row.locationUrl();
        if (row.latitude() != null && row.longitude() != null) {
            return "https://www.google.com/maps/search/?api=1&query=" + row.latitude().toPlainString() + "," + row.longitude().toPlainString();
        }
        return null;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(BRASILIA));
    }
}
