package br.com.operadorzero.ranking;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class RankingCalculator {
    public static final String RULE_VERSION = "OZ-RANK-2026-07-28";
    private static final BigDecimal ORGANIZER_BONUS = new BigDecimal("0.10");
    private static final BigDecimal TEAM_BONUS = new BigDecimal("0.05");

    private RankingCalculator() {}

    public static Score score(Totals totals) {
        BigDecimal gross = BigDecimal.valueOf(10L * totals.validOperations())
            .add(BigDecimal.valueOf(3L * totals.eliminations()))
            .subtract(BigDecimal.valueOf(totals.deaths()))
            .add(BigDecimal.valueOf(8L * totals.objectives()))
            .add(BigDecimal.valueOf(15L * totals.wins()))
            .add(BigDecimal.valueOf(5L * totals.draws()));
        BigDecimal organizerBonus = totals.organizerConfirmedGross().multiply(ORGANIZER_BONUS);
        BigDecimal teamBonus = totals.teamConfirmedGross().multiply(TEAM_BONUS);
        BigDecimal bonus = organizerBonus.add(teamBonus);
        BigDecimal factor = factor(totals.validOperations());
        BigDecimal finalScore = gross.add(bonus).subtract(totals.penalties()).multiply(factor);
        return new Score(money(gross), money(bonus), money(totals.penalties()), factor, money(finalScore));
    }

    public static BigDecimal operationGross(int eliminations, int deaths, int objectives, String result) {
        long resultPoints = "WIN".equals(result) ? 15 : "DRAW".equals(result) ? 5 : 0;
        return BigDecimal.valueOf(10L + 3L * eliminations - deaths + 8L * objectives + resultPoints);
    }

    public static BigDecimal factor(int validOperations) {
        if (validOperations <= 0) return BigDecimal.ZERO.setScale(2);
        if (validOperations <= 2) return new BigDecimal("0.40");
        if (validOperations <= 5) return new BigDecimal("0.60");
        if (validOperations <= 10) return new BigDecimal("0.80");
        return BigDecimal.ONE.setScale(2);
    }

    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

    public record Totals(int validOperations, long eliminations, long deaths, long objectives, long wins, long draws,
                         BigDecimal organizerConfirmedGross, BigDecimal teamConfirmedGross, BigDecimal penalties) {}
    public record Score(BigDecimal gross, BigDecimal bonus, BigDecimal penalties, BigDecimal factor, BigDecimal finalScore) {}
}
