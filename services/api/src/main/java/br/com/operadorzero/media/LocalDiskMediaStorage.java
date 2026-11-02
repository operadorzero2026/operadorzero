package br.com.operadorzero.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class LocalDiskMediaStorage implements MediaStorage {
    private static final Pattern SAFE_PREFIX = Pattern.compile("^[a-z]/[0-9a-f]{2}/[0-9a-f-]{36}$");
    private final MediaSettings settings;

    public LocalDiskMediaStorage(MediaSettings settings) {
        this.settings = settings;
    }

    @Override
    public void publish(Path directory, String prefix) throws IOException {
        Path target = resolve(prefix);
        Files.createDirectories(target.getParent());
        try {
            Files.move(directory, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException crossDevice) {
            // Diretorio de trabalho em outro volume: copia arquivo a arquivo e apaga a origem.
            Files.createDirectories(target);
            try (Stream<Path> files = Files.walk(directory)) {
                for (Path source : files.sorted().toList()) {
                    Path destination = target.resolve(directory.relativize(source).toString());
                    if (Files.isDirectory(source)) Files.createDirectories(destination);
                    else Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            deleteTree(directory);
        }
    }

    @Override
    public void delete(String prefix) throws IOException {
        deleteTree(resolve(prefix));
    }

    private Path resolve(String prefix) {
        if (!SAFE_PREFIX.matcher(prefix).matches()) throw new IllegalArgumentException("Prefixo de mídia inválido.");
        return settings.root().resolve(prefix).normalize();
    }

    static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> files = Files.walk(path)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        }
    }
}
