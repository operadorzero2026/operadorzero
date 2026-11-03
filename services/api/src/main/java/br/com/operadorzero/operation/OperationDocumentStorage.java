package br.com.operadorzero.operation;

import br.com.operadorzero.media.MediaSettings;
import br.com.operadorzero.shared.web.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * PDFs das operacoes numa pasta privada (irma da pasta publica de midia, que o Nginx serve). Nada aqui tem URL publica:
 * a API le o arquivo so depois de conferir quem pede.
 */
@Component
public class OperationDocumentStorage {
    public static final long MAX_BYTES = 9L * 1024 * 1024;
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private final Path dir;

    public OperationDocumentStorage(MediaSettings settings) {
        this.dir = settings.root().resolveSibling("private").resolve("operation-docs");
    }

    /** Grava o corpo da requisicao como PDF; recusa o que nao comeca com %PDF- ou passa do limite. */
    public Stored save(InputStream body, long declaredLength) {
        if (declaredLength <= 0 || declaredLength > MAX_BYTES) {
            throw BusinessException.badRequest("DOCUMENT_TOO_LARGE", "O PDF pode ter no máximo 9 MB.");
        }
        String key = UUID.randomUUID() + ".pdf";
        Path target = path(key);
        try {
            Files.createDirectories(dir);
            byte[] head = body.readNBytes(PDF_MAGIC.length);
            if (!Arrays.equals(head, PDF_MAGIC)) throw BusinessException.badRequest("DOCUMENT_NOT_PDF", "Envie um arquivo PDF.");
            long total = head.length;
            try (OutputStream out = Files.newOutputStream(target)) {
                out.write(head);
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = body.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_BYTES) throw BusinessException.badRequest("DOCUMENT_TOO_LARGE", "O PDF pode ter no máximo 9 MB.");
                    out.write(buffer, 0, read);
                }
            }
            return new Stored(key, total);
        } catch (BusinessException exception) {
            delete(key);
            throw exception;
        } catch (IOException exception) {
            delete(key);
            throw new UncheckedIOException(exception);
        }
    }

    public Path path(String key) {
        if (!key.matches("[0-9a-f-]{36}\\.pdf")) throw new IllegalArgumentException("chave de documento invalida");
        return dir.resolve(key);
    }

    public void delete(String key) {
        try {
            Files.deleteIfExists(path(key));
        } catch (IOException | IllegalArgumentException ignored) {
            // Arquivo orfao nao impede a operacao; fica para limpeza manual.
        }
    }

    public record Stored(String key, long size) {}
}
