package br.com.operadorzero.performance;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.performance.PerformanceDtos.ContestRequest;
import br.com.operadorzero.performance.PerformanceDtos.MessageResponse;
import br.com.operadorzero.performance.PerformanceDtos.PerformanceListResponse;
import br.com.operadorzero.performance.PerformanceDtos.PerformanceResponse;
import br.com.operadorzero.performance.PerformanceDtos.ReviewPerformanceRequest;
import br.com.operadorzero.performance.PerformanceDtos.SavePerformanceRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/performance")
public class PerformanceController {
    private final PerformanceService service;public PerformanceController(PerformanceService service){this.service=service;}
    @GetMapping("/me") PerformanceListResponse mine(@AuthenticationPrincipal AuthenticatedUser user){return service.mine(user);}
    @GetMapping("/reviewable") PerformanceListResponse reviewable(@AuthenticationPrincipal AuthenticatedUser user){return service.reviewable(user);}
    @PostMapping("/operations/{operationId}") PerformanceResponse save(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID operationId,@Valid @RequestBody SavePerformanceRequest request){return service.save(user,operationId,request);}
    @PatchMapping("/{id}/review") PerformanceResponse review(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID id,@Valid @RequestBody ReviewPerformanceRequest request){return service.review(user,id,request);}
    @PostMapping("/{id}/contests") MessageResponse contest(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID id,@Valid @RequestBody ContestRequest request){return service.contest(user,id,request);}
}
