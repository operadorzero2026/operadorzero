package br.com.operadorzero.operator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OperatorDtos {
    private OperatorDtos() {}

    public record ProfileResponse(
        UUID id,
        String email,
        String username,
        String displayName,
        String callsign,
        String bio,
        String city,
        String stateCode,
        String preferredPosition,
        List<String> secondaryPositions,
        String recruitmentStatus,
        Map<String, String> privacy,
        List<EquipmentResponse> equipment,
        boolean hasPhoto,
        long version
    ) {}

    public record UpdateProfileRequest(
        @NotBlank @Size(min = 2, max = 80) String displayName,
        @NotBlank @Size(min = 2, max = 40) String callsign,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,30}") String username,
        @Size(max = 500) String bio,
        @Size(max = 80) String city,
        @Size(max = 2) String stateCode,
        @Size(max = 48) String preferredPosition,
        @Size(max = 5) List<@Size(max = 48) String> secondaryPositions,
        @NotBlank @Size(max = 32) String recruitmentStatus,
        @NotNull @PositiveOrZero Long version
    ) {}

    public record PrivacyRequest(@NotNull @Size(max = 12) Map<@Size(max = 64) String, @Size(max = 32) String> fields) {}

    public record CreateEquipmentRequest(
        @NotBlank @Size(max = 48) String category,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String details,
        @NotBlank @Size(max = 16) String condition,
        @NotBlank @Size(max = 32) String visibility
    ) {}

    public record EquipmentResponse(
        UUID id,
        String category,
        String name,
        String details,
        String condition,
        String visibility,
        Instant createdAt
    ) {}

    public record SearchResponse(List<OperatorSummary> items) {}

    public record OperatorSummary(
        UUID id,
        String username,
        String displayName,
        String callsign,
        String city,
        String stateCode,
        String recruitmentStatus
    ) {}
}

