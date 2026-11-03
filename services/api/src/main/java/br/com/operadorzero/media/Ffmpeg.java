package br.com.operadorzero.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Chama ffprobe/ffmpeg como processos separados, com tempo limite e sem shell (argumentos nunca sao interpretados). */
@Component
public class Ffmpeg {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Ffmpeg.class);
    public record Probe(String kind, int width, int height, Integer durationMs, boolean hasAudio, Instant capturedAt) {}

    public static class MediaRejected extends Exception {
        private final String code;

        public MediaRejected(String code) {
            super(code);
            this.code = code;
        }

        public String code() { return code; }
    }

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(30);
    private final MediaSettings settings;
    private final ObjectMapper json;

    public Ffmpeg(MediaSettings settings, ObjectMapper json) {
        this.settings = settings;
        this.json = json;
    }

    public Probe probe(Path file) throws IOException, MediaRejected {
        String output = run(List.of(settings.ffprobe(), "-v", "error", "-print_format", "json", "-show_format", "-show_streams",
            file.toString()), PROBE_TIMEOUT, "PROBE_FAILED", null);
        JsonNode root = json.readTree(output);
        JsonNode video = null;
        boolean audio = false;
        for (JsonNode stream : root.path("streams")) {
            if ("video".equals(stream.path("codec_type").asText()) && video == null) video = stream;
            if ("audio".equals(stream.path("codec_type").asText())) audio = true;
        }
        if (video == null) throw new MediaRejected("NO_VIDEO_STREAM");
        int width = video.path("width").asInt();
        int height = video.path("height").asInt();
        if (width <= 0 || height <= 0) throw new MediaRejected("INVALID_DIMENSIONS");
        // Celulares gravam "deitado" e guardam a rotacao; as dimensoes exibidas sao as giradas.
        if (Math.abs(rotation(video)) % 180 == 90) { int swap = width; width = height; height = swap; }
        double seconds = root.path("format").path("duration").asDouble(video.path("duration").asDouble(0));
        String format = root.path("format").path("format_name").asText();
        // Gravacoes do navegador (MediaRecorder, WebM) nao trazem a duracao no cabecalho: mede pelos pacotes.
        if (seconds <= 0 && format.contains("webm")) seconds = packetDuration(file);
        boolean still = format.contains("image2") || format.contains("png_pipe") || format.contains("jpeg_pipe") || format.contains("webp_pipe")
            || (seconds <= 0 && root.path("streams").size() == 1);
        String kind = still ? "IMAGE" : "VIDEO";
        Integer durationMs = still ? null : (int) Math.round(seconds * 1000);
        return new Probe(kind, width, height, durationMs, audio, capturedAt(root.path("format").path("tags")));
    }

    private double packetDuration(Path file) throws IOException, MediaRejected {
        String output = run(List.of(settings.ffprobe(), "-v", "error", "-select_streams", "v:0", "-show_entries", "packet=pts_time",
            "-of", "csv=p=0", file.toString()), PROBE_TIMEOUT, "PROBE_FAILED", null);
        double last = 0;
        for (String line : output.split("\\R")) {
            try { last = Math.max(last, Double.parseDouble(line.strip().replace(",", ""))); } catch (NumberFormatException ignored) { /* linha vazia */ }
        }
        return last;
    }

    public void run(List<String> args, Duration timeout) throws IOException, MediaRejected {
        run(args, timeout, null);
    }

    /** {@code workingDir} define onde caminhos relativos dos argumentos sao gravados. */
    public void run(List<String> args, Duration timeout, Path workingDir) throws IOException, MediaRejected {
        List<String> command = new ArrayList<>();
        if (settings.lowPriority()) command.addAll(List.of("nice", "-n", "15"));
        command.add(settings.ffmpeg());
        command.addAll(List.of("-hide_banner", "-loglevel", "error", "-nostdin", "-y"));
        command.addAll(args);
        run(command, timeout, "TRANSCODE_FAILED", workingDir);
    }

    private String run(List<String> command, Duration timeout, String errorCode, Path workingDir) throws IOException, MediaRejected {
        // Saidas em arquivo: ler o pipe direto bloquearia e o tempo limite nunca dispararia.
        Path log = Files.createTempFile("oz-ffmpeg", ".log");
        Path out = Files.createTempFile("oz-ffmpeg", ".out");
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectOutput(out.toFile()).redirectError(log.toFile());
            if (workingDir != null) builder.directory(workingDir.toFile());
            Process process = builder.start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new MediaRejected("PROCESSING_TIMEOUT");
            }
            if (process.exitValue() != 0) {
                String stderr = Files.readString(log, StandardCharsets.UTF_8);
                log(errorCode, stderr);
                throw new MediaRejected(errorCode);
            }
            return Files.readString(out, StandardCharsets.UTF_8);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MediaRejected("PROCESSING_INTERRUPTED");
        } finally {
            Files.deleteIfExists(log);
            Files.deleteIfExists(out);
        }
    }

    private static void log(String errorCode, String stderr) {
        String tail = stderr.length() > 800 ? stderr.substring(stderr.length() - 800) : stderr;
        LOG.warn("media_tool_failed code={} stderr={}", errorCode, tail.replaceAll("\s+", " ").strip());
    }

    private static int rotation(JsonNode video) {
        JsonNode tags = video.path("tags").path("rotate");
        if (!tags.isMissingNode()) return tags.asInt();
        for (JsonNode side : video.path("side_data_list")) {
            if (side.has("rotation")) return side.path("rotation").asInt();
        }
        return 0;
    }

    /** So o horario de gravacao e aproveitado (Debriefing); GPS, aparelho e demais metadados sao descartados. */
    private static Instant capturedAt(JsonNode tags) {
        for (String key : List.of("com.apple.quicktime.creationdate", "creation_time")) {
            // Alguns arquivos trazem o mesmo campo repetido ("a;b"): vale o primeiro.
            String value = tags.path(key).asText("").split(";")[0].strip();
            if (value.isBlank()) continue;
            try {
                return java.time.OffsetDateTime.parse(value).toInstant();
            } catch (DateTimeParseException ignored) {
                try { return Instant.parse(value); } catch (DateTimeParseException alsoIgnored) { /* proxima chave */ }
            }
        }
        return null;
    }
}
