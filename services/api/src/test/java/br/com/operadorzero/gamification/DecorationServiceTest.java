package br.com.operadorzero.gamification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.operadorzero.feed.FeedService;
import br.com.operadorzero.gamification.DecorationRepository.Target;
import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.notification.Notifier;
import br.com.operadorzero.shared.web.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class DecorationServiceTest {
    // Segunda-feira 02/11/2026, 10h em Brasilia.
    private static final Instant MONDAY = Instant.parse("2026-11-02T13:00:00Z");
    private final DecorationRepository repository = mock(DecorationRepository.class);
    private final XpService xp = mock(XpService.class);
    private final Notifier notifier = mock(Notifier.class);
    private final AuthenticatedUser giver = new AuthenticatedUser(7L, UUID.randomUUID(), "op@example.test", "op", "Op", "OP", List.of("USER"));
    private final UUID postId = UUID.randomUUID();

    private DecorationService service(Instant now) {
        return new DecorationService(repository, mock(FeedService.class), xp, notifier, mock(NamedParameterJdbcTemplate.class),
            Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void theWeekStartsOnMondayMidnightInBrasilia() {
        // Domingo 23h30 em Brasilia (segunda 02h30 UTC) ainda e a semana anterior.
        assertThat(DecorationService.weekStart(Instant.parse("2026-11-02T02:30:00Z"))).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(DecorationService.weekStart(Instant.parse("2026-11-02T03:00:00Z"))).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(DecorationService.weekStart(Instant.parse("2026-11-08T23:00:00Z"))).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    void decoratingUsesTheQuotaGivesXpAndNotifiesTheAuthor() {
        when(repository.target(postId, 7L)).thenReturn(Optional.of(new Target(11L, postId, 9L)));
        when(repository.weeklyQuota(3)).thenReturn(3);
        when(repository.usedThisWeek(7L, LocalDate.of(2026, 11, 2))).thenReturn(1);
        when(repository.insert(7L, 11L, LocalDate.of(2026, 11, 2), MONDAY)).thenReturn(40L);
        when(repository.bumpPost(11L, MONDAY)).thenReturn(5L);

        var response = service(MONDAY).decorate(giver, postId);

        assertThat(response.decorations()).isEqualTo(5);
        assertThat(response.balance().remaining()).isEqualTo(1);
        assertThat(response.balance().resetsAt()).isEqualTo(Instant.parse("2026-11-09T03:00:00Z"));
        verify(repository).lockGiver(7L);
        verify(xp).award(9L, "DECORATION_RECEIVED", "DECORATION", 40L, 7L);
        verify(notifier).toUser(eq(9L), eq("DECORATION_RECEIVED"), anyString(), anyString(), eq("Feed"));
    }

    @Test
    void ownPostsRepeatedPostsAndAnEmptyQuotaAreRefused() {
        when(repository.target(postId, 7L)).thenReturn(Optional.of(new Target(11L, postId, 7L)));
        assertThatThrownBy(() -> service(MONDAY).decorate(giver, postId))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("OWN_POST"));

        when(repository.target(postId, 7L)).thenReturn(Optional.of(new Target(11L, postId, 9L)));
        when(repository.alreadyDecorated(7L, 11L)).thenReturn(true);
        assertThatThrownBy(() -> service(MONDAY).decorate(giver, postId))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("ALREADY_DECORATED"));

        when(repository.alreadyDecorated(7L, 11L)).thenReturn(false);
        when(repository.weeklyQuota(3)).thenReturn(3);
        when(repository.usedThisWeek(7L, LocalDate.of(2026, 11, 2))).thenReturn(3);
        assertThatThrownBy(() -> service(MONDAY).decorate(giver, postId))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("NO_DECORATIONS_LEFT"));
        verify(repository, never()).insert(anyLong(), anyLong(), any(), any());
    }

    @Test
    void unusedDecorationsDoNotCarryOverToTheNextWeek() {
        when(repository.weeklyQuota(3)).thenReturn(3);
        when(repository.usedThisWeek(7L, LocalDate.of(2026, 11, 2))).thenReturn(0);
        assertThat(service(MONDAY).balance(giver).remaining()).isEqualTo(3);
    }
}
