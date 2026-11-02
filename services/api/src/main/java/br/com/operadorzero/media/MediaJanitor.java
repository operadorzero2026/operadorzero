package br.com.operadorzero.media;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/** Limpa envios abandonados e midias que nunca viraram post nem Recon (libera disco e nao guarda o que o usuario desistiu). */
@Configuration
@ConditionalOnProperty(name = "app.media.processing-enabled", havingValue = "true", matchIfMissing = true)
public class MediaJanitor {
    private static final Logger log = LoggerFactory.getLogger(MediaJanitor.class);
    private final MediaRepository repository;
    private final MediaSettings settings;
    private final MediaStorage storage;
    private final Clock clock = Clock.systemUTC();

    public MediaJanitor(MediaRepository repository, MediaSettings settings, MediaStorage storage) {
        this.repository = repository;
        this.settings = settings;
        this.storage = storage;
    }

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT30M")
    public void clean() {
        Instant now = clock.instant();
        for (var upload : repository.expiredUploads(now)) {
            try {
                Files.deleteIfExists(settings.work().resolve("uploads").resolve(upload.uploadPublicId() + ".part"));
                repository.expireUpload(upload.uploadId(), upload.assetId(), now);
            } catch (IOException exception) {
                log.warn("media_cleanup_upload_failed upload={}", upload.uploadPublicId(), exception);
            }
        }
        for (var asset : repository.orphanedReady(now.minus(Duration.ofHours(24)))) {
            try {
                if (asset.storagePrefix() != null) storage.delete(asset.storagePrefix());
                repository.markDeleted(asset.id(), now);
            } catch (IOException | IllegalArgumentException exception) {
                log.warn("media_cleanup_asset_failed asset={}", asset.publicId(), exception);
            }
        }
    }
}
