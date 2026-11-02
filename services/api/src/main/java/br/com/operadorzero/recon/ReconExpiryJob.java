package br.com.operadorzero.recon;

import br.com.operadorzero.media.MediaStorage;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Encerra Recons com mais de 24 h e apaga a midia dos que nao foram guardados em Dossie.
 * A leitura ja filtra por expires_at, entao um Recon vencido some da barra mesmo antes desta rodada.
 */
@Component
public class ReconExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(ReconExpiryJob.class);
    private final ReconRepository repository;
    private final MediaStorage storage;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Autowired
    public ReconExpiryJob(ReconRepository repository, MediaStorage storage, TransactionTemplate transactions) {
        this(repository, storage, transactions, Clock.systemUTC());
    }

    ReconExpiryJob(ReconRepository repository, MediaStorage storage, TransactionTemplate transactions, Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT10M")
    public void run() {
        Instant now = clock.instant();
        Integer expired = transactions.execute(status -> repository.expire(now));
        int discarded = 0;
        for (var media : repository.mediaToDiscard(200)) {
            try {
                if (media.storagePrefix() != null) storage.delete(media.storagePrefix());
                repository.markMediaDeleted(media.assetId(), now);
                discarded++;
            } catch (IOException | IllegalArgumentException exception) {
                log.warn("recon_media_discard_failed asset={}", media.assetId(), exception);
            }
        }
        if ((expired != null && expired > 0) || discarded > 0) log.info("recon_expiry expired={} media_discarded={}", expired, discarded);
    }
}
