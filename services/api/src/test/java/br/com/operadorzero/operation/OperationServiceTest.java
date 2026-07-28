package br.com.operadorzero.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.web.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperationServiceTest {
    @Test
    void publishRejectsOperationThatIsNotAnOwnedDraft() {
        OperationRepository repository = mock(OperationRepository.class);
        AuthenticatedUser user = new AuthenticatedUser(10L, UUID.randomUUID(), "operator@example.test", "operator",
            "Operator", "Zero", List.of("OPERATOR"));
        UUID operationId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-28T12:00:00Z");
        when(repository.publish(operationId, user.internalId(), now)).thenReturn(0);
        OperationService service = new OperationService(repository, mock(AuditEventRepository.class),
            Clock.fixed(now, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.publish(user, operationId))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.code()).isEqualTo("OPERATION_NOT_PUBLISHABLE"));
    }
}
