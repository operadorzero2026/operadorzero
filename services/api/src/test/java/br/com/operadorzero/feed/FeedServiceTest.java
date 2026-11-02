package br.com.operadorzero.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.operadorzero.feed.FeedDtos.AuthorSummary;
import br.com.operadorzero.feed.FeedDtos.Counts;
import br.com.operadorzero.feed.FeedDtos.FeedPage;
import br.com.operadorzero.feed.FeedDtos.MediaResponse;
import br.com.operadorzero.feed.FeedDtos.ViewerState;
import br.com.operadorzero.feed.FeedRepository.MediaRow;
import br.com.operadorzero.feed.FeedRepository.PostRow;
import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.shared.web.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class FeedServiceTest {
    private final FeedRepository repository = mock(FeedRepository.class);
    private final FeedService service = new FeedService(repository, new ObjectMapper(), "https://cdn.test/media/");
    private final AuthenticatedUser user = new AuthenticatedUser(7L, UUID.randomUUID(), "op@example.test", "op",
        "Operador", "OP", List.of("USER"));

    @Test
    void returnsACursorOnlyWhenThereIsAnotherPageAndResumesFromIt() {
        List<PostRow> rows = IntStream.range(0, 3).mapToObj(i -> post(i, Instant.parse("2026-11-02T12:00:00Z").minusSeconds(i))).toList();
        when(repository.chronological(eq(7L), eq(false), isNull(), isNull(), eq(3))).thenReturn(rows);

        FeedPage first = service.page(user, "for-you", null, 2);

        assertThat(first.items()).hasSize(2);
        assertThat(first.nextCursor()).isNotNull();
        when(repository.chronological(eq(7L), eq(false), any(), any(), eq(3))).thenReturn(List.of(rows.get(2)));
        FeedPage second = service.page(user, "for-you", first.nextCursor(), 2);
        verify(repository).chronological(7L, false, rows.get(1).publishedAt(), rows.get(1).publicId(), 3);
        assertThat(second.items()).hasSize(1);
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    void followingTabOnlyAsksForFollowedAuthorsAndLimitIsCapped() {
        when(repository.chronological(anyLong(), anyBoolean(), any(), any(), anyInt())).thenReturn(List.of());

        service.page(user, "following", null, 500);

        verify(repository).chronological(7L, true, null, null, FeedService.MAX_LIMIT + 1);
    }

    @Test
    void rejectsUnknownTabsAndTamperedCursors() {
        assertThatThrownBy(() -> service.page(user, "trending", null, null))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("INVALID_FEED_TAB"));
        assertThatThrownBy(() -> service.page(user, "for-you", "not-a-cursor", null))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("INVALID_CURSOR"));
    }

    @Test
    void mapsRenditionsToPublicUrlsPickingTheSmallestMp4AsFallback() {
        MediaRow video = new MediaRow(UUID.randomUUID(), "VIDEO", 1080, 1920, 15000, "v/ab12", """
            [{"type":"hls","path":"master.m3u8"},{"type":"mp4","path":"720.mp4","height":1280},
             {"type":"mp4","path":"360.mp4","height":640},{"type":"image","path":"1080.webp","width":1080},
             {"type":"image","path":"540.webp","width":540}]""", "poster.webp", "data:image/webp;base64,AA");

        MediaResponse media = service.media(video);

        assertThat(media.hlsUrl()).isEqualTo("https://cdn.test/media/v/ab12/master.m3u8");
        assertThat(media.mp4Url()).isEqualTo("https://cdn.test/media/v/ab12/360.mp4");
        assertThat(media.posterUrl()).isEqualTo("https://cdn.test/media/v/ab12/poster.webp");
        assertThat(media.images()).extracting(FeedDtos.ImageSource::width).containsExactly(540, 1080);
    }

    @Test
    void attachesMediaAndHashtagsToTheirPosts() {
        PostRow row = post(1, Instant.parse("2026-11-02T12:00:00Z"));
        when(repository.chronological(anyLong(), anyBoolean(), any(), any(), anyInt())).thenReturn(List.of(row));
        when(repository.hashtags(List.of(1L))).thenReturn(Map.of(1L, List.of("cqb", "milsim")));
        when(repository.media(List.of(1L))).thenReturn(Map.of(1L, List.of(new MediaRow(UUID.randomUUID(), "IMAGE", 1080, 1350,
            null, "i/cd34", "[{\"type\":\"image\",\"path\":\"1080.webp\",\"width\":1080}]", null, null))));

        FeedPage page = service.page(user, null, null, null);

        assertThat(page.items().getFirst().hashtags()).containsExactly("cqb", "milsim");
        assertThat(page.items().getFirst().media().getFirst().images().getFirst().url()).isEqualTo("https://cdn.test/media/i/cd34/1080.webp");
    }

    private PostRow post(long id, Instant publishedAt) {
        return new PostRow(id, UUID.randomUUID(), "MEDIA", "post " + id, publishedAt,
            new AuthorSummary("op" + id, "OP" + id, "RECRUTA"), null, null,
            new Counts(0, 0, 0, 0, 0, 0), new ViewerState(false, false, false, false, false));
    }
}
