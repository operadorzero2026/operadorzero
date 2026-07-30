package br.com.operadorzero.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operator.OperatorSocialRepository.ConnectionRow;
import br.com.operadorzero.shared.web.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperatorSocialServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-30T12:00:00Z"), ZoneOffset.UTC);
    private static final AuthenticatedUser USER = new AuthenticatedUser(10L, UUID.randomUUID(), "a@example.test", "alpha", "Alpha", "A", List.of("OPERATOR"));

    @Test
    void requestDoesNotCreateDuplicatePendingConnection() {
        OperatorSocialRepository repository=mock(OperatorSocialRepository.class);
        UUID id=UUID.randomUUID();
        when(repository.userId("bravo")).thenReturn(Optional.of(20L));
        when(repository.blocked(10L,20L)).thenReturn(false);
        when(repository.activeConnection(10L,20L)).thenReturn(Optional.of(new ConnectionRow(1L,id,10L,20L,"PENDING")));
        var response=new OperatorSocialService(repository,CLOCK).request(USER,"bravo");
        assertThat(response.status()).isEqualTo("PENDING_SENT");
        assertThat(response.connectionId()).isEqualTo(id);
    }

    @Test
    void onlyRecipientCanAcceptPendingRequest() {
        OperatorSocialRepository repository=mock(OperatorSocialRepository.class);
        UUID id=UUID.randomUUID();
        when(repository.transition(id,10L,"PENDING","ACCEPTED",true,CLOCK.instant())).thenReturn(0);
        OperatorSocialService service=new OperatorSocialService(repository,CLOCK);
        assertThatThrownBy(() -> service.accept(USER,id)).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("INVALID_CONNECTION_STATE"));
        verify(repository).transition(id,10L,"PENDING","ACCEPTED",true,CLOCK.instant());
    }
}
