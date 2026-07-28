package br.com.operadorzero.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RankingCalculatorTest {
    @Test
    void appliesConfirmedBonusesPenaltiesAndExperienceFactor() {
        var totals = new RankingCalculator.Totals(2, 10, 4, 3, 1, 0,
            new BigDecimal("85"), new BigDecimal("30"), new BigDecimal("7"));
        var score = RankingCalculator.score(totals);
        assertThat(score.gross()).isEqualByComparingTo("85.00");
        assertThat(score.bonus()).isEqualByComparingTo("10.00");
        assertThat(score.penalties()).isEqualByComparingTo("7.00");
        assertThat(score.factor()).isEqualByComparingTo("0.40");
        assertThat(score.finalScore()).isEqualByComparingTo("35.20");
    }

    @Test
    void definesAllExperienceBandsAndPreservesNegativePoints() {
        assertThat(RankingCalculator.factor(0)).isEqualByComparingTo("0.00");
        assertThat(RankingCalculator.factor(2)).isEqualByComparingTo("0.40");
        assertThat(RankingCalculator.factor(5)).isEqualByComparingTo("0.60");
        assertThat(RankingCalculator.factor(10)).isEqualByComparingTo("0.80");
        assertThat(RankingCalculator.factor(11)).isEqualByComparingTo("1.00");
        assertThat(RankingCalculator.operationGross(0, 20, 0, "LOSS")).isEqualByComparingTo("-10");
    }

    @Test
    void doesNotGiveBonusToSelfDeclaredPoints() {
        var score = RankingCalculator.score(new RankingCalculator.Totals(1, 2, 1, 0, 0, 1,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThat(score.gross()).isEqualByComparingTo("20.00");
        assertThat(score.bonus()).isEqualByComparingTo("0.00");
        assertThat(score.finalScore()).isEqualByComparingTo("8.00");
    }
}
