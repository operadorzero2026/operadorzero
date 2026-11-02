package br.com.operadorzero.media;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.media.MediaDtos.MediaStatusResponse;
import br.com.operadorzero.media.MediaDtos.StartUploadRequest;
import br.com.operadorzero.media.MediaDtos.UploadResponse;
import br.com.operadorzero.media.MediaRepository.UploadRow;
import br.com.operadorzero.shared.ratelimit.UserActionLimiter;
import br.com.operadorzero.shared.web.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Envio retomavel em pedacos: o cliente manda cada pedaco no offset que o servidor ja recebeu.
 * Repetir um pedaco ja gravado e inofensivo; pular um pedaco e recusado.
 */
@Service
public class MediaUploadService {
    private final MediaRepository repository;
    private final MediaSettings settings;
    private final UserActionLimiter limiter;
    private final Clock clock;

    @Autowired
    public MediaUploadService(MediaRepository repository, MediaSettings settings, UserActionLimiter limiter) {
        this(repository, settings, limiter, Clock.systemUTC());
    }

    MediaUploadService(MediaRepository repository, MediaSettings settings, UserActionLimiter limiter, Clock clock) {
        this.repository = repository;
        this.settings = settings;
        this.limiter = limiter;
        this.clock = clock;
    }

    @Transactional
    public UploadResponse start(AuthenticatedUser user, StartUploadRequest request) {
        if (request.totalBytes() > MediaSettings.maxBytes(request.kind())) {
            throw BusinessException.badRequest("MEDIA_TOO_LARGE", "VIDEO".equals(request.kind())
                ? "O vídeo pode ter no máximo 300 MB." : "A foto pode ter no máximo 20 MB.");
        }
        if (!VideoFilters.isKnown(request.filterCode()) || ("IMAGE".equals(request.kind()) && request.filterCode() != null && !request.filterCode().isBlank())) {
            throw BusinessException.badRequest("UNKNOWN_FILTER", "Filtro indisponível.");
        }
        limiter.check("media-upload", user.internalId(), 30, Duration.ofHours(1));
        Instant now = clock.instant();
        UploadRow row = repository.createUpload(user.internalId(), request.purpose(), request.kind(), request.contentType(),
            request.totalBytes(), request.filterCode(), request.crop(), MediaSettings.CHUNK_BYTES, now, now.plus(MediaSettings.UPLOAD_TTL));
        return response(row);
    }

