package br.com.operadorzero.shared.image;

import br.com.operadorzero.shared.web.BusinessException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.web.multipart.MultipartFile;

public final class SafeRasterImageProcessor {
    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final int MAX_DIMENSION = 2048;

    private SafeRasterImageProcessor() {}

    public static ProcessedImage process(MultipartFile file) {
        if (file == null || file.isEmpty()) throw BusinessException.badRequest("EMPTY_IMAGE", "Selecione uma imagem.");
        if (file.getSize() > MAX_BYTES) throw tooLarge();
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid();
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("png", "jpeg", "jpg").contains(format)) throw invalid();
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw BusinessException.badRequest("IMAGE_DIMENSIONS", "A imagem deve ter no máximo 2048 × 2048 pixels.");
                }
                BufferedImage image = reader.read(0);
                String outputFormat = "png".equals(format) ? "png" : "jpg";
                String contentType = "png".equals(outputFormat) ? "image/png" : "image/jpeg";
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                if (!ImageIO.write(image, outputFormat, output)) throw invalid();
                byte[] sanitized = output.toByteArray();
                if (sanitized.length == 0 || sanitized.length > MAX_BYTES) throw tooLarge();
                return new ProcessedImage(contentType, sanitized);
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return BusinessException.badRequest("INVALID_IMAGE", "Envie uma imagem PNG ou JPEG válida.");
    }

    private static BusinessException tooLarge() {
        return BusinessException.badRequest("IMAGE_TOO_LARGE", "A imagem deve ter no máximo 2 MB.");
    }

    public record ProcessedImage(String contentType, byte[] data) {}
}
