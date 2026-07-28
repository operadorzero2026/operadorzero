package br.com.operadorzero.operator;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.operadorzero.identity.AuthRateLimiter;
import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operator.OperatorDtos.UpdateProfileRequest;
import br.com.operadorzero.operator.OperatorRepository.ProfileRow;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.web.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperatorServiceTest {
    @Test
    void updateRejectsUsernameOwnedByAnotherAccount() {
        OperatorRepository repository = mock(OperatorRepository.class);
        AuthenticatedUser user = new AuthenticatedUser(10L, UUID.randomUUID(), "user@example.test", "oldname",
            "Operator", "Zero", List.of("OPERATOR"));
        when(repository.findProfile(user.internalId())).thenReturn(Optional.of(new ProfileRow(
            3L, UUID.randomUUID(), user.email(), "oldname", "Operator", "Zero", null,
            null, null, null, "LONE_WOLF", 0L)));
        when(repository.usernameExistsForOther("newname", user.internalId())).thenReturn(true);
        OperatorService service = new OperatorService(repository, new BrazilLocationValidator(),
            mock(AuditEventRepository.class), mock(AuthRateLimiter.class),
            Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC));

        UpdateProfileRequest request = new UpdateProfileRequest("Operator", "Zero", "newname", null,
            null, null, null, List.of(), "LONE_WOLF", 0L);

        assertThatThrownBy(() -> service.update(user, request))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo("USERNAME_UNAVAILABLE"));
    }
}
