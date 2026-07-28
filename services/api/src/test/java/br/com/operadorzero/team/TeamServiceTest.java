package br.com.operadorzero.team;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.operadorzero.identity.AuthRateLimiter;
import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operator.BrazilLocationValidator;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.web.BusinessException;
import br.com.operadorzero.team.TeamDtos.InviteRequest;
import br.com.operadorzero.team.TeamRepository.InvitationRow;
import br.com.operadorzero.team.TeamRepository.MembershipRow;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TeamServiceTest {
    private final TeamRepository repository = mock(TeamRepository.class);
    private final BrazilLocationValidator locations = mock(BrazilLocationValidator.class);
    private final AuditEventRepository audit = mock(AuditEventRepository.class);
    private final AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC);
    private TeamService service;
    private AuthenticatedUser user;
    private UUID teamId;

    @BeforeEach
    void setUp() {
        service = new TeamService(repository, locations, audit, rateLimiter, clock);
        user = new AuthenticatedUser(10L, UUID.randomUUID(), "user@example.test", "operator",
            "Operator", "Zero", List.of("OPERATOR"));
        teamId = UUID.randomUUID();
    }

    @Test
    void captainCannotLeaveBeforeTransferringCaptaincy() {
        when(repository.activeMembership(user.internalId()))
            .thenReturn(Optional.of(new MembershipRow(20L, user.internalId(), "CAPTAIN", teamId)));

        assertThatThrownBy(() -> service.leave(user))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo("CAPTAIN_TRANSFER_REQUIRED"));
    }

    @Test
    void managerCannotInviteAnotherManager() {
        when(repository.activeMembership(user.internalId(), teamId))
            .thenReturn(Optional.of(new MembershipRow(20L, user.internalId(), "MANAGER", teamId)));

        assertThatThrownBy(() -> service.invite(user, teamId,
            new InviteRequest(UUID.randomUUID(), "Bem-vindo", "MANAGER"), mock(HttpServletRequest.class)))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> org.assertj.core.api.Assertions.assertThat(exception.status().value()).isEqualTo(403));
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void invitationCannotBeAcceptedByAnotherOperator() {
        UUID invitationId = UUID.randomUUID();
        when(repository.lockInvitation(invitationId)).thenReturn(Optional.of(new InvitationRow(
            99L, invitationId, 20L, 777L, 8L, "MEMBER", "PENDING",
            clock.instant().plusSeconds(3600), teamId)));

        assertThatThrownBy(() -> service.acceptInvitation(user, invitationId))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> org.assertj.core.api.Assertions.assertThat(exception.status().value()).isEqualTo(403));
    }
}
