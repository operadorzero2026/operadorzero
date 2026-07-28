package br.com.operadorzero.operator;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operator.OperatorDtos.CreateEquipmentRequest;
import br.com.operadorzero.operator.OperatorDtos.OperatorSummary;
import br.com.operadorzero.operator.OperatorDtos.PrivacyRequest;
import br.com.operadorzero.operator.OperatorDtos.ProfileResponse;
import br.com.operadorzero.operator.OperatorDtos.SearchResponse;
import br.com.operadorzero.operator.OperatorDtos.UpdateProfileRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/operators")
public class OperatorController {
    private final OperatorService service;

    public OperatorController(OperatorService service) {
        this.service = service;
    }

    @GetMapping("/me")
    ProfileResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.me(user);
    }

    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ProfileResponse savePhoto(@AuthenticationPrincipal AuthenticatedUser user,
                              @RequestParam("file") MultipartFile file) {
        return service.savePhoto(user, file);
    }

    @GetMapping("/me/photo")
    ResponseEntity<byte[]> photo(@AuthenticationPrincipal AuthenticatedUser user) {
        var photo = service.photo(user);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType(photo.contentType()))
            .body(photo.data());
    }

    @PatchMapping("/me")
    ProfileResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                           @Valid @RequestBody UpdateProfileRequest request) {
        return service.update(user, request);
    }

    @PutMapping("/me/privacy")
    ProfileResponse privacy(@AuthenticationPrincipal AuthenticatedUser user,
                            @Valid @RequestBody PrivacyRequest request) {
        return service.updatePrivacy(user, request);
    }

    @PostMapping("/me/equipment")
    @ResponseStatus(HttpStatus.CREATED)
    ProfileResponse addEquipment(@AuthenticationPrincipal AuthenticatedUser user,
                                 @Valid @RequestBody CreateEquipmentRequest request) {
        return service.addEquipment(user, request);
    }

    @DeleteMapping("/me/equipment/{equipmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeEquipment(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID equipmentId) {
        service.removeEquipment(user, equipmentId);
    }

    @GetMapping("/search")
    SearchResponse search(@AuthenticationPrincipal AuthenticatedUser user,
                          @RequestParam(defaultValue = "") @Size(max = 80) String q,
                          @RequestParam(defaultValue = "10") @Min(1) @Max(20) int limit,
                          HttpServletRequest request) {
        return service.search(user, q, limit, request);
    }

    @GetMapping("/{username}")
    OperatorSummary profile(@AuthenticationPrincipal AuthenticatedUser user,
                            @PathVariable @Pattern(regexp = "[A-Za-z0-9._-]{3,30}") String username) {
        return service.publicProfile(user, username);
    }
}

