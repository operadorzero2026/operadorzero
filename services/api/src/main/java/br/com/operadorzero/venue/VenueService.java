package br.com.operadorzero.venue;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operator.BrazilLocationValidator;
import br.com.operadorzero.operator.BrazilLocationValidator.Location;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.web.BusinessException;
import br.com.operadorzero.venue.VenueDtos.FieldListResponse;
import br.com.operadorzero.venue.VenueDtos.FieldResponse;
import br.com.operadorzero.venue.VenueDtos.MapListResponse;
import br.com.operadorzero.venue.VenueDtos.SaveFieldRequest;
import br.com.operadorzero.venue.VenueDtos.SaveMapRequest;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VenueService {
    private static final Set<String> TERRAINS=Set.of("CQB","URBAN","FOREST","MIXED","INDUSTRIAL","OPEN","NIGHT","INDOOR","OUTDOOR");
    private final VenueRepository repository; private final BrazilLocationValidator locations; private final AuditEventRepository audit; private final Clock clock;
    @Autowired
    public VenueService(VenueRepository repository,BrazilLocationValidator locations,AuditEventRepository audit){this(repository,locations,audit,Clock.systemUTC());}
    VenueService(VenueRepository repository,BrazilLocationValidator locations,AuditEventRepository audit,Clock clock){this.repository=repository;this.locations=locations;this.audit=audit;this.clock=clock;}
    public FieldListResponse fields(AuthenticatedUser user,String q,String city,String state,int limit){return new FieldListResponse(repository.fields(user.internalId(),norm(q),norm(city),norm(state),limit));}
    public FieldResponse field(AuthenticatedUser user,UUID id){return repository.field(id,user.internalId()).orElseThrow(()->BusinessException.notFound("Campo não encontrado."));}
    @Transactional public FieldResponse createField(AuthenticatedUser user,SaveFieldRequest request){Location location=locations.validate(request.stateCode(),request.city());Instant now=clock.instant();UUID id=repository.createField(user.internalId(),request,location.city(),location.stateCode(),now);audit.record(user.internalId(),"FIELD_CREATED","FIELD",id,null,now);return field(user,id);}
    public MapListResponse maps(AuthenticatedUser user,UUID fieldId,String terrain,String q,int limit){String normalized=norm(terrain).toUpperCase(Locale.ROOT);if(!normalized.isEmpty()&&!TERRAINS.contains(normalized))throw BusinessException.badRequest("INVALID_TERRAIN","Selecione um terreno válido.");return new MapListResponse(repository.maps(user.internalId(),fieldId,normalized,norm(q),limit));}
    @Transactional public VenueDtos.MapResponse createMap(AuthenticatedUser user,UUID fieldId,SaveMapRequest request){String terrain=norm(request.terrainType()).toUpperCase(Locale.ROOT);if(!TERRAINS.contains(terrain))throw BusinessException.badRequest("INVALID_TERRAIN","Selecione um terreno válido.");Instant now=clock.instant();try{UUID id=repository.createMap(fieldId,user.internalId(),request,terrain,now);audit.record(user.internalId(),"FIELD_MAP_CREATED","FIELD_MAP",id,null,now);return repository.maps(user.internalId(),fieldId,"",norm(request.name()),10).stream().filter(m->m.id().equals(id)).findFirst().orElseThrow(()->BusinessException.notFound("Mapa não encontrado."));}catch(DataIntegrityViolationException exception){throw BusinessException.conflict("MAP_CONFLICT","Já existe um mapa ativo com este nome no campo.");}catch(EmptyResultDataAccessException exception){throw BusinessException.notFound("Campo não encontrado ou não administrado por você.");}}
    private String norm(String value){return value==null?"":value.trim().replaceAll("\\s+"," ");}
}
