package br.com.operadorzero.performance;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PerformanceDtos {
    private PerformanceDtos() {}
    public record SavePerformanceRequest(
        @NotNull @Min(0) @Max(10000) Integer eliminations,
        @NotNull @Min(0) @Max(10000) Integer deaths,
        @NotNull @Min(0) @Max(1000) Integer objectivesCompleted,
        @NotNull @Min(0) @Max(1000) Integer roundWins,
        @NotBlank @Size(max=12) String result,
        @Size(max=48) String positionUsed,
        UUID representedTeamId,
        @Size(max=1000) String notes,
        @Size(max=160) String highlightReceived,
        boolean abandoned,
        @NotBlank @Size(max=12) String participationScope,
        @NotNull @PositiveOrZero BigDecimal penaltyPoints,
        @NotNull @PositiveOrZero Long version
    ) {}
    public record ReviewPerformanceRequest(
        @NotBlank @Size(max=32) String action,
        Integer eliminations, Integer deaths, Integer objectivesCompleted, Integer roundWins,
        @Size(max=12) String result, @PositiveOrZero BigDecimal penaltyPoints,
        @Size(max=1000) String notes, @NotNull @PositiveOrZero Long version
    ) {}
    public record ContestRequest(@NotBlank @Size(min=10,max=1000) String reason) {}
    public record PerformanceResponse(UUID id, UUID operationId, String operationName, LocalDate operationDate,
        UUID operatorId, String operatorCallsign, UUID representedTeamId, String representedTeamName,
        int eliminations, int deaths, int objectivesCompleted, int roundWins, String result, String positionUsed,
        String notes, String highlightReceived, BigDecimal penaltyPoints, boolean abandoned, String participationScope,
        String status, boolean organizerConfirmed, boolean teamConfirmed, String organizerNotes,
        boolean currentUserRecord, boolean canReviewAsOrganizer, boolean canReviewAsTeam, boolean canContest,
        Instant createdAt, Instant updatedAt, long version) {}
    public record PerformanceListResponse(List<PerformanceResponse> items) {}
    public record MessageResponse(String message) {}
}
