package br.com.operadorzero.operation;

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

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.notification.Notifier;
import br.com.operadorzero.operation.OperatorInviteRepository.InvitableOperation;
import br.com.operadorzero.operation.OperatorInviteRepository.ManagedTeam;
import br.com.operadorzero.operation.OperatorInviteRepository.Target;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.ratelimit.UserActionLimiter;
import br.com.operadorzero.shared.web.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperatorInviteServiceTest {
    // 02/11/2026 23h30 em Brasilia (03/11 02h30 UTC): o "hoje" do jogo ainda e 02/11.
    private static final Instant NOW = Instant.parse("2026-11-03T02:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    private final OperatorInviteRepository repository = mock(OperatorInviteRepository.class);
    private final Notifier notifier = mock(Notifier.class);
    private final UserActionLimiter limiter = mock(UserActionLimiter.class);
    private final AuditEventRepository audit = mock(AuditEventRepository.class);
    private final AuthenticatedUser viewer = new AuthenticatedUser(7L, UUID.randomUUID(), "op@example.test", "op", "Op", "OP", List.of("USER"));
    private final Target friend = new Target(9L, UUID.randomUUID(), "amigo", "Lobo", false);
    private final UUID gameId = UUID.randomUUID();
    private final InvitableOperation game = new InvitableOperation(gameId, "Operação Aurora", LocalDate.of(2026, 11, 8), "Campo Alfa");
    private final OperatorInviteService service = new OperatorInviteService(repository, notifier, limiter, audit, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void strangersSeeNoGamesAndTheTeamInviteExplainsWhy() {
        when(repository.target("amigo", 7L)).thenReturn(Optional.of(friend));
        when(repository.friends(7L, 9L)).thenReturn(false);
        when(repository.managedTeam(7L)).thenReturn(Optional.of(new ManagedTeam(UUID.randomUUID(), "Bravo", "CAPTAIN")));

        var options = service.options(viewer, "amigo");

        assertThat(options.friends()).isFalse();
        assertThat(options.operations()).isEmpty();
        assertThat(options.team().canInvite()).isFalse();
        verify(repository, never()).invitableOperations(anyLong(), anyLong(), any());
    }

    @Test
    void friendsWithoutTeamCanBeInvitedToTheCaptainsTeamAndToUpcomingGames() {
        when(repository.target("amigo", 7L)).thenReturn(Optional.of(friend));
        when(repository.friends(7L, 9L)).thenReturn(true);
        when(repository.managedTeam(7L)).thenReturn(Optional.of(new ManagedTeam(UUID.randomUUID(), "Bravo", "MANAGER")));
        when(repository.invitableOperations(7L, 9L, TODAY)).thenReturn(List.of(game));

        var options = service.options(viewer, "amigo");

        assertThat(options.team().canInvite()).isTrue();
        assertThat(options.operations()).containsExactly(game);
        assertThat(options.operatorId()).isEqualTo(friend.operatorId());
    }

    @Test
    void invitingAFriendNotifiesWithALinkToTheCallUp() {
        when(repository.target("amigo", 7L)).thenReturn(Optional.of(friend));
        when(repository.friends(7L, 9L)).thenReturn(true);
        // Depois do convite o jogo some da lista; o aviso precisa usar o que foi lido antes.
        when(repository.invitableOperations(7L, 9L, TODAY)).thenReturn(List.of(game), List.of());
        when(repository.operationId(gameId)).thenReturn(55L);
        when(repository.insertInvitation(55L, 7L, 9L, NOW)).thenReturn(true);
        when(repository.callsign(7L)).thenReturn("Falcão");

        service.inviteToGame(viewer, gameId, "amigo");

        verify(limiter).check("game-invite", 7L, OperatorInviteService.DAILY_GAME_INVITES, Duration.ofDays(1));
        verify(notifier).toUserAt(eq(9L), eq("GAME_INVITATION"), eq("Falcão convidou você para Operação Aurora"),
            eq("Jogo em 08/11 · Campo Alfa. Toque para ver a convocação e se alistar."), eq("Operações"), eq("/convocacao/" + gameId));
    }

    @Test
    void onlyFriendsCanBeInvitedToGames() {
        when(repository.target("amigo", 7L)).thenReturn(Optional.of(friend));
        when(repository.friends(7L, 9L)).thenReturn(false);

        assertThatThrownBy(() -> service.inviteToGame(viewer, gameId, "amigo")).isInstanceOf(BusinessException.class);
        verify(repository, never()).insertInvitation(anyLong(), anyLong(), anyLong(), any());
        verify(notifier, never()).toUserAt(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void aGameThatIsClosedOrAlreadyInvitedIsRefused() {
        when(repository.target("amigo", 7L)).thenReturn(Optional.of(friend));
        when(repository.friends(7L, 9L)).thenReturn(true);
        when(repository.invitableOperations(7L, 9L, TODAY)).thenReturn(List.of());

        assertThatThrownBy(() -> service.inviteToGame(viewer, gameId, "amigo")).isInstanceOf(BusinessException.class);
        verify(notifier, never()).toUserAt(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString());
    }
}
