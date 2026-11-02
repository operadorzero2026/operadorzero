package br.com.operadorzero.media;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Limites de midia (tabela da especificacao) e caminhos de trabalho. */
@Component
public class MediaSettings {
    public static final long MB = 1024L * 1024L;
    public static final long MAX_VIDEO_BYTES = 300 * MB;
    public static final long MAX_IMAGE_BYTES = 20 * MB;
    public static final int MAX_POST_VIDEO_MS = 90_000;
    public static final int MAX_RECON_VIDEO_MS = 30_000;
    public static final int CHUNK_BYTES = (int) (8 * MB);
    /** Evita "bombas" de descompressao: imagens acima disso sao recusadas. */
    public static final long MAX_IMAGE_PIXELS = 50_000_000L;
    public static final Duration UPLOAD_TTL = Duration.ofHours(6);

    private final Path root;
    private final Path work;
    private final String ffmpeg;
    private final String ffprobe;
    private final boolean lowPriority;

    public MediaSettings(@Value("${app.media.root}") String root,
                         @Value("${app.media.work-dir:}") String work,
                         @Value("${app.media.ffmpeg:ffmpeg}") String ffmpeg,
                         @Value("${app.media.ffprobe:ffprobe}") String ffprobe,
                         @Value("${app.media.low-priority:false}") boolean lowPriority) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.work = work.isBlank() ? this.root.resolveSibling(this.root.getFileName() + "-work") : Path.of(work).toAbsolutePath().normalize();
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
        this.lowPriority = lowPriority;
    }

    public Path root() { return root; }
    public Path work() { return work; }
    public String ffmpeg() { return ffmpeg; }
    public String ffprobe() { return ffprobe; }
    /** Na VPS o processamento roda com "nice" para nao disputar CPU com os outros servicos. */
    public boolean lowPriority() { return lowPriority; }

    public static long maxBytes(String kind) {
        return "VIDEO".equals(kind) ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES;
    }

    public static int maxDurationMs(String purpose) {
        return "RECON".equals(purpose) ? MAX_RECON_VIDEO_MS : MAX_POST_VIDEO_MS;
    }
}
