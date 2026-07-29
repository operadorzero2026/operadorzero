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
import java.time.LocalDate;
import java.time.LocalTime;
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
        when(repository.requestParticipation(operationId, operationTeamId, null, user.internalId(), now)).thenReturn(1);
        OperationService service = new OperationService(repository, mock(AuditEventRepository.class),
            Clock.fixed(now, ZoneOffset.UTC));

        var response = service.requestParticipation(user, operationId,
            new OperationDtos.ParticipationRequest(operationTeamId, null));

        assertThat(response.message()).isEqualTo("Solicitação registrada.");
    }

    @Test
    void publishRejectsOperationThatIsNotAnOwnedDraft() {
        OperationRepository repository = mock(OperationRepository.class);
        AuthenticatedUser user = new AuthenticatedUser(10L, UUID.randomUUID(), "operator@example.test", "operator",
            "Operator", "Zero", List.of("OPERATOR"));
        UUID operationId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-28T12:00:00Z");
        when(repository.publishStructureReady(operationId, user.internalId())).thenReturn(true);
        when(repository.publish(operationId, user.internalId(), now)).thenReturn(0);
        OperationService service = new OperationService(repository, mock(AuditEventRepository.class),
            Clock.fixed(now, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.publish(user, operationId))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.code()).isEqualTo("OPERATION_NOT_PUBLISHABLE"));
    }

    @Test
    void updateRejectsAnInvalidScheduleBeforePersistence() {
        OperationRepository repository = mock(OperationRepository.class);
        AuthenticatedUser user = new AuthenticatedUser(10L, UUID.randomUUID(), "operator@example.test", "operator",
            "Operator", "Zero", List.of("OPERATOR"));
        OperationService service = new OperationService(repository, mock(AuditEventRepository.class),
            Clock.fixed(Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC));
        var request = new OperationDtos.UpdateOperationRequest(LocalDate.of(2026, 8, 10),
            LocalTime.of(9, 0), LocalTime.of(8, 0), LocalTime.of(18, 0), "Briefing", "Ajuste", 0L);

        assertThatThrownBy(() -> service.update(user, UUID.randomUUID(), request))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.code()).isEqualTo("INVALID_OPERATION_TIME"));
    }

    @Test
    void updateReportsAnOptimisticConflictForAnOwnedOperation() {
        OperationRepository repository = mock(OperationRepository.class);
        AuthenticatedUser user = new AuthenticatedUser(10L, UUID.randomUUID(), "operator@example.test", "operator",
            "Operator", "Zero", List.of("OPERATOR"));
        UUID operationId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-29T12:00:00Z");
        var request = new OperationDtos.UpdateOperationRequest(LocalDate.of(2026, 8, 10),
            LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(18, 0), "Briefing", "Ajuste", 2L);
        when(repository.update(operationId, user.internalId(), request, "Briefing", now)).thenReturn(0);
        when(repository.isOwned(operationId, user.internalId())).thenReturn(true);
        OperationService service = new OperationService(repository, mock(AuditEventRepository.class), Clock.fixed(now, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.update(user, operationId, request))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.code()).isEqualTo("OPERATION_UPDATE_CONFLICT"));
    }

    @Test
    void deleteSoftDeletesAnOwnedOperation() {
        OperationRepository repository = mock(OperationRepository.class);
        AuditEventRepository audit = mock(AuditEventRepository.class);
        AuthenticatedUser user = new AuthenticatedUser(10L, UUID.randomUUID(), "operator@example.test", "operator",
            "Operator", "Zero", List.of("OPERATOR"));
        UUID operationId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-29T12:00:00Z");
        when(repository.softDelete(operationId, user.internalId(), now)).thenReturn(1);
        OperationService service = new OperationService(repository, audit, Clock.fixed(now, ZoneOffset.UTC));

        assertThat(service.delete(user, operationId, new OperationDtos.DeleteOperationRequest("Evento cancelado")).message())
            .isEqualTo("Operação excluída.");
    }
}
