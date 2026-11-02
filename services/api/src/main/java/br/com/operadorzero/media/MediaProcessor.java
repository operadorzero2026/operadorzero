package br.com.operadorzero.media;

import br.com.operadorzero.media.Ffmpeg.MediaRejected;
import br.com.operadorzero.media.Ffmpeg.Probe;
import br.com.operadorzero.media.MediaPlans.Rendition;
import br.com.operadorzero.media.MediaPlans.Size;
import br.com.operadorzero.media.MediaPlans.Window;
import br.com.operadorzero.media.MediaRepository.AssetRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fila de processamento: um arquivo por vez, para nao disputar CPU com os outros servicos da VPS.
 * Toda saida e reencodada (H.264/AAC ou WebP) com -map_metadata -1: EXIF, GPS e dados do aparelho nao sobrevivem.
 */
@Component
@ConditionalOnProperty(name = "app.media.processing-enabled", havingValue = "true", matchIfMissing = true)
public class MediaProcessor {
    private static final Logger log = LoggerFactory.getLogger(MediaProcessor.class);
    private static final Duration VIDEO_TIMEOUT = Duration.ofMinutes(20);
    private static final Duration IMAGE_TIMEOUT = Duration.ofMinutes(2);
    private static final double MIN_PHOTO_ASPECT = 0.99;
    private static final double MAX_PHOTO_ASPECT = 1.80;

    private final MediaRepository repository;
    private final MediaSettings settings;
    private final MediaStorage storage;
    private final Ffmpeg ffmpeg;
    private final ObjectMapper json;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Autowired
    public MediaProcessor(MediaRepository repository, MediaSettings settings, MediaStorage storage, Ffmpeg ffmpeg, ObjectMapper json,
                          ApplicationEventPublisher events, TransactionTemplate transactions) {
        this(repository, settings, storage, ffmpeg, json, events, transactions, Clock.systemUTC());
    }

