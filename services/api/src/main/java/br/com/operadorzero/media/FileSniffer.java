package br.com.operadorzero.media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/** Descobre o tipo real pelo conteudo (magic bytes), sem confiar na extensao nem no tipo declarado. */
public final class FileSniffer {
    public record Detected(String kind, String type) {}

    private static final Set<String> HEIF_BRANDS = Set.of("heic", "heix", "heim", "heis", "mif1", "msf1", "avif");
    private static final Set<String> QUICKTIME_BRANDS = Set.of("qt  ");

    private FileSniffer() {}

    public static Optional<Detected> sniff(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return sniff(in.readNBytes(32));
        }
    }

    static Optional<Detected> sniff(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Optional.of(new Detected("IMAGE", "image/jpeg"));
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
            && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return Optional.of(new Detected("IMAGE", "image/png"));
        }
        if (b.length >= 12 && ascii(b, 0, 4).equals("RIFF") && ascii(b, 8, 4).equals("WEBP")) {
            return Optional.of(new Detected("IMAGE", "image/webp"));
        }
        if (b.length >= 4 && (b[0] & 0xFF) == 0x1A && (b[1] & 0xFF) == 0x45 && (b[2] & 0xFF) == 0xDF && (b[3] & 0xFF) == 0xA3) {
            return Optional.of(new Detected("VIDEO", "video/webm"));
        }
        // Familia ISO BMFF: "....ftyp<brand>" serve tanto para MP4/MOV quanto para HEIC.
        if (b.length >= 12 && ascii(b, 4, 4).equals("ftyp")) {
            String brand = ascii(b, 8, 4);
            if (HEIF_BRANDS.contains(brand)) return Optional.of(new Detected("IMAGE", "avif".equals(brand) ? "image/avif" : "image/heic"));
            if (QUICKTIME_BRANDS.contains(brand)) return Optional.of(new Detected("VIDEO", "video/quicktime"));
            return Optional.of(new Detected("VIDEO", "video/mp4"));
        }
        return Optional.empty();
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.ISO_8859_1);
    }
}
