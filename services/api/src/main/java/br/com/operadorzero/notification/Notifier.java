package br.com.operadorzero.notification;

import java.util.UUID;

/** Avisos dentro da plataforma. targetView e o nome da area do painel que a notificacao abre. */
public interface Notifier {
    void toUser(long userId, String kind, String title, String body, String targetView);

    /** Aviso que abre um endereco do app (ex.: /convocacao/{id}); quem nao suporta cai na area do painel. */
    default void toUserAt(long userId, String kind, String title, String body, String targetView, String targetPath) {
        toUser(userId, kind, title, body, targetView);
    }

    void toOperator(UUID operatorId, String kind, String title, String body, String targetView);

    void toTeamManagers(long teamId, String kind, String title, String body, String targetView);

    /** Todos os inscritos ativos da operacao, exceto o organizador. */
    void toOperationParticipants(UUID operationId, String kind, String title, String body, String targetView);

    static Notifier none() {
        return new Notifier() {
            @Override public void toUser(long userId, String kind, String title, String body, String targetView) {}
            @Override public void toOperator(UUID operatorId, String kind, String title, String body, String targetView) {}
            @Override public void toTeamManagers(long teamId, String kind, String title, String body, String targetView) {}
            @Override public void toOperationParticipants(UUID operationId, String kind, String title, String body, String targetView) {}
        };
    }
}
