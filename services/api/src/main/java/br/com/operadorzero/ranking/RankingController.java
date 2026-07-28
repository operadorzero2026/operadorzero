package br.com.operadorzero.ranking;

import br.com.operadorzero.ranking.RankingDtos.RankingResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated @RestController @RequestMapping("/api/rankings")
public class RankingController {
    private final RankingService service; public RankingController(RankingService service){this.service=service;}
    @GetMapping("/operators") RankingResponse operators(@RequestParam(defaultValue="")@Size(max=80)String city,
        @RequestParam(defaultValue="")@Size(max=2)String stateCode,@RequestParam(required=false)UUID teamId,
        @RequestParam(defaultValue="100")@Min(1)@Max(100)int limit){return service.operators(city,stateCode,teamId,limit);}
}
