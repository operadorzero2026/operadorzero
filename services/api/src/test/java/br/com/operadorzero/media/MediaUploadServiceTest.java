package br.com.operadorzero.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.media.MediaDtos.StartUploadRequest;
import br.com.operadorzero.media.MediaRepository.UploadRow;
import br.com.operadorzero.shared.ratelimit.UserActionLimiter;
import br.com.operadorzero.shared.web.BusinessException;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaUploadServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    @TempDir Path root;
    private final MediaRepository repository = mock(MediaRepository.class);
    private final AuthenticatedUser user = new AuthenticatedUser(7L, UUID.randomUUID(), "op@example.test", "op", "Op", "OP", List.of("USER"));
    private final UUID uploadId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();

    private MediaUploadService service() {
        return new MediaUploadService(repository, new MediaSettings(root.resolve("media").toString(), "", "ffmpeg", "ffprobe", false),
            mock(UserActionLimiter.class), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void rejectsFilesAboveTheLimitBeforeAnyByteIsSent() {
        assertThatThrownBy(() -> service().start(user, new StartUploadRequest("POST", "VIDEO", "video/mp4", 301 * MediaSettings.MB, null, null)))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("MEDIA_TOO_LARGE"));
        assertThatThrownBy(() -> service().start(user, new StartUploadRequest("POST", "IMAGE", "image/jpeg", 21 * MediaSettings.MB, null, null)))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("MEDIA_TOO_LARGE"));
        assertThatThrownBy(() -> service().start(user, new StartUploadRequest("POST", "VIDEO", "video/mp4", 1000, "drawtext", null)))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("UNKNOWN_FILTER"));
        verify(repository, never()).createUpload(anyLong(), anyString(), anyString(), anyString(), anyLong(), any(), any(), any(Integer.class), any(), any());
    }

    @Test
    void chunksMustContinueFromTheReceivedOffsetAndRepeatsAreHarmless() {
        byte[] jpeg = jpeg(10);
        when(repository.lockUpload(uploadId, 7L)).thenReturn(Optional.of(upload("IMAGE", jpeg.length, 0)));
        var first = service().putChunk(user, uploadId, 0, jpeg.length, new ByteArrayInputStream(jpeg));
        assertThat(first.receivedBytes()).isEqualTo(jpeg.length);

        when(repository.lockUpload(uploadId, 7L)).thenReturn(Optional.of(upload("IMAGE", jpeg.length, jpeg.length)));
        assertThat(service().putChunk(user, uploadId, 0, jpeg.length, new ByteArrayInputStream(jpeg)).receivedBytes()).isEqualTo(jpeg.length);

        when(repository.lockUpload(uploadId, 7L)).thenReturn(Optional.of(upload("IMAGE", 100, 10)));
        assertThatThrownBy(() -> service().putChunk(user, uploadId, 50, 50, new ByteArrayInputStream(new byte[50])))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("UPLOAD_OFFSET_MISMATCH"));
    }

    @Test
    void aFileThatIsNotTheDeclaredKindIsRejectedOnTheFirstChunk() throws Exception {
        byte[] script = "#!/bin/sh\necho oi\n".getBytes();
        when(repository.lockUpload(uploadId, 7L)).thenReturn(Optional.of(upload("IMAGE", script.length, 0)));

        assertThatThrownBy(() -> service().putChunk(user, uploadId, 0, script.length, new ByteArrayInputStream(script)))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE"));
        verify(repository).failUpload(eq(1L), eq(2L), eq("UNSUPPORTED_TYPE"), any());
        verify(repository, never()).updateReceived(anyLong(), anyLong(), any());
        try (var files = Files.walk(root)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    void aShortChunkIsRefused() {
        when(repository.lockUpload(uploadId, 7L)).thenReturn(Optional.of(upload("IMAGE", 100, 0)));
        assertThatThrownBy(() -> service().putChunk(user, uploadId, 0, 40, new ByteArrayInputStream(jpeg(40))))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("UPLOAD_CHUNK_SIZE"));
    }

    @Test
    void completingQueuesTheFileOnlyWhenEveryByteArrived() {
        when(repository.lockUpload(uploadId, 7L)).thenReturn(Optional.of(upload("IMAGE", 100, 60)));
        assertThatThrownBy(() -> service().complete(user, uploadId))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("UPLOAD_INCOMPLETE"));

        byte[] jpeg = jpeg(12);
        when(repository.lockUpload(uploadId, 7L)).thenReturn(Optional.of(upload("IMAGE", jpeg.length, 0)));
        service().putChunk(user, uploadId, 0, jpeg.length, new ByteArrayInputStream(jpeg));
        when(repository.lockUpload(uploadId, 7L)).thenReturn(Optional.of(upload("IMAGE", jpeg.length, jpeg.length)));

        assertThat(service().complete(user, uploadId).status()).isEqualTo("QUEUED");
        verify(repository).completeUpload(1L, 2L, "image/jpeg", NOW);
        assertThat(MediaUploadService.sourceFile(new MediaSettings(root.resolve("media").toString(), "", "f", "p", false), assetId)).exists();
    }

    @Test
    void expiredUploadsCannotReceiveChunks() {
        when(repository.lockUpload(uploadId, 7L)).thenReturn(Optional.of(new UploadRow(1L, uploadId, 2L, assetId, 7L, "POST", "IMAGE",
            10, 0, MediaSettings.CHUNK_BYTES, "OPEN", NOW.minusSeconds(1))));
        assertThatThrownBy(() -> service().putChunk(user, uploadId, 0, 10, new ByteArrayInputStream(jpeg(10))))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("UPLOAD_EXPIRED"));
    }

    private UploadRow upload(String kind, long total, long received) {
        return new UploadRow(1L, uploadId, 2L, assetId, 7L, "POST", kind, total, received, MediaSettings.CHUNK_BYTES, "OPEN", NOW.plusSeconds(3600));
    }

    private static byte[] jpeg(int size) {
        byte[] bytes = new byte[size];
        bytes[0] = (byte) 0xFF; bytes[1] = (byte) 0xD8; bytes[2] = (byte) 0xFF;
        return bytes;
    }
}
