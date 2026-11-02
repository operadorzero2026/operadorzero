package br.com.operadorzero.gamification;

import br.com.operadorzero.feed.FeedDtos.FeedPostResponse;
import br.com.operadorzero.feed.FeedService;
import br.com.operadorzero.gamification.DecorationRepository.HonorRow;
import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.notification.Notifier;
import br.com.operadorzero.shared.web.BusinessException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Condecorar: cada operador tem uma cota semanal (padrao 3, em gamification_setting) que zera na segunda-feira
 * 00h de Brasilia e nao acumula. Nao vale condecorar o proprio post nem o mesmo post duas vezes.
 */
@Service
public class DecorationService {
    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    static final int DEFAULT_QUOTA = 3;
    static final int HONOR_LIMIT = 20;

    public record Balance(int quota, int used, int remaining, Instant resetsAt) {}
    public record DecorateResponse(long decorations, Balance balance) {}
    public record HonorEntry(int position, long decorations, FeedPostResponse post) {}
    public record HonorBoard(LocalDate weekStart, LocalDate weekEnd, boolean current, List<HonorEntry> items) {}

    private final DecorationRepository repository;
    private final FeedService feed;
    private final XpService xp;
    private final Notifier notifier;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public DecorationService(DecorationRepository repository, FeedService feed, XpService xp, Notifier notifier, NamedParameterJdbcTemplate jdbc) {
        this(repository, feed, xp, notifier, jdbc, Clock.systemUTC());
    }

    DecorationService(DecorationRepository repository, FeedService feed, XpService xp, Notifier notifier, NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.repository = repository;
        this.feed = feed;
        this.xp = xp;
        this.notifier = notifier;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Segunda-feira da semana atual em Brasilia. */
    static LocalDate weekStart(Instant now) {
        return LocalDate.ofInstant(now, BRASILIA).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    @Transactional(readOnly = true)
    public Balance balance(AuthenticatedUser user) {
        LocalDate week = weekStart(clock.instant());
        return balance(repository.weeklyQuota(DEFAULT_QUOTA), repository.usedThisWeek(user.internalId(), week), week);
    }

    @Transactional
    public DecorateResponse decorate(AuthenticatedUser user, UUID postId) {
        var target = repository.target(postId, user.internalId()).orElseThrow(() -> BusinessException.notFound("Post não encontrado."));
        if (target.authorUserId() == user.internalId()) {
            throw BusinessException.badRequest("OWN_POST", "Não é possível condecorar o próprio post.");
        }
        repository.lockGiver(user.internalId());
        Instant now = clock.instant();
        LocalDate week = weekStart(now);
        if (repository.alreadyDecorated(user.internalId(), target.id())) {
            throw BusinessException.conflict("ALREADY_DECORATED", "Você já condecorou este post.");
        }
        int quota = repository.weeklyQuota(DEFAULT_QUOTA);
        int used = repository.usedThisWeek(user.internalId(), week);
        if (used >= quota) {
            throw BusinessException.conflict("NO_DECORATIONS_LEFT", "Suas condecorações da semana acabaram. Elas voltam na segunda-feira.");
        }
        long decorationId = repository.insert(user.internalId(), target.id(), week, now);
        long total = repository.bumpPost(target.id(), now);
        xp.award(target.authorUserId(), "DECORATION_RECEIVED", "DECORATION", decorationId, user.internalId());
        notifier.toUser(target.authorUserId(), "DECORATION_RECEIVED", callsign(user.internalId()) + " condecorou seu post",
            "Seu post já tem " + total + (total == 1 ? " condecoração." : " condecorações."), "Feed");
        return new DecorateResponse(total, balance(quota, used + 1, week));
    }

    /** Quadro de Honra da semana atual ou da anterior (destaque das segundas-feiras). */
    @Transactional(readOnly = true)
    public HonorBoard honorBoard(AuthenticatedUser user, boolean previous) {
        LocalDate current = weekStart(clock.instant());
        LocalDate week = previous ? current.minusWeeks(1) : current;
        List<HonorRow> rows = repository.honorBoard(week, user.internalId(), HONOR_LIMIT);
        Map<Long, FeedPostResponse> posts = feed.postsByIds(user, rows.stream().map(HonorRow::postId).toList()).stream()
            .collect(Collectors.toMap(FeedService.IdentifiedPost::internalId, FeedService.IdentifiedPost::post, (a, b) -> a));
        List<HonorEntry> items = new java.util.ArrayList<>();
        for (HonorRow row : rows) {
            FeedPostResponse post = posts.get(row.postId());
            if (post != null) items.add(new HonorEntry(items.size() + 1, row.decorations(), post));
        }
        return new HonorBoard(week, week.plusDays(6), !previous, items);
    }

    private static Balance balance(int quota, int used, LocalDate week) {
        Instant resetsAt = week.plusWeeks(1).atStartOfDay(BRASILIA).toInstant();
        return new Balance(quota, used, Math.max(0, quota - used), resetsAt);
    }

    private String callsign(long userId) {
        return jdbc.query("SELECT callsign FROM operator_profile WHERE user_id = :id", Map.of("id", userId), (r, i) -> r.getString(1))
            .stream().findFirst().orElse("Um operador");
    }

}
