package br.com.operadorzero.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.web.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperationSizeRulesTest {
    private final AuthenticatedUser user=new AuthenticatedUser(1L,UUID.randomUUID(),"op@example.test","operator","Op","Zero",List.of("OPERATOR"));
    private final OperationService service=new OperationService(mock(OperationRepository.class),mock(AuditEventRepository.class),Clock.fixed(Instant.parse("2026-07-29T12:00:00Z"),ZoneOffset.UTC));

    @Test void smallRejectsMoreThanFiftyPlayers(){assertRule("SMALL",51,2,"SMALL_OPERATION_LIMITS");}
    @Test void smallRejectsAThirdTeam(){assertRule("SMALL",50,3,"SMALL_OPERATION_LIMITS");}
    @Test void mediumRejectsMoreThanOneHundredPlayers(){assertRule("MEDIUM",101,4,"MEDIUM_OPERATION_LIMITS");}
    @Test void mediumRejectsAFifthTeam(){assertRule("MEDIUM",100,5,"MEDIUM_OPERATION_LIMITS");}

    private void assertRule(String size,int players,int teams,String code){
        assertThatThrownBy(()->service.create(user,request(size,players,teams))).isInstanceOfSatisfying(BusinessException.class,
            exception->assertThat(exception.code()).isEqualTo(code));
    }
    private OperationDtos.SaveOperationRequest request(String size,int players,int teams){return new OperationDtos.SaveOperationRequest(
        "Operação teste","Descrição segura",UUID.randomUUID(),null,LocalDate.of(2026,8,1),LocalTime.of(8,0),LocalTime.of(9,0),LocalTime.of(17,0),
        "ELIMINATION",null,"Regras",size,players,teams,BigDecimal.ZERO,null,18,null,null,"INDIVIDUAL",false,true);}
}
