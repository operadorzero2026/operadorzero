package br.com.operadorzero.feed;

import br.com.operadorzero.feed.FeedDtos.FeedPage;
import br.com.operadorzero.feed.FeedDtos.FeedPostResponse;
import br.com.operadorzero.feed.FeedDtos.ImageSource;
import br.com.operadorzero.feed.FeedDtos.MediaResponse;
import br.com.operadorzero.feed.FeedRepository.MediaRow;
import br.com.operadorzero.feed.FeedRepository.PostRow;
import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.shared.web.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedService {
    static final int DEFAULT_LIMIT = 8;
    static final int MAX_LIMIT = 20;
    private static final TypeReference<List<Rendition>> RENDITIONS = new TypeReference<>() {};

    private final FeedRepository repository;
    private final ObjectMapper json;
    private final String mediaBaseUrl;

    public FeedService(FeedRepository repository, ObjectMapper json, @Value("${app.media.public-base-url:/media}") String mediaBaseUrl) {
        this.repository = repository;
        this.json = json;
        this.mediaBaseUrl = mediaBaseUrl.endsWith("/") ? mediaBaseUrl.substring(0, mediaBaseUrl.length() - 1) : mediaBaseUrl;
    }

    @Transactional(readOnly = true)
    public FeedPage page(AuthenticatedUser user, String tab, String cursor, Integer limit) {
        return page(user, tab, null, cursor, limit);
    }

    @Transactional(readOnly = true)
    public FeedPage page(AuthenticatedUser user, String tab, String hashtag, String cursor, Integer limit) {
        boolean following = switch (tab == null ? "for-you" : tab) {
            case "for-you" -> false;
            case "following" -> true;
            default -> throw BusinessException.badRequest("INVALID_FEED_TAB", "Aba do feed inválida.");
        };
        int size = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(MAX_LIMIT, limit));
        Cursor position = Cursor.decode(cursor);
        // Ranking "Para voce" entra na etapa 7; ate la as duas abas sao cronologicas.
        String tag = hashtag == null || hashtag.isBlank() ? null : FeedText.normalizeTag(hashtag);
        List<PostRow> rows = tag == null
            ? repository.chronological(user.internalId(), following, position == null ? null : position.publishedAt(), position == null ? null : position.id(), size + 1)
            : repository.query(user.internalId(), following, tag, null, false, position == null ? null : position.publishedAt(),
                position == null ? null : position.id(), size + 1);
        boolean more = rows.size() > size;
        List<PostRow> page = more ? rows.subList(0, size) : rows;
        List<FeedPostResponse> items = responses(page);
        String next = more ? new Cursor(page.getLast().publishedAt(), page.getLast().publicId()).encode() : null;
        return new FeedPage(items, next);
    }

    /** Um post para quem esta logado (link compartilhado aberto no app). */
    @Transactional(readOnly = true)
    public FeedPostResponse post(AuthenticatedUser user, UUID postId) {
        return responses(repository.query(user.internalId(), false, null, postId, false, null, null, 1)).stream().findFirst()
            .orElseThrow(() -> BusinessException.notFound("Post não encontrado."));
    }

    /** Pagina publica do link compartilhado: so posts com compartilhamento publico e sem estado de quem ve. */
    @Transactional(readOnly = true)
    public java.util.Optional<FeedPostResponse> publicPost(UUID postId) {
        return responses(repository.query(-1, false, null, postId, true, null, null, 1)).stream().findFirst();
    }

    private List<FeedPostResponse> responses(List<PostRow> page) {
        List<Long> ids = page.stream().map(PostRow::id).toList();
        Map<Long, List<MediaRow>> media = repository.media(ids);
        Map<Long, List<String>> hashtags = repository.hashtags(ids);
        return page.stream().map(row -> new FeedPostResponse(row.publicId(), row.postType(), row.author(),
            row.caption(), hashtags.getOrDefault(row.id(), List.of()),
            media.getOrDefault(row.id(), List.of()).stream().map(this::media).toList(),
            row.field(), row.team(), row.counts(), row.viewer(), row.publishedAt())).toList();
    }

    public MediaResponse media(MediaRow row) {
        List<Rendition> renditions;
        try {
            renditions = json.readValue(row.renditionsJson(), RENDITIONS);
        } catch (Exception exception) {
            renditions = List.of();
        }
        String hls = null;
        String mp4 = null;
        int mp4Height = 0;
        List<ImageSource> images = new ArrayList<>();
        for (Rendition rendition : renditions) {
            String url = url(row.storagePrefix(), rendition.path());
            switch (rendition.type()) {
                case "hls" -> hls = url;
                case "mp4" -> {
                    // MP4 de reserva: o de menor altura, que toca rapido onde nao ha HLS.
                    int height = rendition.height() == null ? Integer.MAX_VALUE : rendition.height();
                    if (mp4 == null || height < mp4Height) { mp4 = url; mp4Height = height; }
                }
                case "image" -> images.add(new ImageSource(rendition.width() == null ? row.width() : rendition.width(), url));
                default -> { }
            }
        }
        images.sort(Comparator.comparingInt(ImageSource::width));
        return new MediaResponse(row.publicId(), row.kind(), row.width(), row.height(), row.durationMs(),
            row.posterKey() == null ? null : url(row.storagePrefix(), row.posterKey()), row.blurPlaceholder(), hls, mp4, images);
    }

    private String url(String prefix, String path) {
        return mediaBaseUrl + "/" + prefix + "/" + path;
    }

    record Rendition(String type, String path, Integer width, Integer height) {}

    record Cursor(Instant publishedAt, UUID id) {
        String encode() {
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((publishedAt + "|" + id).getBytes(StandardCharsets.UTF_8));
        }

        static Cursor decode(String value) {
            if (value == null || value.isBlank()) return null;
            try {
                String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", 2);
                return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
            } catch (RuntimeException exception) {
                throw BusinessException.badRequest("INVALID_CURSOR", "Posição do feed inválida.");
            }
        }
    }
}
