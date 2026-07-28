package br.com.operadorzero.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.operadorzero.community.CommunityDtos.CreatePostRequest;
import br.com.operadorzero.community.CommunityDtos.ReportRequest;
import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.web.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommunityServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-28T15:00:00Z");

    @Test
    void createPostRequiresAnActiveCategory() {
        CommunityRepository repository = mock(CommunityRepository.class);
        AuthenticatedUser user = user(List.of("USER"));
        UUID key = UUID.randomUUID();
        when(repository.createPost(user.internalId(), "duvidas", "Título válido", "Descrição", key, NOW))
            .thenReturn(null);
        CommunityService service = service(repository);

        assertThatThrownBy(() -> service.createPost(user,
            new CreatePostRequest("duvidas", "Título válido", "Descrição", key)))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.code()).isEqualTo("INVALID_COMMUNITY_CATEGORY"));
    }

    @Test
    void duplicateOpenReportIsRejected() {
        CommunityRepository repository = mock(CommunityRepository.class);
        AuthenticatedUser user = user(List.of("USER"));
        UUID postId = UUID.randomUUID();
        when(repository.reportPost(postId, user.internalId(), "SPAM", null, NOW)).thenReturn(false);
        CommunityService service = service(repository);

        assertThatThrownBy(() -> service.reportPost(user, postId, new ReportRequest("SPAM", null)))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.code()).isEqualTo("REPORT_ALREADY_OPEN"));
    }

    @Test
    void regularUserCannotOpenTheModerationQueue() {
        CommunityService service = service(mock(CommunityRepository.class));

        assertThatThrownBy(() -> service.reports(user(List.of("USER"))))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.code()).isEqualTo("ACCESS_DENIED"));
    }

    @Test
    void voteToggleRemovesAnExistingVoteWithoutCreatingAnother() {
        CommunityRepository repository = mock(CommunityRepository.class);
        AuthenticatedUser user = user(List.of("USER"));
        UUID postId = UUID.randomUUID();
        when(repository.removeVote(postId, user.internalId())).thenReturn(true);
        when(repository.voteCount(postId)).thenReturn(4);
        CommunityService service = service(repository);

        var response = service.vote(user, postId);

        assertThat(response.active()).isFalse();
        assertThat(response.count()).isEqualTo(4);
        verify(repository).removeVote(postId, user.internalId());
    }

    @Test
    void topSortUsesOnlyTheFixedRepositoryOrder() {
        CommunityRepository repository = mock(CommunityRepository.class);
        CommunityService service = service(repository);

        service.posts(null, "", "", "TOP", 0, 20);

        verify(repository).search(0L, false, "", "", "vote_count DESC, p.created_at DESC, p.id DESC", 20, 0);
        verify(repository).count("", "");
    }

    private CommunityService service(CommunityRepository repository) {
        return new CommunityService(repository, mock(AuditEventRepository.class), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AuthenticatedUser user(List<String> roles) {
        return new AuthenticatedUser(42L, UUID.randomUUID(), "operator@example.test", "operator",
            "Operator", "ZERO", roles);
    }
}
