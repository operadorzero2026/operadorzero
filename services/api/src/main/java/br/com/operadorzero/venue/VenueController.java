package br.com.operadorzero.venue;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.venue.VenueDtos.FieldListResponse;
import br.com.operadorzero.venue.VenueDtos.FieldResponse;
import br.com.operadorzero.venue.VenueDtos.MapListResponse;
import br.com.operadorzero.venue.VenueDtos.MapResponse;
import br.com.operadorzero.venue.VenueDtos.SaveFieldRequest;
import br.com.operadorzero.venue.VenueDtos.SaveMapRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated @RestController @RequestMapping("/api")
public class VenueController {
    private final VenueService service; public VenueController(VenueService service){this.service=service;}
    @GetMapping("/fields") FieldListResponse fields(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(defaultValue="")@Size(max=80)String q,@RequestParam(defaultValue="")@Size(max=80)String city,@RequestParam(defaultValue="")@Size(max=2)String stateCode,@RequestParam(defaultValue="50")@Min(1)@Max(100)int limit){return service.fields(user,q,city,stateCode,limit);}
    @GetMapping("/fields/{id}") FieldResponse field(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID id){return service.field(user,id);}
    @PostMapping("/fields") @ResponseStatus(HttpStatus.CREATED) FieldResponse createField(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody SaveFieldRequest request){return service.createField(user,request);}
    @GetMapping("/maps") MapListResponse maps(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(required=false)UUID fieldId,@RequestParam(defaultValue="")@Size(max=24)String terrain,@RequestParam(defaultValue="")@Size(max=80)String q,@RequestParam(defaultValue="50")@Min(1)@Max(100)int limit){return service.maps(user,fieldId,terrain,q,limit);}
    @PostMapping("/fields/{fieldId}/maps") @ResponseStatus(HttpStatus.CREATED) MapResponse createMap(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID fieldId,@Valid @RequestBody SaveMapRequest request){return service.createMap(user,fieldId,request);}
}
