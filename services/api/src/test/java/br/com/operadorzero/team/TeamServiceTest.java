package br.com.operadorzero.team;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;

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

    @Test
    void captainCanUploadSanitizedPngLogo() throws Exception {
        when(repository.activeMembership(user.internalId(), teamId))
            .thenReturn(Optional.of(new MembershipRow(20L, user.internalId(), "CAPTAIN", teamId)));
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);

        service.saveLogo(user, teamId, new MockMultipartFile("file", "logo.png", "image/png", output.toByteArray()));

        verify(repository).saveLogo(eq(20L), eq(user.internalId()), eq("image/png"), any(byte[].class), eq(clock.instant()));
    }

    @Test
    void rejectsLogoThatIsNotARealImage() {
        when(repository.activeMembership(user.internalId(), teamId))
            .thenReturn(Optional.of(new MembershipRow(20L, user.internalId(), "CAPTAIN", teamId)));

        assertThatThrownBy(() -> service.saveLogo(user, teamId,
            new MockMultipartFile("file", "fake.png", "image/png", "not-an-image".getBytes())))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo("INVALID_TEAM_LOGO"));
    }

    @Test
    void joinRequestNeedsAnOpenTeam() {
        when(repository.teamInternalId(teamId)).thenReturn(Optional.of(20L));
        when(repository.findTeam(20L)).thenReturn(Optional.of(new TeamRepository.TeamRow(20L, teamId, "Alfa", "ALF",
            "Campinas", "SP", "Milsim", false, null, "INVITE_ONLY", 1)));
        when(repository.activeMembership(user.internalId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestToJoin(user, teamId, new TeamDtos.JoinRequestCreate(null), mock(HttpServletRequest.class)))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo("TEAM_NOT_RECRUITING"));
        verify(repository, org.mockito.Mockito.never()).createJoinRequest(org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(), any(), any());
    }

    @Test
    void managerCannotRemoveAnotherManagerOrTheCaptain() {
        UUID other = UUID.randomUUID();
        when(repository.activeMembership(user.internalId(), teamId))
            .thenReturn(Optional.of(new MembershipRow(20L, user.internalId(), "MANAGER", teamId)));
        when(repository.memberByOperator(20L, other)).thenReturn(Optional.of(new MembershipRow(20L, 99L, "MANAGER", teamId)));

        assertThatThrownBy(() -> service.removeMember(user, teamId, other))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> org.assertj.core.api.Assertions.assertThat(exception.status().value()).isEqualTo(403));
        verify(repository, org.mockito.Mockito.never()).leave(org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    void acceptingRequestOfOperatorWhoJoinedElsewhereCancelsIt() {
        UUID requestId = UUID.randomUUID();
        when(repository.lockJoinRequest(requestId))
            .thenReturn(Optional.of(new TeamRepository.JoinRequestRow(5L, requestId, 20L, 77L, "PENDING", teamId)));
        when(repository.activeMembership(user.internalId(), teamId))
            .thenReturn(Optional.of(new MembershipRow(20L, user.internalId(), "CAPTAIN", teamId)));
        when(repository.activeMembership(77L)).thenReturn(Optional.of(new MembershipRow(30L, 77L, "MEMBER", UUID.randomUUID())));

        assertThatThrownBy(() -> service.respondJoinRequest(user, requestId, true))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo("OPERATOR_HAS_TEAM"));
        verify(repository).respondJoinRequest(5L, "CANCELLED", user.internalId(), clock.instant());
        verify(repository, org.mockito.Mockito.never()).addMember(org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(), any(), any(), any());
    }
}
