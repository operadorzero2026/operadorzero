package br.com.operadorzero.recon;

import br.com.operadorzero.feed.FeedDtos.MediaResponse;
import br.com.operadorzero.recon.ReconComposition.Element;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ReconDtos {
    private ReconDtos() {}

    /** elements: composicao do editor (validada em ReconComposition); dossierId: guardar automaticamente nesse Dossie. */
    public record CreateReconRequest(@NotNull UUID mediaId, @Size(max = 140) String overlayText, @Size(max = 40) List<Element> elements,
                                     Boolean audioMuted, UUID dossierId) {
        public CreateReconRequest(UUID mediaId, String overlayText) {
            this(mediaId, overlayText, null, null, null);
        }
    }

    public record ReconCreated(UUID id, String status) {}

    /** Um circulo da barra: um autor com Recons ativos. */
    public record BarEntry(String username, String callsign, String rankCode, int count, boolean hasUnseen, Instant latestAt, boolean own) {}

    public record ReconBar(List<BarEntry> items, boolean ownProcessing) {}

    public record ReconItem(UUID id, String overlayText, MediaResponse media, Instant publishedAt, Instant expiresAt, boolean seen,
                            List<Element> elements, boolean audioMuted) {}

    public record AuthorRecons(String username, String callsign, String rankCode, boolean own, List<ReconItem> items) {}

    public record CreateDossierRequest(@NotBlank @Size(max = 40) String title) {}

    public record AddToDossierRequest(@NotNull UUID reconId) {}

    public record DossierSummary(UUID id, String title, String coverUrl, int count) {}

    public record DossierDetail(UUID id, String title, String ownerUsername, boolean own, List<ReconItem> items) {}
}
