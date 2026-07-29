package br.com.operadorzero.operation;

import static br.com.operadorzero.operation.OperationStructureDtos.*;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operation.OperationDtos.MessageResponse;
import br.com.operadorzero.operation.OperationStructureRepository.ChatAccess;
import br.com.operadorzero.operation.OperationStructureRepository.OperationAccess;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.web.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationStructureService {
    private static final Set<String> ROLES=Set.of("OPERATION_ADMIN","TEAM_COMMANDER","TEAM_RADIO","SQUAD_COMMANDER","SQUAD_RADIO");
    private final OperationStructureRepository repository;
    private final OperationChatRateLimiter rateLimiter;
    private final AuditEventRepository audit;
    private final Clock clock;
    @Autowired public OperationStructureService(OperationStructureRepository repository,OperationChatRateLimiter rateLimiter,AuditEventRepository audit){this(repository,rateLimiter,audit,Clock.systemUTC());}
    OperationStructureService(OperationStructureRepository repository,OperationChatRateLimiter rateLimiter,AuditEventRepository audit,Clock clock){this.repository=repository;this.rateLimiter=rateLimiter;this.audit=audit;this.clock=clock;}

    @Transactional(readOnly=true) public StructureResponse structure(AuthenticatedUser user,UUID id){return repository.structure(access(id,user,false),user.internalId());}
    @Transactional public StructureResponse updateSettings(AuthenticatedUser user,UUID id,UpdateStructureSettingsRequest request){OperationAccess a=managed(id,user,true);StructureResponse s=repository.structure(a,user.internalId());String size=request.gameSize().trim().toUpperCase(Locale.ROOT);
        if(!Set.of("SMALL","MEDIUM","LARGE").contains(size))throw BusinessException.badRequest("INVALID_GAME_SIZE","Tamanho do jogo inválido.");
        if(size.equals("SMALL")&&(request.participantLimit()==null||request.participantLimit()>50||s.participantCount()>50||s.teams().size()!=2||s.teams().stream().anyMatch(t->!t.squads().isEmpty())))throw BusinessException.conflict("INVALID_SIZE_REDUCTION","Para reduzir para pequeno, ajuste para até 50 participantes, exatamente 2 times e remova todos os esquadrões vazios.");
        if(size.equals("MEDIUM")&&(request.participantLimit()==null||request.participantLimit()>100||s.participantCount()>100||s.teams().size()<2||s.teams().size()>4))throw BusinessException.conflict("INVALID_SIZE_REDUCTION","Para usar tamanho médio, mantenha até 100 participantes e entre 2 e 4 times.");
        if(request.participantLimit()!=null&&request.participantLimit()<s.participantCount())throw BusinessException.conflict("LIMIT_BELOW_OCCUPANCY","O limite não pode ser menor que a ocupação atual.");
        repository.updateSettings(a,request,size,clock.instant());audit.record(user.internalId(),"OPERATION_STRUCTURE_SETTINGS_UPDATED","OPERATION",id,size,clock.instant());return repository.structure(repository.access(id,user.internalId(),false).orElseThrow(),user.internalId());}
    @Transactional public StructureResponse createTeam(AuthenticatedUser user,UUID id,SaveTeamRequest request){
        OperationAccess a=managed(id,user,true); StructureResponse current=repository.structure(a,user.internalId());
        if("SMALL".equals(a.gameSize())&&current.teams().size()>=2) throw BusinessException.conflict("SMALL_TEAM_LIMIT","Jogo pequeno permite somente dois times.");
        if("MEDIUM".equals(a.gameSize())&&current.teams().size()>=4) throw BusinessException.conflict("MEDIUM_TEAM_LIMIT","Jogo médio permite no máximo quatro times.");
        validateTeamCapacity(a,current,request.capacity(),null); UUID created=repository.createTeam(a,request,clock.instant());
        audit.record(user.internalId(),"OPERATION_TEAM_CREATED","OPERATION_TEAM",created,null,clock.instant()); return repository.structure(a,user.internalId());
    }
    @Transactional public StructureResponse updateTeam(AuthenticatedUser user,UUID id,UUID teamId,SaveTeamRequest request){
        OperationAccess a=managed(id,user,true); StructureResponse current=repository.structure(a,user.internalId());
        validateTeamCapacity(a,current,request.capacity(),teamId);
        if(repository.updateTeam(a,teamId,request,clock.instant())!=1) throw BusinessException.notFound("Time não encontrado.");
        audit.record(user.internalId(),"OPERATION_TEAM_UPDATED","OPERATION_TEAM",teamId,null,clock.instant()); return repository.structure(a,user.internalId());
    }
    @Transactional public MessageResponse deleteTeam(AuthenticatedUser user,UUID id,UUID teamId){OperationAccess a=managed(id,user,true);
        if(repository.deleteEmptyTeam(a,teamId)!=1) throw BusinessException.conflict("TEAM_NOT_EMPTY","Transfira ou remova todos os participantes antes de excluir o time.");
        audit.record(user.internalId(),"OPERATION_TEAM_DELETED","OPERATION_TEAM",teamId,null,clock.instant());return new MessageResponse("Time excluído.");}
    @Transactional public StructureResponse createSquad(AuthenticatedUser user,UUID id,UUID teamId,SaveSquadRequest request){OperationAccess a=managed(id,user,true);
        if("SMALL".equals(a.gameSize())) throw BusinessException.badRequest("SQUADS_NOT_ALLOWED","Jogos pequenos não possuem esquadrões.");
        UUID created=repository.createSquad(a,teamId,request,clock.instant());audit.record(user.internalId(),"OPERATION_SQUAD_CREATED","OPERATION_SQUAD",created,null,clock.instant());return repository.structure(a,user.internalId());}
    @Transactional public StructureResponse updateSquad(AuthenticatedUser user,UUID id,UUID squadId,SaveSquadRequest request){OperationAccess a=managed(id,user,true);
        if(repository.updateSquad(a,squadId,request,clock.instant())!=1)throw BusinessException.notFound("Esquadrão não encontrado.");
        audit.record(user.internalId(),"OPERATION_SQUAD_UPDATED","OPERATION_SQUAD",squadId,null,clock.instant());return repository.structure(a,user.internalId());}
    @Transactional public MessageResponse deleteSquad(AuthenticatedUser user,UUID id,UUID squadId){OperationAccess a=managed(id,user,true);
        if(repository.deleteEmptySquad(a,squadId)!=1)throw BusinessException.conflict("SQUAD_NOT_EMPTY","Transfira ou remova todos os integrantes antes de excluir o esquadrão.");
        audit.record(user.internalId(),"OPERATION_SQUAD_DELETED","OPERATION_SQUAD",squadId,null,clock.instant());return new MessageResponse("Esquadrão excluído.");}
    @Transactional public StructureResponse move(AuthenticatedUser user,UUID id,MoveParticipantRequest request){OperationAccess a=managed(id,user,true);
        if("SMALL".equals(a.gameSize())&&request.squadId()!=null)throw BusinessException.badRequest("SQUADS_NOT_ALLOWED","Jogos pequenos não possuem esquadrões.");
        repository.lockTeamAndSquad(a,request.teamId(),request.squadId());
        if(repository.moveParticipant(a,request,clock.instant())!=1)throw BusinessException.conflict("DESTINATION_FULL","O destino não existe ou a última vaga já foi ocupada.");
        audit.record(user.internalId(),"OPERATION_PARTICIPANT_MOVED","OPERATOR",request.operatorId(),null,clock.instant());return repository.structure(a,user.internalId());}
    @Transactional public StructureResponse assignRole(AuthenticatedUser user,UUID id,AssignRoleRequest raw){OperationAccess a=managed(id,user,true);String role=raw.role().trim().toUpperCase(Locale.ROOT);
        if(!ROLES.contains(role))throw BusinessException.badRequest("INVALID_OPERATION_ROLE","Função da operação inválida.");
        if("SMALL".equals(a.gameSize())&&!"OPERATION_ADMIN".equals(role))throw BusinessException.badRequest("COMMAND_ROLES_NOT_ALLOWED","Jogos pequenos não possuem funções formais de comando.");
        if(role.startsWith("TEAM_")&&raw.teamId()==null||role.startsWith("SQUAD_")&&(raw.teamId()==null||raw.squadId()==null))throw BusinessException.badRequest("ROLE_SCOPE_REQUIRED","Informe o time e o esquadrão exigidos pela função.");
        AssignRoleRequest request=new AssignRoleRequest(raw.operatorId(),role,raw.teamId(),raw.squadId());
        if(!a.allowRoleAccumulation()&&repository.roleCount(a,request.operatorId())>0)throw BusinessException.conflict("ROLE_ACCUMULATION_NOT_ALLOWED","Este participante já possui uma função de comando nesta operação.");
        if(repository.assignRole(a,request,user.internalId(),clock.instant())!=1)throw BusinessException.conflict("ROLE_ASSIGNMENT_INVALID","O participante ou o escopo da função é inválido.");
        audit.record(user.internalId(),"OPERATION_ROLE_ASSIGNED","OPERATOR",request.operatorId(),role,clock.instant());return repository.structure(a,user.internalId());}
    @Transactional public MessageResponse removeRole(AuthenticatedUser user,UUID id,UUID roleId){OperationAccess a=managed(id,user,true);if(repository.removeRole(a,roleId)!=1)throw BusinessException.notFound("Função não encontrada.");return new MessageResponse("Função removida.");}

    @Transactional(readOnly=true) public ChatPageResponse chat(AuthenticatedUser user,UUID id,UUID teamId,int limit,Instant before){OperationAccess a=access(id,user,false);ChatAccess c=chatAccess(a,user,teamId,false);return repository.messages(c,user.internalId(),limit,before);}
    @Transactional public MessageResponse send(AuthenticatedUser user,UUID id,UUID teamId,SendMessageRequest request){OperationAccess a=access(id,user,false);ChatAccess c=chatAccess(a,user,teamId,true);
        if("LOCKED".equals(c.status())&&!a.owner()&&!a.operationAdmin())throw BusinessException.forbidden("Este chat está bloqueado.");
        if(!rateLimiter.allow(user.internalId(),c.publicId()))throw new OperationRateLimitException();
        String body=sanitize(request.body());UUID message=repository.send(c,user.internalId(),request,body,clock.instant());return new MessageResponse("Mensagem enviada: "+message);}
    @Transactional public MessageResponse report(AuthenticatedUser user,UUID id,UUID teamId,UUID messageId,ReportMessageRequest request){OperationAccess a=access(id,user,false);ChatAccess c=chatAccess(a,user,teamId,false);repository.report(c,user.internalId(),messageId,request.reason().trim(),clock.instant());return new MessageResponse("Denúncia recebida.");}
    @Transactional public MessageResponse moderate(AuthenticatedUser user,UUID id,UUID teamId,UUID messageId,ModerateMessageRequest request){OperationAccess a=managed(id,user,false);ChatAccess c=repository.chatAccess(a,user.internalId(),teamId);String action=request.action().toUpperCase(Locale.ROOT);
        String status=switch(action){case "HIDE"->"HIDDEN";case "DELETE"->"DELETED";case "RESTORE"->"VISIBLE";default->throw BusinessException.badRequest("INVALID_MODERATION_ACTION","Ação de moderação inválida.");};
        if(repository.moderate(c,user.internalId(),messageId,status,request.reason(),clock.instant())!=1)throw BusinessException.notFound("Mensagem não encontrada.");return new MessageResponse("Moderação registrada.");}
    @Transactional public MessageResponse channelState(AuthenticatedUser user,UUID id,UUID teamId,ChannelStateRequest request){OperationAccess a=managed(id,user,false);ChatAccess c=repository.chatAccess(a,user.internalId(),teamId);repository.setChannelState(c,request.locked(),clock.instant());return new MessageResponse(request.locked()?"Chat bloqueado.":"Chat reaberto.");}

    private OperationAccess access(UUID id,AuthenticatedUser user,boolean lock){return repository.access(id,user.internalId(),lock).orElseThrow(()->BusinessException.notFound("Operação não encontrada."));}
    private OperationAccess managed(UUID id,AuthenticatedUser user,boolean lock){OperationAccess a=access(id,user,lock);if(!a.owner()&&!a.operationAdmin())throw BusinessException.forbidden("Você não pode administrar esta operação.");return a;}
    private ChatAccess chatAccess(OperationAccess a,AuthenticatedUser user,UUID teamId,boolean send){ChatAccess c;try{c=repository.chatAccess(a,user.internalId(),teamId);}catch(EmptyResultDataAccessException ex){throw BusinessException.notFound("Canal não encontrado.");}
        boolean manager=a.owner()||a.operationAdmin();if(teamId==null){if(send&&!c.participant()&&!manager)throw BusinessException.forbidden("Somente inscritos podem enviar mensagens.");if(!send&&!c.participant()&&!c.publicRead()&&!manager)throw BusinessException.forbidden("Chat restrito aos inscritos.");}
        else if(!c.teamMember()&&!manager)throw BusinessException.forbidden("Este chat pertence a outro time.");return c;}
    private void validateTeamCapacity(OperationAccess a,StructureResponse s,int newCapacity,UUID replacing){int total=s.teams().stream().filter(t->!t.id().equals(replacing)).mapToInt(TeamStructureResponse::capacity).sum()+newCapacity;if(a.participantLimit()!=null&&total>a.participantLimit())throw BusinessException.conflict("TEAM_CAPACITY_EXCEEDS_OPERATION","A soma das capacidades dos times ultrapassa o limite global.");}
    private String sanitize(String value){String text=value.trim().replaceAll("[\\p{Cntrl}&&[^\\n\\t]]","");if(text.isBlank())throw BusinessException.badRequest("EMPTY_MESSAGE","A mensagem não pode estar vazia.");return text;}
}
