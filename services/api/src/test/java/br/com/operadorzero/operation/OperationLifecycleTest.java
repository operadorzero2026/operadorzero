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
import br.com.operadorzero.operation.OperationDtos.ParticipantDecisionRequest;
import br.com.operadorzero.operation.OperationDtos.UpdateStatusRequest;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.web.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OperationLifecycleTest {
    private final OperationRepository repository = mock(OperationRepository.class);
    private final Instant now = Instant.parse("2026-11-01T12:00:00Z");
    private final AuthenticatedUser organizer = new AuthenticatedUser(10L, UUID.randomUUID(), "org@example.test", "org",
        "Org", "ORG", List.of("USER"));
    private final UUID operationId = UUID.randomUUID();
    private final UUID operatorId = UUID.randomUUID();
    private OperationService service;

    @BeforeEach
    void setup() {
        service = new OperationService(repository, mock(AuditEventRepository.class), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void onlyTheOrganizerCanDecideOnParticipants() {
        when(repository.lockOwnedOperation(operationId, organizer.internalId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decideParticipant(organizer, operationId, operatorId, new ParticipantDecisionRequest("approve", null)))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code()).isEqualTo("NOT_FOUND"));
        verify(repository, never()).transitionParticipant(anyLong(), any(), any(), anyString(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void approvalMovesPendingOrWaitingParticipantsBeforeTheStart() {
        when(repository.lockOwnedOperation(operationId, organizer.internalId())).thenReturn(Optional.of(5L));
        when(repository.transitionParticipant(eq(5L), eq(operatorId), any(), eq("APPROVED"), any(), eq(now))).thenReturn(1);

        assertThat(service.decideParticipant(organizer, operationId, operatorId, new ParticipantDecisionRequest("APPROVE", null)).message())
            .isEqualTo("Inscrição aprovada.");

        ArgumentCaptor<Collection<String>> from = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Collection<String>> operationStatuses = ArgumentCaptor.forClass(Collection.class);
        verify(repository).transitionParticipant(eq(5L), eq(operatorId), from.capture(), eq("APPROVED"), operationStatuses.capture(), eq(now));
        assertThat(from.getValue()).containsExactlyInAnyOrder("REQUESTED", "WAITING_LIST");
        assertThat(operationStatuses.getValue()).doesNotContain("IN_PROGRESS", "FINISHED", "CANCELLED", "DRAFT");
    }

    @Test
    void rejectionFreesTheSpotForTheWaitingList() {
        when(repository.lockOwnedOperation(operationId, organizer.internalId())).thenReturn(Optional.of(5L));
        when(repository.transitionParticipant(eq(5L), eq(operatorId), any(), eq("REJECTED"), any(), eq(now))).thenReturn(1);

        service.decideParticipant(organizer, operationId, operatorId, new ParticipantDecisionRequest("REJECT", "Sem equipamento obrigatório"));

        verify(repository).promoteWaitingList(5L, now);
    }

    @Test
    void rejectedOperatorIsNotifiedWithTheReason() {
        br.com.operadorzero.notification.Notifier notifier = mock(br.com.operadorzero.notification.Notifier.class);
        OperationService notifying = new OperationService(repository, mock(AuditEventRepository.class), notifier, Clock.fixed(now, ZoneOffset.UTC));
        when(repository.lockOwnedOperation(operationId, organizer.internalId())).thenReturn(Optional.of(5L));
        when(repository.transitionParticipant(eq(5L), eq(operatorId), any(), eq("REJECTED"), any(), eq(now))).thenReturn(1);

        notifying.decideParticipant(organizer, operationId, operatorId, new ParticipantDecisionRequest("REJECT", "Sem equipamento obrigatório"));

        verify(notifier).toOperator(eq(operatorId), eq("OPERATION_PARTICIPATION"), org.mockito.ArgumentMatchers.startsWith("Inscrição recusada"),
            eq("Sem equipamento obrigatório"), eq("Operações"));
    }

    @Test
    void unavailableTransitionIsReportedAsConflict() {
        when(repository.lockOwnedOperation(operationId, organizer.internalId())).thenReturn(Optional.of(5L));
        when(repository.transitionParticipant(anyLong(), any(), any(), anyString(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.decideParticipant(organizer, operationId, operatorId, new ParticipantDecisionRequest("CHECK_IN", null)))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code()).isEqualTo("PARTICIPANT_ACTION_UNAVAILABLE"));
    }

    @Test
    void unknownParticipantActionIsRejected() {
        assertThatThrownBy(() -> service.decideParticipant(organizer, operationId, operatorId, new ParticipantDecisionRequest("PROMOTE_TO_ADMIN", null)))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code()).isEqualTo("INVALID_PARTICIPANT_ACTION"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void finishingIsOnlyAllowedFromInProgress() {
        when(repository.updateStatus(eq(operationId), eq(10L), eq("FINISHED"), any(), eq(now))).thenReturn(0);
        when(repository.isOwned(operationId, 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateStatus(organizer, operationId, new UpdateStatusRequest("FINISHED", null)))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code()).isEqualTo("INVALID_STATUS_TRANSITION"));

        ArgumentCaptor<Collection<String>> from = ArgumentCaptor.forClass(Collection.class);
        verify(repository).updateStatus(eq(operationId), eq(10L), eq("FINISHED"), from.capture(), eq(now));
        assertThat(from.getValue()).containsExactly("IN_PROGRESS");
    }

    @Test
    void draftAndPublishedCannotBeSetManuallyAndCancellationNeedsAReason() {
        assertThatThrownBy(() -> service.updateStatus(organizer, operationId, new UpdateStatusRequest("DRAFT", null)))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code()).isEqualTo("INVALID_OPERATION_STATUS"));
        assertThatThrownBy(() -> service.updateStatus(organizer, operationId, new UpdateStatusRequest("CANCELLED", "  ")))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code()).isEqualTo("CANCELLATION_REASON_REQUIRED"));
        verify(repository, never()).updateStatus(any(), anyLong(), anyString(), any(), any());
    }

    @Test
    void cancellingAParticipationPromotesTheWaitingList() {
        AuthenticatedUser player = new AuthenticatedUser(20L, UUID.randomUUID(), "p@example.test", "p", "P", "P", List.of("USER"));
        when(repository.lockOperation(operationId)).thenReturn(Optional.of(5L));
        when(repository.cancelParticipation(operationId, 20L, now)).thenReturn(1);

        service.cancelParticipation(player, operationId);

        verify(repository).promoteWaitingList(5L, now);
    }

    @Test
    void participantConfirmsOwnPresenceOnlyWhenApproved() {
        AuthenticatedUser player = new AuthenticatedUser(20L, UUID.randomUUID(), "p@example.test", "p", "P", "P", List.of("USER"));
        when(repository.confirmOwnPresence(operationId, 20L, now)).thenReturn(0);

        assertThatThrownBy(() -> service.confirmOwnPresence(player, operationId))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code()).isEqualTo("PRESENCE_NOT_CONFIRMABLE"));
    }
}
