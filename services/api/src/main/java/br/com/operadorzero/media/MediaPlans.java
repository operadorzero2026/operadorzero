package br.com.operadorzero.media;

import br.com.operadorzero.media.MediaDtos.Crop;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Monta os argumentos do ffmpeg. Funcoes puras para poderem ser testadas sem o binario. */
final class MediaPlans {
    record Size(int width, int height) {}
    record Window(int width, int height, int x, int y) {}
    record Rendition(int shortSide, int width, int height, int kbps) {}

    /** Altura/lado curto alvo -> bitrate de video (kbps). */
    static final Map<Integer, Integer> LADDER = Map.of(360, 800, 720, 2500, 1080, 5000);
    private static final double TARGET_ASPECT = 9.0 / 16.0;

    private MediaPlans() {}

    /** Janela 9:16 escolhida na previa: o zoom estreita a janela e o centro (x, y) a posiciona dentro do quadro. */
    static Window cropWindow(int width, int height, Crop crop) {
        if (crop == null) return null;
        double zoom = crop.zoom().doubleValue();
        double baseW = (double) width / height > TARGET_ASPECT ? height * TARGET_ASPECT : width;
        double baseH = baseW / TARGET_ASPECT;
        int w = even(baseW / zoom);
        int h = even(baseH / zoom);
        int x = even(clamp(crop.x().doubleValue() * width - w / 2.0, 0, width - w));
        int y = even(clamp(crop.y().doubleValue() * height - h / 2.0, 0, height - h));
        return new Window(w, h, x, y);
    }

    /** Lados curtos 360/720/1080 que cabem na origem (sem ampliar); sempre ha ao menos uma versao. */
    static List<Rendition> ladder(int width, int height) {
        int shortSide = Math.min(width, height);
        List<Rendition> result = new ArrayList<>();
        for (int target : List.of(360, 720, 1080)) {
            if (target <= shortSide) result.add(rendition(width, height, target, LADDER.get(target)));
        }
        if (result.isEmpty()) result.add(rendition(width, height, even(shortSide), LADDER.get(360)));
        return result;
    }

    static List<String> hlsArgs(Path source, Window crop, List<Rendition> renditions, boolean audio, String filterGraph) {
        StringBuilder graph = new StringBuilder("[0:v]");
        if (crop != null) graph.append("crop=%d:%d:%d:%d,".formatted(crop.width(), crop.height(), crop.x(), crop.y()));
        if (filterGraph != null) graph.append(filterGraph).append(',');
        graph.append("split=").append(renditions.size());
        for (int i = 0; i < renditions.size(); i++) graph.append("[s").append(i).append(']');
        for (int i = 0; i < renditions.size(); i++) {
            Rendition r = renditions.get(i);
            graph.append(";[s%d]scale=%d:%d,setsar=1[v%d]".formatted(i, r.width(), r.height(), i));
        }
        List<String> args = new ArrayList<>(List.of("-i", source.toString(), "-map_metadata", "-1", "-map_chapters", "-1",
            "-filter_complex", graph.toString()));
        StringBuilder streamMap = new StringBuilder();
        for (int i = 0; i < renditions.size(); i++) {
            Rendition r = renditions.get(i);
            args.addAll(List.of("-map", "[v" + i + "]", "-c:v:" + i, "libx264", "-b:v:" + i, r.kbps() + "k",
                "-maxrate:v:" + i, (int) (r.kbps() * 1.07) + "k", "-bufsize:v:" + i, (int) (r.kbps() * 1.5) + "k"));
            if (audio) args.addAll(List.of("-map", "0:a:0"));
            if (i > 0) streamMap.append(' ');
            streamMap.append("v:").append(i);
            if (audio) streamMap.append(",a:").append(i);
        }
        args.addAll(List.of("-preset", "veryfast", "-profile:v", "main", "-pix_fmt", "yuv420p", "-g", "60", "-keyint_min", "60",
            "-sc_threshold", "0", "-threads", "2"));
        if (audio) args.addAll(List.of("-c:a", "aac", "-b:a", "128k", "-ac", "2"));
        args.addAll(List.of("-f", "hls", "-hls_time", "4", "-hls_playlist_type", "vod", "-hls_flags", "independent_segments",
            // Caminhos relativos: o ffmpeg roda dentro de "out" (cria v0, v1... sem depender de caminho absoluto).
            "-hls_segment_filename", "v%v/seg_%03d.ts",
            "-master_pl_name", "master.m3u8", "-var_stream_map", streamMap.toString(),
            "v%v/index.m3u8"));
        return args;
    }

    /** MP4 pequeno (menor versao) para navegadores sem HLS e como reserva. */
    static List<String> fallbackMp4Args(Path source, Path target, Window crop, Rendition smallest, boolean audio, String filterGraph) {
        List<String> args = new ArrayList<>(List.of("-i", source.toString(), "-map_metadata", "-1", "-map_chapters", "-1",
            "-vf", videoFilter(crop, filterGraph, smallest.width(), smallest.height()), "-map", "0:v:0"));
        if (audio) args.addAll(List.of("-map", "0:a:0", "-c:a", "aac", "-b:a", "96k", "-ac", "2"));
        args.addAll(List.of("-c:v", "libx264", "-preset", "veryfast", "-crf", "27", "-profile:v", "main", "-pix_fmt", "yuv420p",
            "-threads", "2", "-movflags", "+faststart", target.toString()));
        return args;
    }

    static List<String> posterArgs(Path source, Path target, Window crop, String filterGraph, Size size, double atSeconds) {
        return List.of("-ss", String.valueOf(atSeconds), "-i", source.toString(), "-map_metadata", "-1", "-frames:v", "1",
            "-vf", videoFilter(crop, filterGraph, size.width(), size.height()), "-c:v", "libwebp", "-quality", "80", target.toString());
    }

    static List<String> imageArgs(Path source, Path target, int width) {
        return List.of("-i", source.toString(), "-map_metadata", "-1", "-frames:v", "1",
            "-vf", "scale=%d:-2".formatted(width), "-c:v", "libwebp", "-quality", "82", target.toString());
    }

    static List<String> placeholderArgs(Path source, Path target) {
        return List.of("-i", source.toString(), "-map_metadata", "-1", "-frames:v", "1", "-vf", "scale=16:-2",
            "-c:v", "libwebp", "-quality", "40", target.toString());
    }

    static Size fit(int width, int height, int shortSide) {
        return width <= height
            ? new Size(even(shortSide), even((double) height * shortSide / width))
            : new Size(even((double) width * shortSide / height), even(shortSide));
    }

    private static Rendition rendition(int width, int height, int shortSide, int kbps) {
        Size size = fit(width, height, shortSide);
        return new Rendition(shortSide, size.width(), size.height(), kbps);
    }

    private static String videoFilter(Window crop, String filterGraph, int width, int height) {
        StringBuilder filter = new StringBuilder();
        if (crop != null) filter.append("crop=%d:%d:%d:%d,".formatted(crop.width(), crop.height(), crop.x(), crop.y()));
        if (filterGraph != null) filter.append(filterGraph).append(',');
        return filter.append("scale=%d:%d,setsar=1".formatted(width, height)).toString();
    }

    /** Arredonda para baixo ate um par (H.264 exige dimensoes pares) e nunca passa do quadro de origem. */
    private static int even(double value) {
        return Math.max(2, (int) Math.floor(value / 2.0) * 2);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
