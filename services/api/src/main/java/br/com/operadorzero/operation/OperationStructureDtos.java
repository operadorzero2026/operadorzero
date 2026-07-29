package br.com.operadorzero.operation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OperationStructureDtos {
    private OperationStructureDtos() {}

    public record SaveTeamRequest(
        @NotBlank @Size(max=80) String name, @Size(max=12) String acronym,
        @NotBlank @Pattern(regexp="^#[0-9A-Fa-f]{6}$") String color,
        @Size(max=1000) String description, @NotNull @Min(1) @Max(100000) Integer capacity,
        @Min(1) Integer sortOrder, boolean entriesOpen) {}
    public record SaveSquadRequest(
        @NotBlank @Size(max=80) String name, @Size(max=12) String acronym,
        @Size(max=1000) String description, @NotNull @Min(1) @Max(10000) Integer capacity,
        @Min(1) Integer sortOrder, boolean entriesOpen) {}
    public record MoveParticipantRequest(@NotNull UUID operatorId, @NotNull UUID teamId, UUID squadId) {}
    public record UpdateStructureSettingsRequest(@NotBlank @Size(max=8) String gameSize,
        @Min(1) @Max(100000) Integer participantLimit, boolean commandRolesEnabled,
        boolean allowRoleAccumulation, boolean generalChatPublicRead) {}
    public record AssignRoleRequest(@NotNull UUID operatorId, @NotBlank @Size(max=32) String role,
                                    UUID teamId, UUID squadId) {}
    public record RoleAssignmentResponse(UUID id, UUID operatorId, String callsign, String role, UUID teamId, UUID squadId) {}
    public record SquadResponse(UUID id, UUID teamId, String name, String acronym, String description, int capacity,
                                long participantCount, int sortOrder, String status, UUID commanderId,
                                String commanderCallsign, UUID radioId, String radioCallsign) {}
    public record TeamStructureResponse(UUID id, String name, String acronym, String color, String description,
                                        int capacity, long participantCount, int sortOrder, String status,
                                        UUID commanderId, String commanderCallsign, UUID radioId,
                                        String radioCallsign, List<SquadResponse> squads) {}
    public record StructureResponse(UUID operationId, String gameSize, Integer participantLimit, long participantCount,
                                    boolean commandRolesEnabled, boolean allowRoleAccumulation,
                                    boolean managedByCurrentUser, List<TeamStructureResponse> teams,
                                    List<RoleAssignmentResponse> roles) {}

    public record SendMessageRequest(@NotBlank @Size(max=2000) String body, UUID parentMessageId,
                                     @NotNull UUID idempotencyKey, boolean official) {}
    public record ReportMessageRequest(@NotBlank @Size(max=500) String reason) {}
    public record ModerateMessageRequest(@NotBlank @Size(max=12) String action, @Size(max=500) String reason) {}
    public record ChannelStateRequest(boolean locked) {}
    public record ChatAuthor(UUID id, String callsign, String displayName, String avatarUrl) {}
    public record ChatMessageResponse(UUID id, UUID channelId, ChatAuthor author, UUID parentMessageId,
                                      String body, String status, boolean official, String roleLabel,
                                      Instant createdAt, Instant updatedAt, boolean editableByCurrentUser) {}
    public record ChatPageResponse(UUID channelId, String channelType, boolean locked,
                                   List<ChatMessageResponse> items, boolean hasMore) {}
}
