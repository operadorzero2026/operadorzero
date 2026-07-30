package br.com.operadorzero.operator;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operator.OperatorSocialDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/operators/social")
public class OperatorSocialController {
    private final OperatorSocialService service;
    public OperatorSocialController(OperatorSocialService service) { this.service=service; }
    @GetMapping("/connections") ConnectionListResponse connections(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(defaultValue="ACCEPTED") @Pattern(regexp="ACCEPTED|PENDING") String status) { return service.connections(user,status); }
    @GetMapping("/blocks") OperatorDtos.SearchResponse blocked(@AuthenticationPrincipal AuthenticatedUser user) { return service.blocked(user); }
    @GetMapping("/{username}/connection") ConnectionStatusResponse status(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String username) { return service.status(user,username); }
    @GetMapping("/{username}/profile") ProfileOverviewResponse profile(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String username) { return service.profile(user,username); }
    @PostMapping("/{username}/requests") @ResponseStatus(HttpStatus.CREATED) ConnectionStatusResponse request(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String username) { return service.request(user,username); }
    @PostMapping("/requests/{id}/accept") MessageResponse accept(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID id) { return service.accept(user,id); }
    @PostMapping("/requests/{id}/decline") MessageResponse decline(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID id) { return service.decline(user,id); }
    @DeleteMapping("/requests/{id}") MessageResponse cancel(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID id) { return service.cancel(user,id); }
    @DeleteMapping("/connections/{id}") MessageResponse remove(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID id) { return service.remove(user,id); }
    @PostMapping("/{username}/block") MessageResponse block(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String username) { return service.block(user,username); }
    @DeleteMapping("/{username}/block") MessageResponse unblock(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String username) { return service.unblock(user,username); }
    @PostMapping("/{username}/reports") @ResponseStatus(HttpStatus.CREATED) MessageResponse report(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String username,@Valid @RequestBody ReportRequest request) { return service.report(user,username,request); }
}
