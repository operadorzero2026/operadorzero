package br.com.operadorzero.ranking;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class RankingDtos {
    private RankingDtos() {}
    public record RankingEntry(int position, UUID operatorId, String username, String displayName, String callsign,
        String city, String stateCode, UUID teamId, String teamName, String teamAcronym, BigDecimal gross,
        BigDecimal factor, BigDecimal bonus, BigDecimal penalties, BigDecimal finalScore, int positionVariation,
        int operationsConsidered) {}
    public record RankingResponse(String ruleVersion, List<RankingEntry> items) {}
}
