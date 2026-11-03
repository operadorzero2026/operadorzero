package br.com.operadorzero.operation;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operation.OperationChatService.ChatMessage;
import br.com.operadorzero.operation.OperationChatService.ChatPage;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/operations/{id}/chat")
public class OperationChatController {
    public record SendRequest(@NotBlank @Size(max = 1000) String body, UUID idempotencyKey) {}
    public record MuteRequest(@NotBlank @Size(max = 12) String duration) {}

    private final OperationChatService service;

    public OperationChatController(OperationChatService service) {
        this.service = service;
    }

    @GetMapping
    ChatPage page(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @RequestParam(required = false) Instant before,
                  @RequestParam(required = false) Instant after, @RequestParam(required = false) Integer limit) {
        return service.page(user, id, before, after, limit);
    }

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    ChatMessage send(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @Valid @RequestBody SendRequest request) {
        return service.send(user, id, request.body(), request.idempotencyKey());
    }

    @DeleteMapping("/messages/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @PathVariable UUID messageId) {
        service.delete(user, id, messageId);
    }

    @PostMapping("/messages/{messageId}/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void pin(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @PathVariable UUID messageId) {
        service.pin(user, id, messageId, true);
    }

    @DeleteMapping("/messages/{messageId}/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unpin(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @PathVariable UUID messageId) {
        service.pin(user, id, messageId, false);
    }

    @PostMapping("/mutes/{operatorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void mute(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @PathVariable UUID operatorId, @Valid @RequestBody MuteRequest request) {
        service.mute(user, id, operatorId, request.duration());
    }

    @DeleteMapping("/mutes/{operatorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unmute(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @PathVariable UUID operatorId) {
        service.unmute(user, id, operatorId);
    }

    /** Avisos em tempo real (Server-Sent Events). X-Accel-Buffering: o Nginx repassa cada aviso na hora. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-store");
        return service.stream(user, id);
    }

    @GetMapping("/photos/{operatorId}")
    ResponseEntity<byte[]> photo(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @PathVariable UUID operatorId) {
        var photo = service.photo(user, id, operatorId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
            .cacheControl(CacheControl.maxAge(Duration.ofHours(12)).cachePrivate()).header("X-Content-Type-Options", "nosniff").body(photo.data());
    }
}
