package br.com.operadorzero.operation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operation.OperationStructureRepository.ChatAccess;
import br.com.operadorzero.operation.OperationStructureRepository.OperationAccess;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.web.BusinessException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperationStructureServiceTest {
    private final AuthenticatedUser user = new AuthenticatedUser(10L, UUID.randomUUID(), "operator@example.test",
        "operator", "Operator", "Zero", List.of("OPERATOR"));

    @Test
    void memberCannotReadAnotherSquadsChat() {
        OperationStructureRepository repository = mock(OperationStructureRepository.class);
        UUID operationId = UUID.randomUUID();
        UUID adversarySquadId = UUID.randomUUID();
        var access = new OperationAccess(1L, operationId, 99L, "REGISTRATION_OPEN", "MEDIUM", 100, true, false, false, false);
        when(repository.access(operationId, user.internalId(), false)).thenReturn(Optional.of(access));
        when(repository.chatAccess(access, user.internalId(), adversarySquadId))
            .thenReturn(new ChatAccess(2L, UUID.randomUUID(), "SQUAD", "OPEN", false, true, false));
        OperationStructureService service = new OperationStructureService(repository, mock(OperationChatRateLimiter.class),
            mock(AuditEventRepository.class), Clock.systemUTC());

        assertThatThrownBy(() -> service.chat(user, operationId, adversarySquadId, 30, null))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.status().value()).isEqualTo(403));
    }

    @Test
    void publishedOperationRejectsStructuralChanges() {
        OperationStructureRepository repository = mock(OperationStructureRepository.class);
        UUID operationId = UUID.randomUUID();
        var access = new OperationAccess(1L, operationId, user.internalId(), "REGISTRATION_OPEN", "MEDIUM", 100, true, false, true, false);
        when(repository.access(operationId, user.internalId(), true)).thenReturn(Optional.of(access));
        OperationStructureService service = new OperationStructureService(repository, mock(OperationChatRateLimiter.class),
            mock(AuditEventRepository.class), Clock.systemUTC());

        assertThatThrownBy(() -> service.createSquad(user, operationId, UUID.randomUUID(),
            new OperationStructureDtos.SaveSquadRequest("Alfa", "A", null, 10, 1, true)))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo("STRUCTURE_ALREADY_PUBLISHED"));
    }
}
