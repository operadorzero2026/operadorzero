package br.com.operadorzero.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.notification.Notifier;
import br.com.operadorzero.operation.OperationRegistrationDtos.Participant;
import br.com.operadorzero.operation.OperationRegistrationDtos.RegisterRequest;
import br.com.operadorzero.operation.OperationRegistrationDtos.RoleRequest;
import br.com.operadorzero.operation.OperationRegistrationRepository.ChangeRow;
import br.com.operadorzero.operation.OperationRegistrationRepository.Mine;
import br.com.operadorzero.operation.OperationRegistrationRepository.Op;
import br.com.operadorzero.operation.OperationRegistrationRepository.Target;
import br.com.operadorzero.operation.OperationRegistrationRepository.TeamRow;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.web.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OperationRegistrationServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final Instant START = Instant.parse("2026-11-15T12:00:00Z");
    private final OperationRegistrationRepository repository = mock(OperationRegistrationRepository.class);
    private final OperationRepository operations = mock(OperationRepository.class);
    private final Notifier notifier = mock(Notifier.class);
    private final OperationRegistrationService service = new OperationRegistrationService(repository, operations,
        mock(AuditEventRepository.class), notifier, Clock.fixed(NOW, ZoneOffset.UTC));
    private final AuthenticatedUser player = new AuthenticatedUser(20L, UUID.randomUUID(), "p@example.test", "p", "P", "P", List.of("USER"));
    private final AuthenticatedUser organizer = new AuthenticatedUser(7L, UUID.randomUUID(), "o@example.test", "o", "O", "O", List.of("USER"));
    private final UUID operationId = UUID.randomUUID();

    private Op op(String model, String status, Instant closedAt, Instant startsAt, boolean teamChange) {
        return new Op(5L, operationId, 7L, "Operação Aurora", model, status, closedAt, startsAt, false, true, null, teamChange);
    }

    private void given(Op op) {
        when(repository.operation(eq(operationId), anyLong(), anyBoolean())).thenReturn(Optional.of(op));
    }

    @ParameterizedTest(name = "{0} medico={1} radio={2}")
    @CsvSource({"ASSAULT,false,false", "ASSAULT,true,false", "ASSAULT,false,true", "ASSAULT,true,true",
        "SNIPER,false,false", "SNIPER,true,false", "SNIPER,false,true", "SNIPER,true,true",
        "DMR,false,false", "DMR,true,false", "DMR,false,true", "DMR,true,true"})
    void everyPrimaryRoleCombinesFreelyWithMedicAndRadio(String role, boolean medic, boolean radio) {
        given(op("SINGLE_LIST", "REGISTRATION_OPEN", null, START, true));
        when(repository.register(5L, 20L, "APPROVED", null, null, role, medic, radio, NOW)).thenReturn(1);

        assertThat(service.register(player, operationId, new RegisterRequest(null, null, role.toLowerCase(), medic, radio)).message())
            .isEqualTo("Inscrição confirmada.");
        verify(repository).register(5L, 20L, "APPROVED", null, null, role, medic, radio, NOW);
    }

    @Test
    void thePrimaryRoleIsMandatory() {
        given(op("SINGLE_LIST", "REGISTRATION_OPEN", null, START, true));
        assertThatThrownBy(() -> service.register(player, operationId, new RegisterRequest(null, null, "MEDIC", true, false)))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("PRIMARY_ROLE_REQUIRED"));
    }

    @Test
    void registrationClosesAtTheStartTimeAndWhenTheOrganizerClosesIt() {
        given(op("SINGLE_LIST", "REGISTRATION_OPEN", null, NOW, true));
        assertThatThrownBy(() -> service.register(player, operationId, new RegisterRequest(null, null, "ASSAULT", false, false)))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("REGISTRATION_CLOSED"));

        given(op("SINGLE_LIST", "REGISTRATION_OPEN", NOW.minusSeconds(60), START, true));
        assertThatThrownBy(() -> service.register(player, operationId, new RegisterRequest(null, null, "ASSAULT", false, false)))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("REGISTRATION_CLOSED"));
        verify(repository, never()).register(anyLong(), anyLong(), anyString(), any(), any(), anyString(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    void removedPlayersCannotComeBackAndActivePlayersCannotRegisterTwice() {
        given(op("SINGLE_LIST", "REGISTRATION_OPEN", null, START, true));
        when(repository.mine(5L, 20L, true)).thenReturn(Optional.of(new Mine(1L, "REMOVED", "ASSAULT", false, false, null, null, null, "x", NOW)));
        assertThatThrownBy(() -> service.register(player, operationId, new RegisterRequest(null, null, "ASSAULT", false, false)))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getMessage()).contains("removido desta operação"));

        when(repository.mine(5L, 20L, true)).thenReturn(Optional.of(new Mine(1L, "APPROVED", "ASSAULT", false, false, null, null, null, null, null)));
        assertThatThrownBy(() -> service.register(player, operationId, new RegisterRequest(null, null, "ASSAULT", false, false)))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("ALREADY_REGISTERED"));
    }

    @Test
    void teamOperationsRequireATeamAndSingleListsRefuseOne() {
        UUID team = UUID.randomUUID();
        given(op("TEAMS", "REGISTRATION_OPEN", null, START, true));
        assertThatThrownBy(() -> service.register(player, operationId, new RegisterRequest(null, null, "DMR", false, false)))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("TEAM_REQUIRED"));

        when(repository.team(5L, team)).thenReturn(Optional.of(new TeamRow(30L, team, "OPEN", OperationRegistrationRepository.UNLIMITED, false)));
        when(repository.register(5L, 20L, "APPROVED", 30L, null, "DMR", false, true, NOW)).thenReturn(1);
        service.register(player, operationId, new RegisterRequest(team, null, "DMR", false, true));
        verify(repository).register(5L, 20L, "APPROVED", 30L, null, "DMR", false, true, NOW);

        given(op("SINGLE_LIST", "REGISTRATION_OPEN", null, START, true));
        assertThatThrownBy(() -> service.register(player, operationId, new RegisterRequest(team, null, "DMR", false, false)))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("SINGLE_LIST"));
    }

    @Test
    void playersChangeTheirOwnRoleWithoutApproval() {
        given(op("SINGLE_LIST", "REGISTRATION_OPEN", NOW, START, true));
        when(repository.mine(5L, 20L, true)).thenReturn(Optional.of(new Mine(9L, "APPROVED", "ASSAULT", false, false, null, null, null, null, null)));

        service.updateRole(player, operationId, new RoleRequest("SNIPER", true, true));

        verify(repository).updateRole(9L, "SNIPER", true, true, NOW);
    }

    @Test
    void cancellingWorksAfterAManualCloseButNotAfterTheStart() {
        given(op("SINGLE_LIST", "REGISTRATION_OPEN", NOW.minusSeconds(3600), START, true));
        when(repository.mine(5L, 20L, true)).thenReturn(Optional.of(new Mine(9L, "APPROVED", "ASSAULT", false, false, 3L, null, null, null, null)));
        when(repository.cancel(9L, NOW)).thenReturn(1);
        service.cancel(player, operationId);
        verify(repository).cancelPendingChanges(9L, NOW);
        verify(repository).event(5L, 20L, "CANCELLED", 20L, null, 3L, null, NOW);
        verify(operations).promoteWaitingList(5L, NOW);

        given(op("SINGLE_LIST", "REGISTRATION_OPEN", null, NOW.minusSeconds(1), true));
        assertThatThrownBy(() -> service.cancel(player, operationId))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("PARTICIPATION_NOT_CANCELLABLE"));
    }

    @Test
    void removalNeedsAReasonNotifiesThePlayerAndOnlyTheOrganizerCanDoIt() {
        UUID operator = UUID.randomUUID();
        given(op("SINGLE_LIST", "REGISTRATION_OPEN", null, START, true));
        assertThatThrownBy(() -> service.remove(player, operationId, operator, "Conduta"))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.remove(organizer, operationId, operator, "  "))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("REMOVAL_REASON_REQUIRED"));

        when(repository.target(5L, operator)).thenReturn(Optional.of(new Target(9L, 20L, "CONFIRMED", 3L)));
        service.remove(organizer, operationId, operator, "Descumpriu as regras de FPS");

        verify(repository).remove(9L, 7L, "Descumpriu as regras de FPS", NOW);
        verify(repository).event(5L, 20L, "REMOVED", 7L, "Descumpriu as regras de FPS", 3L, null, NOW);
        verify(notifier).toUser(eq(20L), eq("OPERATION_REMOVED"), anyString(), eq("Motivo: Descumpriu as regras de FPS"), eq("Operações"));
    }

    @Test
    void authorizingTheReturnReleasesOnlyRemovedPlayers() {
        UUID operator = UUID.randomUUID();
        given(op("SINGLE_LIST", "REGISTRATION_OPEN", null, START, true));
        when(repository.target(5L, operator)).thenReturn(Optional.of(new Target(9L, 20L, "APPROVED", null)));
        assertThatThrownBy(() -> service.authorizeReturn(organizer, operationId, operator)).isInstanceOf(BusinessException.class);

        when(repository.target(5L, operator)).thenReturn(Optional.of(new Target(9L, 20L, "REMOVED", null)));
        service.authorizeReturn(organizer, operationId, operator);
        verify(repository).authorizeReturn(9L, 7L, NOW);
    }

    @Test
    void reopeningIsOnlyPossibleBeforeTheStart() {
        given(op("SINGLE_LIST", "REGISTRATION_OPEN", NOW.minusSeconds(60), START, true));
        service.reopen(organizer, operationId);
        verify(repository).setClosed(5L, null, null, NOW);

        given(op("SINGLE_LIST", "REGISTRATION_OPEN", NOW.minusSeconds(60), NOW.minusSeconds(1), true));
        assertThatThrownBy(() -> service.reopen(organizer, operationId))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("REGISTRATION_REOPEN_TOO_LATE"));
    }

    @Test
    void teamChangesWaitForTheOrganizerAndRespectTheSwitch() {
        UUID team = UUID.randomUUID();
        given(op("TEAMS", "REGISTRATION_OPEN", null, START, false));
        assertThatThrownBy(() -> service.requestTeamChange(player, operationId, team))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("TEAM_CHANGE_DISABLED"));

        given(op("TEAMS", "REGISTRATION_OPEN", null, START, true));
        when(repository.mine(5L, 20L, true)).thenReturn(Optional.of(new Mine(9L, "APPROVED", "ASSAULT", false, false, 3L, null, "Alfa", null, null)));
        when(repository.team(5L, team)).thenReturn(Optional.of(new TeamRow(4L, team, "OPEN", 10, false)));
        service.requestTeamChange(player, operationId, team);
        // O jogador continua na equipe 3: nada e movido no pedido.
        verify(repository).requestChange(5L, 9L, 3L, 4L, NOW);
        verify(repository, never()).moveToTeam(anyLong(), any(), anyLong(), any());

        when(repository.hasPendingChange(9L)).thenReturn(true);
        assertThatThrownBy(() -> service.requestTeamChange(player, operationId, team))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("TEAM_CHANGE_PENDING"));
    }

    @Test
    void approvingAChangeMovesThePlayerAndRejectingKeepsTheTeam() {
        UUID request = UUID.randomUUID();
        given(op("TEAMS", "REGISTRATION_OPEN", null, START, true));
        when(repository.change(5L, request)).thenReturn(Optional.of(new ChangeRow(1L, 9L, 3L, 4L, "PENDING", 20L)));
        when(repository.moveToTeam(9L, 3L, 4L, NOW)).thenReturn(1);
        service.decideTeamChange(organizer, operationId, request, "approve");
        verify(repository).decideChange(1L, "APPROVED", 7L, NOW);
        verify(repository).event(5L, 20L, "TEAM_CHANGED", 7L, null, 3L, 4L, NOW);

        UUID other = UUID.randomUUID();
        when(repository.change(5L, other)).thenReturn(Optional.of(new ChangeRow(2L, 9L, 3L, 4L, "PENDING", 20L)));
        service.decideTeamChange(organizer, operationId, other, "REJECT");
        verify(repository).decideChange(2L, "REJECTED", 7L, NOW);
        verify(repository, never()).moveToTeam(9L, 3L, 4L, NOW.plus(Duration.ofSeconds(1)));
    }

    @Test
    void medicAndRadioAreCountedSeparatelyFromThePrimaryRole() {
        List<Participant> list = new ArrayList<>();
        list.add(participant("ASSAULT", true, true));
        list.add(participant("SNIPER", false, true));
        list.add(participant("DMR", true, false));
        list.add(participant(null, false, false));

        var counts = OperationRegistrationService.counts(list);

        assertThat(counts.total()).isEqualTo(4);
        assertThat(counts.assault()).isEqualTo(1);
        assertThat(counts.sniper()).isEqualTo(1);
        assertThat(counts.dmr()).isEqualTo(1);
        assertThat(counts.medic()).isEqualTo(2);
        assertThat(counts.radio()).isEqualTo(2);
        assertThat(counts.withoutRole()).isEqualTo(1);
    }

    private static Participant participant(String role, boolean medic, boolean radio) {
        return new Participant(UUID.randomUUID(), "u", "C", "D", "APPROVED", NOW, 1, role, medic, radio, null, null, null);
    }
}
