package br.com.operadorzero.media;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public final class MediaDtos {
    private MediaDtos() {}

    /** Enquadramento escolhido na previa: centro (0..1) e zoom (1..5). Usado nos videos; fotos ja chegam recortadas. */
    public record Crop(@DecimalMin("0") @DecimalMax("1") BigDecimal x,
                       @DecimalMin("0") @DecimalMax("1") BigDecimal y,
                       @DecimalMin("1") @DecimalMax("5") BigDecimal zoom) {}

    public record StartUploadRequest(@NotBlank @Pattern(regexp = "POST|RECON") String purpose,
                                     @NotBlank @Pattern(regexp = "IMAGE|VIDEO") String kind,
                                     @NotBlank @Size(max = 80) String contentType,
                                     @Positive long totalBytes,
                                     @Size(max = 32) @Pattern(regexp = "[a-z0-9_]*") String filterCode,
                                     @Valid Crop crop) {}

    /** Opcoes conhecidas so ao publicar (editor do Recon): tirar o audio e o enquadramento final do video. */
    public record CompleteUploadRequest(Boolean stripAudio, @Valid Crop crop) {}

    public record UploadResponse(UUID uploadId, UUID mediaId, int chunkBytes, long receivedBytes, long totalBytes, String status) {}

    public record MediaStatusResponse(UUID id, String kind, String status, String errorCode) {}
}