    @Transactional(readOnly = true)
    public UploadResponse status(AuthenticatedUser user, UUID uploadId) {
        return response(repository.findUpload(uploadId, user.internalId()).orElseThrow(MediaUploadService::notFound));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public UploadResponse putChunk(AuthenticatedUser user, UUID uploadId, long offset, long contentLength, InputStream body) {
        Instant now = clock.instant();
        UploadRow row = open(user, uploadId, now);
        if (offset < row.receivedBytes() && offset + contentLength <= row.receivedBytes()) return response(row);
        if (offset != row.receivedBytes()) {
            throw BusinessException.conflict("UPLOAD_OFFSET_MISMATCH", "O envio precisa continuar a partir de " + row.receivedBytes() + " bytes.");
        }
        long expected = Math.min(row.chunkBytes(), row.totalBytes() - offset);
        if (contentLength != expected) {
            throw BusinessException.badRequest("UPLOAD_CHUNK_SIZE", "Tamanho do pedaço inválido.");
        }
        Path part = partFile(row);
        try {
            Files.createDirectories(part.getParent());
            try (OutputStream out = Files.newOutputStream(part, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                long copied = copyExactly(body, out, expected);
                if (copied != expected) throw BusinessException.badRequest("UPLOAD_CHUNK_SIZE", "O pedaço chegou incompleto.");
            }
            if (offset == 0) rejectIfNotMedia(row, part, now);
        } catch (IOException exception) {
            truncate(part, offset);
            throw new UncheckedIOException(exception);
        } catch (BusinessException exception) {
            truncate(part, offset);
            throw exception;
        }
        repository.updateReceived(row.uploadId(), offset + expected, now);
        return new UploadResponse(row.uploadPublicId(), row.assetPublicId(), row.chunkBytes(), offset + expected, row.totalBytes(), row.status());
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public MediaStatusResponse complete(AuthenticatedUser user, UUID uploadId) {
        Instant now = clock.instant();
        UploadRow row = open(user, uploadId, now);
        if (row.receivedBytes() != row.totalBytes()) {
            throw BusinessException.conflict("UPLOAD_INCOMPLETE", "Ainda faltam partes do arquivo.");
        }
        Path part = partFile(row);
        FileSniffer.Detected detected = detect(row, part, now);
        try {
            Path queued = sourceFile(settings, row.assetPublicId());
            Files.createDirectories(queued.getParent());
            Files.move(part, queued, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        repository.completeUpload(row.uploadId(), row.assetId(), detected.type(), now);
        return new MediaStatusResponse(row.assetPublicId(), row.kind(), "QUEUED", null);
    }

    @Transactional(readOnly = true)
    public MediaStatusResponse mediaStatus(AuthenticatedUser user, UUID mediaId) {
        return repository.status(mediaId, user.internalId()).orElseThrow(() -> BusinessException.notFound("Mídia não encontrada."));
    }

    /** Arquivo original aguardando o processamento; e apagado assim que as versoes finais ficam prontas. */
    static Path sourceFile(MediaSettings settings, UUID assetId) {
        return settings.work().resolve("queue").resolve(assetId + ".src");
    }

    private UploadRow open(AuthenticatedUser user, UUID uploadId, Instant now) {
        UploadRow row = repository.lockUpload(uploadId, user.internalId()).orElseThrow(MediaUploadService::notFound);
        if (!"OPEN".equals(row.status())) throw BusinessException.conflict("UPLOAD_CLOSED", "Este envio já foi encerrado.");
        if (row.expiresAt().isBefore(now)) throw BusinessException.conflict("UPLOAD_EXPIRED", "O envio expirou. Comece de novo.");
        return row;
    }

    private void rejectIfNotMedia(UploadRow row, Path part, Instant now) throws IOException {
        var detected = FileSniffer.sniff(part);
        if (detected.isEmpty() || !detected.get().kind().equals(row.kind())) fail(row, part, now);
    }

    private FileSniffer.Detected detect(UploadRow row, Path part, Instant now) {
        try {
            var detected = FileSniffer.sniff(part);
            if (detected.isEmpty() || !detected.get().kind().equals(row.kind())) fail(row, part, now);
            return detected.get();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void fail(UploadRow row, Path part, Instant now) throws IOException {
        Files.deleteIfExists(part);
        repository.failUpload(row.uploadId(), row.assetId(), "UNSUPPORTED_TYPE", now);
        throw BusinessException.badRequest("UNSUPPORTED_MEDIA_TYPE", "VIDEO".equals(row.kind())
            ? "Envie um vídeo MP4, MOV ou WebM." : "Envie uma foto JPG, PNG, WebP ou HEIC.");
    }

    private Path partFile(UploadRow row) {
        return settings.work().resolve("uploads").resolve(row.uploadPublicId() + ".part");
    }

    private static long copyExactly(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int read;
        while ((read = in.read(buffer, 0, (int) Math.min(buffer.length, limit + 1 - total))) > 0) {
            total += read;
            if (total > limit) return total;
            out.write(buffer, 0, read);
        }
        return total;
    }

    private static void truncate(Path part, long size) {
        try (var channel = Files.newByteChannel(part, StandardOpenOption.WRITE)) {
            channel.truncate(size);
        } catch (IOException ignored) {
            // Sem o arquivo nao ha o que desfazer; o proximo pedaco sera recusado pelo offset.
        }
    }

    private static UploadResponse response(UploadRow row) {
        return new UploadResponse(row.uploadPublicId(), row.assetPublicId(), row.chunkBytes(), row.receivedBytes(), row.totalBytes(), row.status());
    }

    private static BusinessException notFound() {
        return BusinessException.notFound("Envio não encontrado.");
    }
}