    MediaProcessor(MediaRepository repository, MediaSettings settings, MediaStorage storage, Ffmpeg ffmpeg, ObjectMapper json,
                   ApplicationEventPublisher events, TransactionTemplate transactions, Clock clock) {
        this.repository = repository;
        this.settings = settings;
        this.storage = storage;
        this.ffmpeg = ffmpeg;
        this.json = json;
        this.events = events;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${app.media.poll-initial-ms:10000}", fixedDelayString = "${app.media.poll-ms:3000}")
    public void drainQueue() {
        repository.requeueStale(clock.instant().minus(Duration.ofMinutes(30)), clock.instant());
        for (int i = 0; i < 20; i++) {
            var next = repository.claimNext(clock.instant());
            if (next.isEmpty()) return;
            process(next.get());
        }
    }

    void process(AssetRow asset) {
        Path source = MediaUploadService.sourceFile(settings, asset.publicId());
        Path job = settings.work().resolve("jobs").resolve(asset.publicId().toString());
        Path out = job.resolve("out");
        try {
            LocalDiskMediaStorage.deleteTree(job);
            Files.createDirectories(out);
            Result result = "VIDEO".equals(asset.kind()) ? video(asset, source, out) : image(asset, source, out);
            String prefix = ("VIDEO".equals(asset.kind()) ? "v" : "i") + "/" + UUID.randomUUID().toString().substring(0, 2) + "/" + UUID.randomUUID();
            storage.publish(out, prefix);
            Instant now = clock.instant();
            transactions.executeWithoutResult(status -> {
                repository.markReady(asset.id(), result.width(), result.height(), result.durationMs(), result.capturedAt(), prefix,
                    write(result.renditions()), result.poster(), result.blur(), now);
                events.publishEvent(new MediaProcessedEvent(asset.id(), asset.publicId(), asset.ownerUserId(), asset.purpose(), true, null));
            });
            Files.deleteIfExists(source);
        } catch (MediaRejected rejected) {
            fail(asset, source, rejected.code());
        } catch (IOException | RuntimeException exception) {
            log.warn("media_processing_error asset={} attempt={}", asset.publicId(), asset.attempts(), exception);
            if (asset.attempts() >= 3) fail(asset, source, "PROCESSING_FAILED");
            else repository.requeue(asset.id(), clock.instant());
        } finally {
            try { LocalDiskMediaStorage.deleteTree(job); } catch (IOException ignored) { /* limpeza na proxima execucao */ }
        }
    }

    private void fail(AssetRow asset, Path source, String code) {
        log.info("media_rejected asset={} code={}", asset.publicId(), code);
        transactions.executeWithoutResult(status -> {
            repository.markFailed(asset.id(), code, clock.instant());
            events.publishEvent(new MediaProcessedEvent(asset.id(), asset.publicId(), asset.ownerUserId(), asset.purpose(), false, code));
        });
        try { Files.deleteIfExists(source); } catch (IOException ignored) { /* removido na limpeza */ }
    }

    private Result video(AssetRow asset, Path source, Path out) throws IOException, MediaRejected {
        Probe probe = ffmpeg.probe(source);
        if (probe.durationMs() == null || probe.durationMs() <= 0) throw new MediaRejected("INVALID_VIDEO");
        if (probe.durationMs() > MediaSettings.maxDurationMs(asset.purpose()) + 500) throw new MediaRejected("VIDEO_TOO_LONG");
        String filter = VideoFilters.graph(asset.filterCode()).orElse(null);
        Window crop = MediaPlans.cropWindow(probe.width(), probe.height(), asset.crop());
        int width = crop == null ? probe.width() : crop.width();
        int height = crop == null ? probe.height() : crop.height();
        List<Rendition> ladder = MediaPlans.ladder(width, height);
        ffmpeg.run(MediaPlans.hlsArgs(source.toAbsolutePath(), crop, ladder, probe.hasAudio(), filter), VIDEO_TIMEOUT, out);
        Rendition smallest = ladder.getFirst();
        ffmpeg.run(MediaPlans.fallbackMp4Args(source, out.resolve("fallback.mp4"), crop, smallest, probe.hasAudio(), filter), VIDEO_TIMEOUT);
        Size poster = MediaPlans.fit(width, height, Math.min(720, Math.min(width, height)));
        ffmpeg.run(MediaPlans.posterArgs(source, out.resolve("poster.webp"), crop, filter, poster,
            probe.durationMs() > 1000 ? 0.5 : 0), IMAGE_TIMEOUT);
        String blur = placeholder(out.resolve("poster.webp"), out);
        List<Map<String, Object>> renditions = new ArrayList<>();
        renditions.add(Map.of("type", "hls", "path", "master.m3u8"));
        renditions.add(Map.of("type", "mp4", "path", "fallback.mp4", "height", smallest.height()));
        Rendition largest = ladder.getLast();
        return new Result(largest.width(), largest.height(), probe.durationMs(), probe.capturedAt(), renditions, "poster.webp", blur);
    }

    private Result image(AssetRow asset, Path source, Path out) throws IOException, MediaRejected {
        Probe probe = ffmpeg.probe(source);
        if ((long) probe.width() * probe.height() > MediaSettings.MAX_IMAGE_PIXELS) throw new MediaRejected("IMAGE_TOO_LARGE");
        double aspect = (double) probe.height() / probe.width();
        if (aspect < MIN_PHOTO_ASPECT || aspect > MAX_PHOTO_ASPECT) throw new MediaRejected("ASPECT_NOT_ALLOWED");
        List<Map<String, Object>> renditions = new ArrayList<>();
        int largest = 0;
        for (int width : List.of(540, 1080)) {
            int target = Math.min(width, probe.width() - probe.width() % 2);
            if (target <= largest) continue;
            ffmpeg.run(MediaPlans.imageArgs(source, out.resolve(width + ".webp"), target), IMAGE_TIMEOUT);
            renditions.add(Map.of("type", "image", "path", width + ".webp", "width", target));
            largest = target;
        }
        String blur = placeholder(out.resolve(renditions.getFirst().get("path").toString()), out);
        int height = (int) Math.round((double) largest * probe.height() / probe.width());
        return new Result(largest, height, null, null, renditions, null, blur);
    }

    private String placeholder(Path from, Path out) throws IOException, MediaRejected {
        Path tiny = out.resolve("blur.tmp.webp");
        ffmpeg.run(MediaPlans.placeholderArgs(from, tiny), IMAGE_TIMEOUT);
        String data = "data:image/webp;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(tiny));
        Files.delete(tiny);
        return data;
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Result(int width, int height, Integer durationMs, Instant capturedAt, List<Map<String, Object>> renditions,
                          String poster, String blur) {}
}
