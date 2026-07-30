package br.com.operadorzero.operation;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class OperationDtos {
    private OperationDtos() {}

    public record OperationFilter(
        @Size(max = 80) String q,
        @Size(max = 80) String city,
        @Size(max = 2) String stateCode,
        @Size(max = 40) String modality,
        @Size(max = 32) String status,
        LocalDate from,
        LocalDate to,
        @Min(1) @Max(100) int limit
    ) {}

    public record SaveOperationRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 4000) String description,
        @NotNull UUID fieldId,
        UUID mapId,
        @NotNull @FutureOrPresent LocalDate operationDate,
        @NotNull LocalTime presentationTime,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotBlank @Size(max = 40) String modality,
        @Size(max = 100) String customModality,
        @Size(max = 6000) String rules,
        @NotBlank @Size(max = 8) String gameSize,
        @NotNull @Positive Integer participantLimit,
        @Positive @Max(20) Integer teamLimit,
        @NotNull @PositiveOrZero BigDecimal registrationPrice,
        @Size(max = 500) String paymentMethods,
        @NotNull @Min(12) @Max(99) Integer minimumAge,
        @Size(max = 2000) String requiredEquipment,
        @Min(100) @Max(1000) Integer fpsLimit,
        @NotBlank @Size(max = 24) String entryMode,
        boolean approvalRequired,
        boolean waitingListEnabled
    ) {}

    public record UpdateStatusRequest(@NotBlank @Size(max = 32) String status, @Size(max = 500) String reason) {}
    public record UpdateOperationRequest(
        @NotNull @FutureOrPresent LocalDate operationDate,
        @NotNull LocalTime presentationTime,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Size(max = 12000) String briefing,
        @NotBlank @Size(max = 500) String reason,
        @NotNull @PositiveOrZero Long version
    ) {}
    public record DeleteOperationRequest(@NotBlank @Size(max = 500) String reason) {}
    public record ParticipationRequest(@NotNull UUID operationTeamId, UUID operationSquadId) {}
    public record OperationTeamResponse(UUID id, String name, String acronym, String color, String description,
                                        int capacity, long participantCount, String status) {}
    public record ParticipantResponse(UUID operatorId, String callsign, String displayName, String teamName,
                                      String teamAcronym, String status,
                                      UUID operationTeamId, String operationTeamName) {}
    public record OperationRosterResponse(List<OperationTeamResponse> teams, List<ParticipantResponse> participants,
                                          UUID currentUserTeamId) {}

    public record OperationSummary(
        UUID id, String name, String description, UUID fieldId, String fieldName, UUID mapId, String mapName,
        String city, String stateCode, LocalDate operationDate, LocalTime presentationTime, LocalTime startTime,
        LocalTime endTime, String modality, String status, String gameSize, Integer participantLimit, long participantCount,
        BigDecimal registrationPrice, String participantStatus, boolean managedByCurrentUser,
        boolean hasCover, long coverVersion, String briefing, long version
    ) {}

    public record OperationResponse(
        UUID id, String name, String description, UUID organizerId, String organizerCallsign,
        UUID fieldId, String fieldName, UUID mapId, String mapName, String city, String stateCode,
        LocalDate operationDate, LocalTime presentationTime, LocalTime startTime, LocalTime endTime,
        String modality, String customModality, String rules, String briefing, String gameSize, Integer participantLimit, Integer teamLimit,
        BigDecimal registrationPrice, String paymentMethods, int minimumAge, String requiredEquipment,
        Integer fpsLimit, String entryMode, boolean approvalRequired, boolean waitingListEnabled,
        String status, long participantCount, String participantStatus, boolean managedByCurrentUser, long version,
        boolean hasCover, long coverVersion
    ) {}

    public record OperationListResponse(List<OperationSummary> items) {}
    public record MessageResponse(String message) {}
}
