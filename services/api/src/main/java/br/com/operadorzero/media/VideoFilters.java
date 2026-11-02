package br.com.operadorzero.media;

import java.util.Map;
import java.util.Optional;

/**
 * Filtros taticos aplicados no servidor aos videos (as fotos chegam ja filtradas pelo cliente).
 * Cada codigo vira um trecho fixo de filtergraph do ffmpeg; nada vindo do cliente entra no comando.
 * Os filtros (Visao Noturna, Termica, Luneta, Capacete) entram na etapa de Filtros taticos.
 */
public final class VideoFilters {
    private static final Map<String, String> GRAPHS = Map.of();

    private VideoFilters() {}

    public static boolean isKnown(String code) {
        return code == null || code.isBlank() || GRAPHS.containsKey(code);
    }

    public static Optional<String> graph(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(GRAPHS.get(code));
    }
}
