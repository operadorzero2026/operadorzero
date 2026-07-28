package br.com.operadorzero.performance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.performance.PerformanceDtos.ContestRequest;
import br.com.operadorzero.performance.PerformanceDtos.SavePerformanceRequest;
import br.com.operadorzero.performance.PerformanceRepository.OperationAccess;
import br.com.operadorzero.performance.PerformanceRepository.RecordRef;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.web.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PerformanceServiceTest {
    private final PerformanceRepository repository=mock(PerformanceRepository.class);
    private final AuditEventRepository audit=mock(AuditEventRepository.class);
    private final Clock clock=Clock.fixed(Instant.parse("2026-07-28T12:00:00Z"),ZoneOffset.UTC);
    private PerformanceService service;private AuthenticatedUser user;
    @BeforeEach void setup(){service=new PerformanceService(repository,audit,clock);user=new AuthenticatedUser(10,UUID.randomUUID(),"user@example.test","operator","Operator","Zero",List.of("USER"));}

    @Test void onlyConfirmedParticipantCanSavePerformance(){UUID operationId=UUID.randomUUID();when(repository.operationAccess(operationId,user.internalId())).thenReturn(Optional.of(new OperationAccess(50,operationId,"Operação","FINISHED",99,"REQUESTED",null,null,null)));SavePerformanceRequest request=new SavePerformanceRequest(1,1,0,0,"NONE",null,null,null,null,false,"FULL",BigDecimal.ZERO,0L);assertThatThrownBy(()->service.save(user,operationId,request)).isInstanceOfSatisfying(BusinessException.class,e->org.assertj.core.api.Assertions.assertThat(e.status().value()).isEqualTo(403));}

    @Test void unrelatedUserCannotContestPerformance(){UUID recordId=UUID.randomUUID();when(repository.recordRef(recordId)).thenReturn(Optional.of(new RecordRef(70,50,99)));when(repository.participated(50,user.internalId())).thenReturn(false);assertThatThrownBy(()->service.contest(user,recordId,new ContestRequest("Motivo detalhado da contestação"))).isInstanceOfSatisfying(BusinessException.class,e->org.assertj.core.api.Assertions.assertThat(e.status().value()).isEqualTo(403));}
}
