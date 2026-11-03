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
import br.com.operadorzero.operation.OperationChatRepository.Context;
import br.com.operadorzero.shared.ratelimit.UserActionLimiter;
import br.com.operadorzero.shared.web.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperationChatServiceTest {
    // Termino em 10/11/2026 as 17h (Brasilia) = 20h UTC.
    private static final Instant END = Instant.parse("2026-11-10T20:00:00Z");
    private final OperationChatRepository repository = mock(OperationChatRepository.class);
    private final OperationChatHub hub = mock(OperationChatHub.class);
    private final AuthenticatedUser player = new AuthenticatedUser(20L, UUID.randomUUID(), "p@example.test", "p", "P", "P", List.of("USER"));
    private final AuthenticatedUser organizer = new AuthenticatedUser(7L, UUID.randomUUID(), "o@example.test", "o", "O", "O", List.of("USER"));
    private final UUID operationId = UUID.randomUUID();

    private OperationChatService at(Instant now) {
        return new OperationChatService(repository, hub, mock(UserActionLimiter.class), Clock.fixed(now, ZoneOffset.UTC));
    }

    private void given(String participation) {
        when(repository.context(eq(operationId), anyLong())).thenReturn(Optional.of(new Context(5L, 9L, 7L, "REGISTRATION_OPEN", END, false, participation)));
    }

    @Test
    void onlyApprovedPlayersAndTheOrganizerAccessTheChat() {
        var service = at(END.minus(Duration.ofDays(1)));
        for (String status : new String[] {null, "REQUESTED", "WAITING_LIST", "CANCELLED", "REMOVED"}) {
            given(status);
            assertThatThrownBy(() -> service.page(player, operationId, null, null, null)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> service.send(player, operationId, "oi", UUID.randomUUID())).isInstanceOf(BusinessException.class);
        }
        verify(repository, never()).insert(anyLong(), anyLong(), anyString(), any(), any());
        given("APPROVED");
        service.send(player, operationId, "  Bom dia, tropa  ", UUID.randomUUID());
        verify(repository).insert(eq(9L), eq(20L), eq("Bom dia, tropa"), any(), any());
        verify(hub).changed(5L);
    }

    @Test
    void mutedPlayersReadButCannotSend() {
        var service = at(END.minus(Duration.ofDays(1)));
        given("CONFIRMED");
        when(repository.activeMute(5L, 20L, END.minus(Duration.ofDays(1)))).thenReturn(Optional.of(Optional.empty()));
        assertThat(service.page(player, operationId, null, null, null).me().canSend()).isFalse();
        assertThatThrownBy(() -> service.send(player, operationId, "oi", UUID.randomUUID()))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getMessage()).contains("silenciado"));
    }

    @Test
    void chatStaysOpenSevenDaysAfterTheEndWithANoticeAndThenOnlyHistory() {
        given("APPROVED");
        assertThat(at(END.minus(Duration.ofHours(1))).page(player, operationId, null, null, null).notice()).isNull();
        var during = at(END.plus(Duration.ofDays(2))).page(player, operationId, null, null, null);
        assertThat(during.notice()).isEqualTo("Este chat será encerrado em 17/11/2026, às 17h.");
        assertThat(during.closed()).isFalse();
        var after = at(END.plus(Duration.ofDays(7))).page(player, operationId, null, null, null);
        assertThat(after.closed()).isTrue();
        assertThat(after.me().canSend()).isFalse();
        assertThatThrownBy(() -> at(END.plus(Duration.ofDays(7))).send(organizer, operationId, "oi", UUID.randomUUID()))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("CHAT_CLOSED"));
    }

    @Test
    void moderationIsOnlyForTheOrganizerAndIsLogged() {
        var service = at(END.minus(Duration.ofDays(1)));
        given("APPROVED");
        UUID target = UUID.randomUUID();
        assertThatThrownBy(() -> service.mute(player, operationId, target, "1H")).isInstanceOf(BusinessException.class);
        when(repository.member(5L, target)).thenReturn(Optional.of(20L));
        service.mute(organizer, operationId, target, "6h");
        verify(repository).mute(eq(5L), eq(20L), eq(END.minus(Duration.ofDays(1)).plus(Duration.ofHours(6))), eq(7L), any());
        verify(repository).log(eq(5L), eq(7L), eq("OPERATOR"), eq(target), eq("MUTED"), eq("6H"), any());
        service.mute(organizer, operationId, target, "INDEFINITE");
        verify(repository).mute(eq(5L), eq(20L), eq(null), eq(7L), any());
        assertThatThrownBy(() -> service.mute(organizer, operationId, target, "2H")).isInstanceOf(BusinessException.class);

        when(repository.member(5L, organizer.publicId())).thenReturn(Optional.of(7L));
        assertThatThrownBy(() -> service.mute(organizer, operationId, organizer.publicId(), "1H")).isInstanceOf(BusinessException.class);
    }

    @Test
    void messagesAreTextOnlyAndLimited() {
        assertThatThrownBy(() -> OperationChatService.clean("   ")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> OperationChatService.clean("x".repeat(1001))).isInstanceOf(BusinessException.class);
        assertThat(OperationChatService.clean("linha\u0007 1\nlinha 2")).isEqualTo("linha 1\nlinha 2");
    }
}
