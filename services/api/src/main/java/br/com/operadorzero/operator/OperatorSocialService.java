package br.com.operadorzero.operator;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operator.OperatorSocialDtos.ConnectionListResponse;
import br.com.operadorzero.operator.OperatorSocialDtos.ConnectionStatusResponse;
import br.com.operadorzero.operator.OperatorSocialDtos.MessageResponse;
import br.com.operadorzero.operator.OperatorSocialDtos.ProfileOverviewResponse;
import br.com.operadorzero.operator.OperatorSocialDtos.ReportRequest;
import br.com.operadorzero.shared.web.BusinessException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperatorSocialService {
    private final OperatorSocialRepository repository;
    private final Clock clock;
    @Autowired
    public OperatorSocialService(OperatorSocialRepository repository) { this(repository, Clock.systemUTC()); }
    OperatorSocialService(OperatorSocialRepository repository, Clock clock) { this.repository=repository; this.clock=clock; }

    @Transactional(readOnly=true)
    public ConnectionListResponse connections(AuthenticatedUser user,String status) {
        String normalized = "PENDING".equals(status) ? "PENDING" : "ACCEPTED";
        return new ConnectionListResponse(repository.connections(user.internalId(),normalized));
    }
    @Transactional(readOnly=true) public OperatorDtos.SearchResponse blocked(AuthenticatedUser user) { return new OperatorDtos.SearchResponse(repository.blockedOperators(user.internalId())); }
    @Transactional(readOnly=true)
    public ProfileOverviewResponse profile(AuthenticatedUser user,String username) {
        return repository.profile(user.internalId(), username)
            .orElseThrow(() -> BusinessException.notFound("Operador não encontrado."));
    }
    @Transactional(readOnly=true)
    public ConnectionStatusResponse status(AuthenticatedUser user,String username) {
        long other=target(user,username);
        if (repository.blocked(user.internalId(),other)) return new ConnectionStatusResponse("BLOCKED",null);
        return repository.activeConnection(user.internalId(),other)
            .map(row -> new ConnectionStatusResponse(row.status().equals("PENDING") ? (row.requester()==user.internalId()?"PENDING_SENT":"PENDING_RECEIVED") : row.status(),row.publicId()))
            .orElse(new ConnectionStatusResponse("NONE",null));
    }
    @Transactional
    public ConnectionStatusResponse request(AuthenticatedUser user,String username) {
        long other=target(user,username);
        if(repository.blocked(user.internalId(),other)) throw BusinessException.forbidden("Não é possível enviar esta solicitação.");
        var current=repository.activeConnection(user.internalId(),other);
        if(current.isPresent()) return status(user,username);
        try { return new ConnectionStatusResponse("PENDING_SENT",repository.create(user.internalId(),other,clock.instant())); }
        catch(DataIntegrityViolationException exception) { return status(user,username); }
    }
    @Transactional public MessageResponse accept(AuthenticatedUser user,UUID id) { transition(user,id,"PENDING","ACCEPTED",true); return new MessageResponse("Solicitação aceita."); }
    @Transactional public MessageResponse decline(AuthenticatedUser user,UUID id) { transition(user,id,"PENDING","DECLINED",true); return new MessageResponse("Solicitação recusada."); }
    @Transactional public MessageResponse cancel(AuthenticatedUser user,UUID id) { if(repository.cancel(id,user.internalId(),clock.instant())!=1) throw BusinessException.conflict("INVALID_CONNECTION_STATE","A solicitação já foi alterada ou não pertence a você."); return new MessageResponse("Solicitação cancelada."); }
    @Transactional public MessageResponse remove(AuthenticatedUser user,UUID id) { transition(user,id,"ACCEPTED","REMOVED",false); return new MessageResponse("Amizade removida."); }
    @Transactional public MessageResponse block(AuthenticatedUser user,String username) { repository.block(user.internalId(),target(user,username),clock.instant()); return new MessageResponse("Operador bloqueado."); }
    @Transactional public MessageResponse unblock(AuthenticatedUser user,String username) { repository.unblock(user.internalId(),target(user,username)); return new MessageResponse("Bloqueio removido."); }
    @Transactional public MessageResponse report(AuthenticatedUser user,String username,ReportRequest request) { try { repository.report(user.internalId(),target(user,username),request.reason(),request.details(),clock.instant()); } catch(DataIntegrityViolationException e) { throw BusinessException.conflict("REPORT_EXISTS","Já existe uma denúncia em análise."); } return new MessageResponse("Denúncia enviada para moderação."); }
    private void transition(AuthenticatedUser user,UUID id,String from,String to,boolean recipientOnly) { if(repository.transition(id,user.internalId(),from,to,recipientOnly,clock.instant())!=1) throw BusinessException.conflict("INVALID_CONNECTION_STATE","A solicitação já foi alterada ou não pertence a você."); }
    private long target(AuthenticatedUser user,String username) { long id=repository.userId(username).orElseThrow(() -> BusinessException.notFound("Operador não encontrado.")); if(id==user.internalId()) throw BusinessException.badRequest("INVALID_TARGET","Selecione outro operador."); return id; }
}
