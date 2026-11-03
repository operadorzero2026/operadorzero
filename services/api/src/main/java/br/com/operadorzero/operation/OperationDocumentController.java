package br.com.operadorzero.operation;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operation.OperationDocumentService.Item;
import br.com.operadorzero.operation.OperationDocumentService.Listing;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Leitura e gestao dos PDFs. O envio usa o prefixo /api/media/uploads/, que ja aceita corpos de ate 9 MB no proxy
 * (o resto do site fica limitado a 3 MB); o corpo e o proprio PDF (application/pdf).
 */
@RestController
public class OperationDocumentController {
    private final OperationDocumentService service;

    public OperationDocumentController(OperationDocumentService service) {
        this.service = service;
    }

    @GetMapping("/api/operations/{id}/documents")
    Listing list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.list(user, id);
    }

    /** Sem cache e sem link duradouro: cada abertura confere de novo o acesso (bloqueia no inicio do jogo). */
    @GetMapping("/api/operations/{id}/documents/{documentId}/file")
    ResponseEntity<Resource> file(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @PathVariable UUID documentId) {
        var file = service.file(user, id, documentId);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .cacheControl(CacheControl.noStore().cachePrivate())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(new FileSystemResource(file.path()));
    }

    @PostMapping(value = "/api/media/uploads/operation-documents/{id}", consumes = MediaType.APPLICATION_PDF_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    Item upload(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @RequestParam(required = false) String title,
                @RequestParam String fileName, HttpServletRequest request) throws IOException {
        return service.upload(user, id, title, fileName, request.getInputStream(), request.getContentLengthLong());
    }

    @PutMapping(value = "/api/media/uploads/operation-documents/{id}/{documentId}", consumes = MediaType.APPLICATION_PDF_VALUE)
    Item replace(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @PathVariable UUID documentId,
                 @RequestParam String fileName, HttpServletRequest request) throws IOException {
        return service.replace(user, id, documentId, fileName, request.getInputStream(), request.getContentLengthLong());
    }

    @DeleteMapping("/api/operations/{id}/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @PathVariable UUID documentId) {
        service.delete(user, id, documentId);
    }
}
