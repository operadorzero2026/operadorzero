package br.com.operadorzero.performance;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.performance.PerformanceDtos.ContestRequest;
import br.com.operadorzero.performance.PerformanceDtos.MessageResponse;
import br.com.operadorzero.performance.PerformanceDtos.PerformanceListResponse;
import br.com.operadorzero.performance.PerformanceDtos.PerformanceResponse;
import br.com.operadorzero.performance.PerformanceDtos.ReviewPerformanceRequest;
import br.com.operadorzero.performance.PerformanceDtos.SavePerformanceRequest;
import br.com.operadorzero.performance.PerformanceRepository.OperationAccess;
import br.com.operadorzero.performance.PerformanceRepository.RecordRef;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.web.BusinessException;
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
public class PerformanceService {
    private static final Set<String> RESULTS=Set.of("WIN","LOSS","DRAW","NONE");
    private static final Set<String> SCOPES=Set.of("FULL","PARTIAL");
    private static final Set<String> ACTIONS=Set.of("CONFIRM_ORGANIZER","CONFIRM_TEAM","CORRECT","REJECT");
    private final PerformanceRepository repository;private final AuditEventRepository audit;private final Clock clock;
    @Autowired
    public PerformanceService(PerformanceRepository repository,AuditEventRepository audit){this(repository,audit,Clock.systemUTC());}
    PerformanceService(PerformanceRepository repository,AuditEventRepository audit,Clock clock){this.repository=repository;this.audit=audit;this.clock=clock;}
    public PerformanceListResponse mine(AuthenticatedUser user){return new PerformanceListResponse(repository.mine(user.internalId()));}
    public PerformanceListResponse reviewable(AuthenticatedUser user){return new PerformanceListResponse(repository.reviewable(user.internalId()));}

    @Transactional public PerformanceResponse save(AuthenticatedUser user,UUID operationId,SavePerformanceRequest request){
        OperationAccess access=repository.operationAccess(operationId,user.internalId()).orElseThrow(()->BusinessException.notFound("Operação não encontrada."));
        if(!"FINISHED".equals(access.status()))throw BusinessException.conflict("OPERATION_NOT_FINISHED","O desempenho só pode ser registrado após a operação ser finalizada.");
        if(!Set.of("CONFIRMED","CHECKED_IN").contains(access.participantStatus()))throw BusinessException.forbidden("Somente participantes com presença confirmada podem registrar desempenho.");
        String result=allowed(request.result(),RESULTS,"INVALID_RESULT","Selecione um resultado válido.");String scope=allowed(request.participationScope(),SCOPES,"INVALID_SCOPE","Selecione participação completa ou parcial.");
        Long teamId=null;if(request.representedTeamId()!=null)teamId=repository.teamInternalId(request.representedTeamId(),user.internalId()).orElseThrow(()->BusinessException.badRequest("INVALID_REPRESENTED_TEAM","A equipe informada não é sua equipe atual."));
        Instant now=clock.instant();UUID id;
        try{id=repository.save(access.id(),user.internalId(),teamId,request,result,scope,now);}catch(EmptyResultDataAccessException e){throw BusinessException.conflict("PERFORMANCE_CHANGED","O registro foi confirmado ou alterado em outro acesso. Recarregue a página.");}
        audit.record(user.internalId(),"PERFORMANCE_SAVED","PERFORMANCE_RECORD",id,null,now);return repository.find(id,user.internalId()).orElseThrow(()->BusinessException.notFound("Registro não encontrado."));
    }

    @Transactional public PerformanceResponse review(AuthenticatedUser user,UUID id,ReviewPerformanceRequest request){
        PerformanceResponse current=repository.find(id,user.internalId()).orElseThrow(()->BusinessException.notFound("Registro não encontrado."));
        String action=allowed(request.action(),ACTIONS,"INVALID_REVIEW_ACTION","Selecione uma ação de revisão válida.");
        boolean organizer="CONFIRM_ORGANIZER".equals(action);boolean team="CONFIRM_TEAM".equals(action);
        if("CONTESTED".equals(current.status())&&(organizer||team))throw BusinessException.conflict("CONTEST_REVIEW_REQUIRED","Um registro contestado deve ser corrigido ou rejeitado pelo organizador antes de voltar ao ranking.");
        if(organizer&&!current.canReviewAsOrganizer())throw BusinessException.forbidden("Somente o organizador desta operação pode confirmar o registro.");
        if(team&&!current.canReviewAsTeam())throw BusinessException.forbidden("Somente capitão ou gestor da equipe representada pode confirmar o registro.");
        if(Set.of("CORRECT","REJECT").contains(action)&&!current.canReviewAsOrganizer())throw BusinessException.forbidden("Somente o organizador pode corrigir ou rejeitar o registro.");
        validateCorrections(request,action);Instant now=clock.instant();
        if(repository.review(id,request.version(),request,action,user.internalId(),organizer,team,now)!=1)throw BusinessException.conflict("PERFORMANCE_CHANGED","O registro foi atualizado em outro acesso. Recarregue e tente novamente.");
        audit.record(user.internalId(),"PERFORMANCE_"+action,"PERFORMANCE_RECORD",id,nullable(request.notes()),now);return repository.find(id,user.internalId()).orElseThrow(()->BusinessException.notFound("Registro não encontrado."));
    }

    @Transactional public MessageResponse contest(AuthenticatedUser user,UUID id,ContestRequest request){
        RecordRef record=repository.recordRef(id).orElseThrow(()->BusinessException.notFound("Registro não encontrado."));
        if(record.userId()==user.internalId())throw BusinessException.badRequest("SELF_CONTEST","Você não pode contestar o próprio registro.");
        if(!repository.participated(record.operationId(),user.internalId()))throw BusinessException.forbidden("Somente outro participante confirmado da operação pode contestar este registro.");
        Instant now=clock.instant();try{UUID contestId=repository.contest(record.id(),user.internalId(),request.reason().trim(),now);repository.markContested(record.id(),now);audit.record(user.internalId(),"PERFORMANCE_CONTESTED","PERFORMANCE_CONTEST",contestId,null,now);return new MessageResponse("Contestação enviada para análise.");}catch(DataIntegrityViolationException e){throw BusinessException.conflict("CONTEST_ALREADY_OPEN","Você já possui uma contestação aberta para este registro.");}
    }
    private void validateCorrections(ReviewPerformanceRequest r,String action){if(!"CORRECT".equals(action))return;if(r.eliminations()==null||r.deaths()==null||r.objectivesCompleted()==null||r.roundWins()==null||r.result()==null||r.penaltyPoints()==null)throw BusinessException.badRequest("CORRECTION_FIELDS_REQUIRED","Informe todos os números e o resultado ao corrigir.");if(r.eliminations()<0||r.eliminations()>10000||r.deaths()<0||r.deaths()>10000||r.objectivesCompleted()<0||r.objectivesCompleted()>1000||r.roundWins()<0||r.roundWins()>1000||r.penaltyPoints().signum()<0)throw BusinessException.badRequest("INVALID_CORRECTION","Os valores da correção são inválidos.");allowed(r.result(),RESULTS,"INVALID_RESULT","Selecione um resultado válido.");}
    private String allowed(String v,Set<String>s,String code,String message){String n=v==null?"":v.trim().toUpperCase(Locale.ROOT);if(!s.contains(n))throw BusinessException.badRequest(code,message);return n;}private String nullable(String v){if(v==null)return null;String n=v.trim();return n.isEmpty()?null:n;}
}
