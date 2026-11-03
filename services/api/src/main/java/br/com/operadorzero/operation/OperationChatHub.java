package br.com.operadorzero.operation;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Tempo real do chat: cada tela aberta recebe um aviso "mudou" e busca as novidades pela API (que confere o acesso).
 * O aviso nao carrega conteudo. Uma instancia da API na VPS, entao a lista fica em memoria.
 */
@Component
public class OperationChatHub {
    static final Duration TIMEOUT = Duration.ofMinutes(30);
    private final Map<Long, Set<SseEmitter>> listeners = new ConcurrentHashMap<>();

    public SseEmitter subscribe(long operationId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
        Set<SseEmitter> set = listeners.computeIfAbsent(operationId, key -> ConcurrentHashMap.newKeySet());
        set.add(emitter);
        Runnable remove = () -> set.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        send(emitter, "ready");
        return emitter;
    }

    /** Avisa depois que a transacao gravar (quem buscar ja encontra a mudanca). */
    public void changed(long operationId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { broadcast(operationId); }
            });
        } else {
            broadcast(operationId);
        }
    }

    void broadcast(long operationId) {
        listeners.getOrDefault(operationId, Set.of()).forEach(emitter -> send(emitter, "changed"));
    }

    /** Sinal de vida a cada 20 s: mantem a conexao aberta no proxy (que corta conexoes paradas por 60 s). */
    @Scheduled(fixedDelay = 20_000, initialDelay = 20_000)
    public void heartbeat() {
        listeners.values().forEach(set -> set.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException error) {
                set.remove(emitter);
            }
        }));
    }

    int count(long operationId) {
        return listeners.getOrDefault(operationId, Set.of()).size();
    }

    private void send(SseEmitter emitter, String name) {
        try {
            emitter.send(SseEmitter.event().name(name).data(name));
        } catch (IOException | IllegalStateException error) {
            emitter.completeWithError(error);
        }
    }
}
