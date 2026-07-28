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
        @NotNull @Positive Integer participantLimit,
        @Positive Integer teamLimit,
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

    public record OperationSummary(
        UUID id, String name, String description, UUID fieldId, String fieldName, UUID mapId, String mapName,
        String city, String stateCode, LocalDate operationDate, LocalTime presentationTime, LocalTime startTime,
        LocalTime endTime, String modality, String status, int participantLimit, long participantCount,
        BigDecimal registrationPrice, String participantStatus
    ) {}

    public record OperationResponse(
        UUID id, String name, String description, UUID organizerId, String organizerCallsign,
        UUID fieldId, String fieldName, UUID mapId, String mapName, String city, String stateCode,
        LocalDate operationDate, LocalTime presentationTime, LocalTime startTime, LocalTime endTime,
        String modality, String customModality, String rules, int participantLimit, Integer teamLimit,
        BigDecimal registrationPrice, String paymentMethods, int minimumAge, String requiredEquipment,
        Integer fpsLimit, String entryMode, boolean approvalRequired, boolean waitingListEnabled,
        String status, long participantCount, String participantStatus, boolean managedByCurrentUser, long version
    ) {}

    public record OperationListResponse(List<OperationSummary> items) {}
    public record MessageResponse(String message) {}
}
