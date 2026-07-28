package br.com.operadorzero.ranking;

import br.com.operadorzero.ranking.RankingCalculator.Score;
import br.com.operadorzero.ranking.RankingCalculator.Totals;
import br.com.operadorzero.ranking.RankingDtos.RankingEntry;
import br.com.operadorzero.ranking.RankingDtos.RankingResponse;
import br.com.operadorzero.ranking.RankingRepository.RankingRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RankingService {
    private final RankingRepository repository;
    public RankingService(RankingRepository repository) { this.repository=repository; }
    public RankingResponse operators(String city,String stateCode,UUID teamId,int limit) {
        List<Scored> scored=repository.operatorTotals(normalize(city),normalize(stateCode),teamId,Math.min(1000,Math.max(limit,100)))
            .stream().map(row->new Scored(row,RankingCalculator.score(new Totals(row.validOperations(),row.eliminations(),
                row.deaths(),row.objectives(),row.wins(),row.draws(),row.organizerGross(),row.teamGross(),row.penalties()))))
            .sorted(Comparator.comparing((Scored item)->item.score().finalScore()).reversed()
                .thenComparing(item->item.row().callsign(),String.CASE_INSENSITIVE_ORDER)).toList();
        List<RankingEntry> entries=new ArrayList<>();
        for(int i=0;i<Math.min(limit,scored.size());i++){Scored item=scored.get(i);RankingRow r=item.row();Score s=item.score();entries.add(new RankingEntry(i+1,r.operatorId(),r.username(),r.displayName(),r.callsign(),r.city(),r.stateCode(),r.teamId(),r.teamName(),r.teamAcronym(),s.gross(),s.factor(),s.bonus(),s.penalties(),s.finalScore(),0,r.validOperations()));}
        return new RankingResponse(RankingCalculator.RULE_VERSION,entries);
    }
    private String normalize(String value){return value==null?"":value.trim().replaceAll("\\s+"," ");}
    private record Scored(RankingRow row,Score score){}
}
