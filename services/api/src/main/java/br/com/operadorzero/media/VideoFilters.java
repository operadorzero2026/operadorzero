package br.com.operadorzero.media;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Filtros taticos aplicados no servidor aos videos (as fotos chegam ja filtradas pelo cliente, com os mesmos efeitos em
 * src/feed/filters/tacticalFilters.ts). Cada codigo vira um trecho fixo de filtergraph do ffmpeg, linear (sem rotulos),
 * que entra entre o recorte e a escala; nada vindo do cliente entra no comando. Compativel com o ffmpeg 4.4 da imagem.
 * Para criar um filtro novo: um codigo aqui e o efeito equivalente no cliente.
 */
public final class VideoFilters {
    private static final String HUD = "0x9EF01A";
    private static final String FONT = "font='DejaVu Sans Mono'";
    private static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");

    /** Fora do circulo da luneta (raio de 46% do lado menor), em cada plano YUV. */
    private static final String OUTSIDE_SCOPE = "gt(hypot(X-W/2\\,Y-H/2)\\,0.46*min(W\\,H))";

    private static final Map<String, Function<Instant, String>> GRAPHS = Map.of(
        // Visao Noturna: cinza com contraste, tinta verde (fosforo), ruido de sensor e vinheta.
        "night_vision", now -> "hue=s=0,eq=contrast=1.3:brightness=0.05:gamma=1.4,"
            + "colorchannelmixer=rr=0.22:rg=0:rb=0:gr=0:gg=1:gb=0:br=0:bg=0:bb=0.32,noise=alls=24:allf=t+u,vignette=angle=PI/3.4",
        // Termica: luminancia mapeada numa paleta "ferro" (azul/roxo frio -> vermelho -> amarelo -> branco quente).
        "thermal", now -> "hue=s=0,format=rgb24,lutrgb="
            + "r='clip(3*val-128\\,0\\,255)':"
            + "g='clip(3*val-420\\,0\\,255)':"
            + "b='if(lt(val\\,80)\\,val*2.6\\,clip(208-(val-80)*2.4\\,0\\,255))+if(gt(val\\,215)\\,(val-215)*6\\,0)',format=yuv420p",
        // Luneta: so o circulo central fica visivel, com reticula em cruz e bordas escuras.
        "scope", now -> "format=yuv420p,geq="
            + "lum='if(" + OUTSIDE_SCOPE + "\\,16\\,lum(X\\,Y))':"
            + "cb='if(" + OUTSIDE_SCOPE + "\\,128\\,cb(X\\,Y))':"
            + "cr='if(" + OUTSIDE_SCOPE + "\\,128\\,cr(X\\,Y))',"
            + "drawbox=x=0:y=ih/2-2:w=iw:h=4:color=black@0.85:t=fill,"
            + "drawbox=x=iw/2-2:y=0:w=4:h=ih:color=black@0.85:t=fill,vignette=angle=PI/5",
        // Capacete: HUD com cantos, mira, rumo (bussola), relogio e contador de municao (decorativos).
        "helmet", VideoFilters::helmet);

    private VideoFilters() {}

    public static boolean isKnown(String code) {
        return code == null || code.isBlank() || GRAPHS.containsKey(code);
    }

    /** Filtergraph do codigo; "now" define o relogio do HUD (hora de Brasilia do processamento + tempo do video). */
    public static Optional<String> graph(String code, Instant now) {
        if (code == null) return Optional.empty();
        Function<Instant, String> graph = GRAPHS.get(code);
        return graph == null ? Optional.empty() : Optional.of(graph.apply(now));
    }

    private static String helmet(Instant now) {
        long localEpoch = now.atZone(BRAZIL).toLocalDateTime().toEpochSecond(ZoneOffset.UTC);
        return "eq=saturation=0.85:contrast=1.08,"
            + box("iw*0.06", "ih*0.06", "iw*0.08", "3") + box("iw*0.06", "ih*0.06", "3", "iw*0.08")
            + box("iw*0.86", "ih*0.06", "iw*0.08", "3") + box("iw*0.94-3", "ih*0.06", "3", "iw*0.08")
            + box("iw*0.06", "ih*0.94-3", "iw*0.08", "3") + box("iw*0.06", "ih*0.94-iw*0.08", "3", "iw*0.08")
            + box("iw*0.86", "ih*0.94-3", "iw*0.08", "3") + box("iw*0.94-3", "ih*0.94-iw*0.08", "3", "iw*0.08")
            + box("iw/2-iw*0.03", "ih/2-1", "iw*0.06", "3") + box("iw/2-1", "ih/2-iw*0.03", "3", "iw*0.06")
            + "drawtext=" + FONT + ":fontcolor=" + HUD + ":fontsize=h*0.028:x=(w-text_w)/2:y=h*0.07:"
            + "text='HDG %{eif\\:mod(45+t*12\\,360)\\:d\\:3}',"
            + "drawtext=" + FONT + ":fontcolor=" + HUD + ":fontsize=h*0.026:x=w*0.06:y=h*0.88:"
            + "text='%{pts\\:gmtime\\:" + localEpoch + "\\:%H\\\\\\:%M\\\\\\:%S}',"
            + "drawtext=" + FONT + ":fontcolor=" + HUD + ":fontsize=h*0.03:x=w*0.94-text_w:y=h*0.88:"
            + "text='%{eif\\:max(0\\,30-floor(t*2))\\:d\\:2}/30'";
    }

    private static String box(String x, String y, String w, String h) {
        return "drawbox=x=" + x + ":y=" + y + ":w=" + w + ":h=" + h + ":color=" + HUD + "@0.9:t=fill,";
    }
}
