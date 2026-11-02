package br.com.operadorzero.feed;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FeedDtos {
    private FeedDtos() {}

    public record FeedPage(List<FeedPostResponse> items, String nextCursor) {}

    public record FeedPostResponse(UUID id, String postType, AuthorSummary author, String caption, List<String> hashtags,
                                   List<MediaResponse> media, PlaceSummary field, TeamSummary team, Counts counts,
                                   ViewerState viewer, Instant publishedAt) {}

    public record AuthorSummary(String username, String callsign, String rankCode) {}

    public record PlaceSummary(UUID id, String name) {}

    public record TeamSummary(UUID id, String name, String acronym) {}

    public record Counts(long onTarget, long eliminated, long comments, long shares, long saves, long decorations) {}

    public record ViewerState(boolean onTarget, boolean eliminated, boolean saved, boolean followsAuthor, boolean ownPost) {}

    /** URLs relativas a raiz publica de midias; o cliente resolve contra a origem da API. */
    public record MediaResponse(UUID id, String kind, int width, int height, Integer durationMs, String posterUrl,
                                String blurPlaceholder, String hlsUrl, String mp4Url, List<ImageSource> images) {}

    public record ImageSource(int width, String url) {}
}
