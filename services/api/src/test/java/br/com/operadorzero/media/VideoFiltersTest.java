package br.com.operadorzero.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VideoFiltersTest {
    private static final Instant NOW = Instant.parse("2026-11-03T22:30:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"night_vision", "thermal", "scope", "helmet"})
    void tacticalFiltersAreKnownAndLinear(String code) {
        assertThat(VideoFilters.isKnown(code)).isTrue();
        String graph = VideoFilters.graph(code, NOW).orElseThrow();
        // Entra entre o recorte e o split: nao pode ter rotulos nem cadeias separadas.
        assertThat(graph).doesNotContain("[").doesNotContain(";");
    }

    @Test
    void unknownOrMissingFilterIsHandled() {
        assertThat(VideoFilters.isKnown("x_ray")).isFalse();
        assertThat(VideoFilters.isKnown(null)).isTrue();
        assertThat(VideoFilters.isKnown("")).isTrue();
        assertThat(VideoFilters.graph("x_ray", NOW)).isEmpty();
        assertThat(VideoFilters.graph(null, NOW)).isEmpty();
    }

    @Test
    void helmetClockStartsAtBrasiliaTimeOfProcessing() {
        // 22:30 UTC = 19:30 em Brasilia -> segundos do relogio local tratados como UTC pelo gmtime do drawtext.
        long expected = java.time.LocalDateTime.of(2026, 11, 3, 19, 30).toEpochSecond(java.time.ZoneOffset.UTC);
        assertThat(VideoFilters.graph("helmet", NOW).orElseThrow()).contains("gmtime\\:" + expected);
    }

    /** Roda o ffmpeg de verdade quando FFMPEG_PATH existe (pega erros de escape do filtergraph). */
    @ParameterizedTest
    @ValueSource(strings = {"night_vision", "thermal", "scope", "helmet"})
    void ffmpegAcceptsEveryFilter(String code, @TempDir Path dir) throws Exception {
        String ffmpeg = System.getenv("FFMPEG_PATH");
        assumeTrue(ffmpeg != null && Files.isExecutable(Path.of(ffmpeg)), "ffmpeg indisponivel neste ambiente");
        Path source = dir.resolve("src.mp4");
        run(List.of(ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "testsrc2=size=360x640:rate=15:duration=1",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", source.toString()));
        List<String> args = new ArrayList<>(List.of(ffmpeg, "-hide_banner", "-loglevel", "error", "-y"));
        args.addAll(MediaPlans.posterArgs(source, dir.resolve("poster.webp"), null, VideoFilters.graph(code, NOW).orElseThrow(),
            new MediaPlans.Size(360, 640), 0.5));
        run(args);
        assertThat(Files.size(dir.resolve("poster.webp"))).isPositive();
    }

    private static void run(List<String> command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor()).as(output).isZero();
    }
}
