package br.com.operadorzero.team;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TeamDtos {
    private TeamDtos() {}

    public record CreateTeamRequest(
        @NotBlank @Size(min = 3, max = 80) String name,
        @NotBlank @Pattern(regexp = "(?=.*[A-Za-z0-9])[A-Za-z0-9.-]{2,12}",
            message = "use de 2 a 12 caracteres: letras, números, pontos ou hífens") String acronym,
        @NotBlank @Size(max = 80) String city,
        @NotBlank @Size(min = 2, max = 2) String stateCode,
        @NotBlank @Size(max = 80) String gameStyle,
        boolean ownsField,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 24) String recruitmentStatus
    ) {}

    public record UpdateTeamRequest(
        @NotBlank @Size(min = 3, max = 80) String name,
        @NotBlank @Pattern(regexp = "(?=.*[A-Za-z0-9])[A-Za-z0-9.-]{2,12}",
            message = "use de 2 a 12 caracteres: letras, números, pontos ou hífens") String acronym,
        @NotBlank @Size(max = 80) String city,
        @NotBlank @Size(min = 2, max = 2) String stateCode,
        @NotBlank @Size(max = 80) String gameStyle,
        boolean ownsField,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 24) String recruitmentStatus,
        @NotNull @PositiveOrZero Long version
    ) {}

    public record InviteRequest(
        @NotNull UUID operatorId,
        @Size(max = 500) String message,
        @NotBlank @Size(max = 24) String proposedRole
    ) {}

    public record TransferCaptaincyRequest(@NotNull UUID operatorId, @Size(max = 500) String reason) {}

    public record TeamWorkspaceResponse(TeamResponse team, List<InvitationResponse> receivedInvitations) {}

    public record TeamResponse(
        UUID id,
        String name,
        String acronym,
        String city,
        String stateCode,
        String gameStyle,
        boolean ownsField,
        String description,
        String recruitmentStatus,
        String currentUserRole,
        List<MemberResponse> members,
        boolean hasLogo,
        long version
    ) {}

    public record MemberResponse(
        UUID operatorId,
        String username,
        String callsign,
        String displayName,
        String role,
        Instant joinedAt
    ) {}

    public record InvitationResponse(
        UUID id,
        UUID teamId,
        String teamName,
        String teamAcronym,
        String inviterCallsign,
        String proposedRole,
        String message,
        Instant createdAt,
        Instant expiresAt
    ) {}

    public record MessageResponse(String message) {}
}

