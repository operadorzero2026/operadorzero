package br.com.operadorzero.operation;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operation.OperationDtos.MessageResponse;
import br.com.operadorzero.operation.OperationDtos.OperationFilter;
import br.com.operadorzero.operation.OperationDtos.OperationListResponse;
import br.com.operadorzero.operation.OperationDtos.OperationResponse;
import br.com.operadorzero.operation.OperationDtos.SaveOperationRequest;
import br.com.operadorzero.operation.OperationDtos.UpdateStatusRequest;
import br.com.operadorzero.operation.OperationDtos.ParticipationRequest;
import br.com.operadorzero.operation.OperationDtos.OperationRosterResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/operations")
public class OperationController {
    private final OperationService service;
    public OperationController(OperationService service) { this.service = service; }

    @GetMapping
    OperationListResponse search(@AuthenticationPrincipal AuthenticatedUser user,
        @RequestParam(defaultValue="") @Size(max=80) String q,
        @RequestParam(defaultValue="") @Size(max=80) String city,
        @RequestParam(defaultValue="") @Size(max=2) String stateCode,
        @RequestParam(defaultValue="") @Size(max=40) String modality,
        @RequestParam(defaultValue="") @Size(max=32) String status,
        @RequestParam(required=false) LocalDate from, @RequestParam(required=false) LocalDate to,
        @RequestParam(defaultValue="50") @Min(1) @Max(100) int limit) {
        return service.search(user, new OperationFilter(q, city, stateCode, modality, status, from, to, limit));
    }
    @GetMapping("/{id}") OperationResponse detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) { return service.detail(user, id); }
    @GetMapping("/{id}/roster") OperationRosterResponse roster(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) { return service.roster(user, id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) OperationResponse create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody SaveOperationRequest request) { return service.create(user, request); }
    @PatchMapping("/{id}/status") OperationResponse status(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) { return service.updateStatus(user, id, request); }
    @PostMapping("/{id}/publish") OperationResponse publish(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) { return service.publish(user, id); }
    @PostMapping("/{id}/participation") @ResponseStatus(HttpStatus.CREATED) MessageResponse participate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @Valid @RequestBody ParticipationRequest request) { return service.requestParticipation(user, id, request); }
    @PostMapping("/{id}/participation/cancel") MessageResponse cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) { return service.cancelParticipation(user, id); }
    @PostMapping(value = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    OperationResponse cover(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                            @RequestParam("file") MultipartFile file) { return service.saveCover(user, id, file); }
    @GetMapping("/{id}/cover") ResponseEntity<byte[]> cover(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        var cover = service.cover(user, id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(cover.contentType()))
            .cacheControl(CacheControl.noStore()).body(cover.data());
    }
    @DeleteMapping("/{id}/cover") MessageResponse removeCover(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.removeCover(user, id);
    }
}
