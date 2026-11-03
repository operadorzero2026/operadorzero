package br.com.operadorzero.recon;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.recon.ReconDtos.AddToDossierRequest;
import br.com.operadorzero.recon.ReconDtos.AuthorRecons;
import br.com.operadorzero.recon.ReconDtos.CreateDossierRequest;
import br.com.operadorzero.recon.ReconDtos.CreateReconRequest;
import br.com.operadorzero.recon.ReconDtos.DossierDetail;
import br.com.operadorzero.recon.ReconDtos.DossierSummary;
import br.com.operadorzero.recon.ReconDtos.ReconBar;
import br.com.operadorzero.recon.ReconDtos.ReconCreated;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReconController {
    private final ReconService service;

    public ReconController(ReconService service) {
        this.service = service;
    }

    @PostMapping("/api/recons")
    @ResponseStatus(HttpStatus.CREATED)
    ReconCreated create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody CreateReconRequest request) {
        return service.create(user, request);
    }

    @GetMapping("/api/recons/bar")
    ReconBar bar(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.bar(user);
    }

    @GetMapping("/api/recons/by/{username}")
    AuthorRecons byAuthor(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String username) {
        return service.ofAuthor(user, username);
    }

    @GetMapping("/api/recons/{id}")
    AuthorRecons one(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.one(user, id);
    }

    @PostMapping("/api/recons/{id}/view")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void view(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.markViewed(user, id);
    }

    @DeleteMapping("/api/recons/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.delete(user, id);
    }

    @PostMapping("/api/dossiers")
    @ResponseStatus(HttpStatus.CREATED)
    DossierSummary createDossier(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody CreateDossierRequest request) {
        return service.createDossier(user, request.title());
    }

    @GetMapping("/api/dossiers/by/{username}")
    List<DossierSummary> dossiers(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String username) {
        return service.dossiersOf(user, username);
    }

    @GetMapping("/api/dossiers/{id}")
    DossierDetail dossier(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.dossier(user, id);
    }

    @PostMapping("/api/dossiers/{id}/recons")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void add(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @Valid @RequestBody AddToDossierRequest request) {
        service.addToDossier(user, id, request.reconId());
    }

    @DeleteMapping("/api/dossiers/{id}/recons/{reconId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @PathVariable UUID reconId) {
        service.removeFromDossier(user, id, reconId);
    }

    @DeleteMapping("/api/dossiers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteDossier(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.deleteDossier(user, id);
    }
}
