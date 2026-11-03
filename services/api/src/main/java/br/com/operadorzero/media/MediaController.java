package br.com.operadorzero.media;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.media.MediaDtos.CompleteUploadRequest;
import br.com.operadorzero.media.MediaDtos.MediaStatusResponse;
import br.com.operadorzero.media.MediaDtos.StartUploadRequest;
import br.com.operadorzero.media.MediaDtos.UploadResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/media")
public class MediaController {
    private final MediaUploadService uploads;

    public MediaController(MediaUploadService uploads) {
        this.uploads = uploads;
    }

    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    UploadResponse start(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody StartUploadRequest request) {
        return uploads.start(user, request);
    }

    @GetMapping("/uploads/{uploadId}")
    UploadResponse upload(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID uploadId) {
        return uploads.status(user, uploadId);
    }

    /** Corpo cru (application/octet-stream), um pedaco por requisicao; o limite do proxy acompanha o tamanho do pedaco. */
    @PutMapping(value = "/uploads/{uploadId}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    UploadResponse chunk(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID uploadId,
                         @RequestParam long offset, HttpServletRequest request) throws IOException {
        return uploads.putChunk(user, uploadId, offset, request.getContentLengthLong(), request.getInputStream());
    }

    @PostMapping("/uploads/{uploadId}/complete")
    MediaStatusResponse complete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID uploadId,
                                 @Valid @RequestBody(required = false) CompleteUploadRequest options) {
        return uploads.complete(user, uploadId, options);
    }

    @GetMapping("/{mediaId}")
    MediaStatusResponse media(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID mediaId) {
        return uploads.mediaStatus(user, mediaId);
    }
}
