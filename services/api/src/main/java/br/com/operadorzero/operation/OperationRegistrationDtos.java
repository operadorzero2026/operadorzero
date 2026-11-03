package br.com.operadorzero.operation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Inscricao em operacoes: lista unica ou por equipes, funcoes do jogador, remocoes e trocas de equipe. */
public final class OperationRegistrationDtos {
    private OperationRegistrationDtos() {}

    /** primaryRole: ASSAULT, SNIPER ou DMR. Medico e Operador de Radio sao opcionais e combinaveis. */
    public record RegisterRequest(UUID operationTeamId, UUID operationSquadId, @NotBlank @Size(max = 8) String primaryRole,
                                  boolean medic, boolean radio) {}

    public record RoleRequest(@NotBlank @Size(max = 8) String primaryRole, boolean medic, boolean radio) {}

    public record RemoveRequest(@NotBlank @Size(min = 3, max = 500) String reason) {}

    public record TeamChangeRequest(@NotNull UUID toTeamId) {}

    public record TeamChangeDecision(@NotBlank @Size(max = 8) String action) {}

    public record SettingsRequest(boolean teamChangeRequestsEnabled) {}

    public record TeamInfo(UUID id, String name, String acronym, String color, Integer capacity, long participantCount) {}

    public record Participant(UUID operatorId, String username, String callsign, String displayName, String status,
                              Instant requestedAt, int position, String primaryRole, boolean medic, boolean radio,
                              UUID teamId, String teamName, String teamColor) {}

    /** Medico e Radio sao contados a parte: um jogador Assalto + Medico + Radio conta 1 em cada e 1 no total. */
    public record Counts(long total, long assault, long sniper, long dmr, long medic, long radio, long withoutRole) {}

    public record PendingChange(UUID id, UUID toTeamId, String toTeamName, Instant createdAt) {}

    public record Mine(String status, String primaryRole, boolean medic, boolean radio, UUID teamId, String teamName,
                       String removalReason, Instant removedAt, PendingChange pendingChange) {}

    public record ChangeRequestItem(UUID id, UUID operatorId, String callsign, String fromTeamName, String toTeamName,
                                    Instant createdAt, String status, Instant decidedAt) {}

    public record RemovedItem(UUID operatorId, String callsign, String reason, Instant removedAt, String removedBy,
                              Instant returnAuthorizedAt) {}

    public record Overview(String model, boolean open, String closedReason, Instant closedAt, Instant startsAt,
                           boolean canCancel, boolean teamChangeRequestsEnabled, boolean managed, List<TeamInfo> teams,
                           List<Participant> participants, Counts counts, Mine mine,
                           List<ChangeRequestItem> changeRequests, List<RemovedItem> removed) {}
}
