package br.com.operadorzero.feed;

import java.time.Duration;
import java.util.Map;

/**
 * Ranking "Para voce" (sem IA), calculado no banco a cada pagina:
 *
 * <pre>
 * pontuacao = (1 + ln(1 + engajamento)) x 0,5^(idade / meia-vida) x (1 + bonus)
 * engajamento = 1 x (Na mira + Eliminado) + 2 x comentarios + 3 x compartilhamentos + 3 x condecoracoes
 *             + 4 x taxa de conclusao + 3 x fracao media assistida
 * bonus = mesmo estado 0,4 + mesma cidade 0,3 + campo onde a pessoa jogou 0,6 + mesma equipe 0,5
 *       + autor seguido 0,4 + modalidade de jogo que a pessoa joga 0,3
 * </pre>
 *
 * O logaritmo impede que um post viral esmague todo o resto; a meia-vida de 24 h mantem o feed fresco.
 * Ficam de fora posts com mais de 14 dias e os que a pessoa ja viu nos ultimos 3 dias.
 */
public final class FeedRanking {
    public static final Duration WINDOW = Duration.ofDays(14);
    public static final Duration SEEN_WINDOW = Duration.ofDays(3);
    public static final double HALF_LIFE_HOURS = 24;

    /** Parametros nomeados usados na consulta (mesmos nomes do SQL em FeedRepository.ranked). */
    public static final Map<String, Double> WEIGHTS = Map.ofEntries(
        Map.entry("wReaction", 1.0), Map.entry("wComment", 2.0), Map.entry("wShare", 3.0), Map.entry("wDecoration", 3.0),
        Map.entry("wCompletion", 4.0), Map.entry("wWatch", 3.0),
        Map.entry("bState", 0.4), Map.entry("bCity", 0.3), Map.entry("bField", 0.6), Map.entry("bTeam", 0.5),
        Map.entry("bFollow", 0.4), Map.entry("bModality", 0.3), Map.entry("halfLifeHours", HALF_LIFE_HOURS));

    private FeedRanking() {}
}
