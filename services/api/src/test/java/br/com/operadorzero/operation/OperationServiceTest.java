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
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class OperationServiceTest {
    @Test
    void coverUploadRejectsAnOperationNotOwnedByTheUser() {
        OperationRepository repository = mock(OperationRepository.class);
        AuthenticatedUser user = new AuthenticatedUser(10L, UUID.randomUUID(), "operator@example.test", "operator",
            "Operator", "Zero", List.of("OPERATOR"));
        UUID operationId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-28T12:00:00Z");
        byte[] png = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        var file = new MockMultipartFile("file", "cover.png", "image/png", png);
        OperationService service = new OperationService(repository, mock(AuditEventRepository.class),
            Clock.fixed(now, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.saveCover(user, operationId, file))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.code()).isEqualTo("NOT_FOUND"));
    }

    @Test
    void participationUsesTheSelectedOperationTeam() {
        OperationRepository repository = mock(OperationRepository.class);
        AuthenticatedUser user = new AuthenticatedUser(10L, UUID.randomUUID(), "operator@example.test", "operator",
            "Operator", "Zero", List.of("OPERATOR"));
        UUID operationId = UUID.randomUUID();
        UUID operationTeamId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-28T12:00:00Z");
        when(repository.requestParticipation(operationId, operationTeamId, user.internalId(), now)).thenReturn(1);
        OperationService service = new OperationService(repository, mock(AuditEventRepository.class),
            Clock.fixed(now, ZoneOffset.UTC));

        var response = service.requestParticipation(user, operationId,
            new OperationDtos.ParticipationRequest(operationTeamId));

        assertThat(response.message()).isEqualTo("Solicitação registrada.");
    }

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
