package br.com.operadorzero.media;

import java.io.IOException;
import java.nio.file.Path;

/** Onde ficam as midias prontas. Hoje e o disco da VPS; um object storage (R2/S3) entra implementando esta interface. */
public interface MediaStorage {
    /** Move todos os arquivos de {@code directory} para {@code prefix}, que passa a ser publico em /media/{prefix}/. */
    void publish(Path directory, String prefix) throws IOException;

    void delete(String prefix) throws IOException;
}
