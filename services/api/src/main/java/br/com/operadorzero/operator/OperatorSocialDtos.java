package br.com.operadorzero.operator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class OperatorSocialDtos {
    private OperatorSocialDtos() {}

    public record ConnectionResponse(UUID id, OperatorDtos.OperatorSummary operator, String status,
                                     boolean receivedByCurrentUser, Instant updatedAt) {}
    public record ConnectionListResponse(List<ConnectionResponse> items) {}
    public record ConnectionStatusResponse(String status, UUID connectionId) {}
    public record ReportRequest(
        @NotBlank @Pattern(regexp = "SPAM|HARASSMENT|IMPERSONATION|FRAUD|ILLEGAL|OTHER") String reason,
        @Size(max = 1000) String details
    ) {}
    public record MessageResponse(String message) {}
    public record ProfileEquipmentResponse(String category, String name) {}
    public record ProfileOperationResponse(UUID id, String name, LocalDate date, String city,
                                           String stateCode, String status, boolean hasCover, long coverVersion) {}
    public record ProfileOverviewResponse(
        OperatorDtos.OperatorSummary operator,
        String bio,
        String preferredPosition,
        String teamRole,
        boolean ownProfile,
        boolean hasPhoto,
        long photoVersion,
        long publicationCount,
        long friendCount,
        long operationCount,
        Integer rankingPosition,
        long achievementCount,
        List<ProfileEquipmentResponse> equipment,
        List<ProfileOperationResponse> recentOperations,
        List<OperatorDtos.OperatorSummary> mutualFriends
    ) {}
}
