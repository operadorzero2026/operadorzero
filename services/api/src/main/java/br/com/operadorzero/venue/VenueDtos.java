package br.com.operadorzero.venue;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class VenueDtos {
    private VenueDtos() {}
    public record SaveFieldRequest(
        @NotBlank @Size(max=100) String name, @Size(max=1000) String description,
        @Size(max=24) String phone, @Email @Size(max=254) String contactEmail,
        @NotBlank @Size(max=160) String addressLine, @Size(max=20) String addressNumber,
        @Size(max=80) String complement, @Size(max=80) String district,
        @NotBlank @Size(max=80) String city, @NotBlank @Size(min=2,max=2) String stateCode,
        @Size(max=9) String postalCode, @Size(max=40) String region,
        @Size(max=4000) String rules, @Size(max=1000) String openingHours,
        @Size(max=1000) String amenities, @Positive Integer maximumCapacity,
        @PositiveOrZero BigDecimal averagePrice, @Size(max=500) String paymentMethods
    ) {}
    public record FieldResponse(UUID id, String name, String description, String phone, String contactEmail,
        String addressLine, String addressNumber, String complement, String district, String city, String stateCode,
        String postalCode, String region, String rules, String openingHours, String amenities, Integer maximumCapacity,
        BigDecimal averagePrice, String paymentMethods, boolean managedByCurrentUser, long version) {}
    public record SaveMapRequest(@NotBlank @Size(max=100) String name, @NotBlank @Size(max=24) String terrainType,
        @Size(max=1000) String description, @Size(max=80) String approximateSize, @Positive Integer capacity,
        @Size(max=1000) String respawnAreas, @Size(max=1000) String bases, @Size(max=1000) String objectives,
        @Size(max=1000) String strategicPoints, @Size(max=1000) String neutralAreas,
        @Size(max=1000) String prohibitedAreas, @Size(max=1000) String routes, @Size(max=1000) String notes,
        @Size(max=2000) String specificRules) {}
    public record MapResponse(UUID id, UUID fieldId, String fieldName, String city, String stateCode, String name,
        String terrainType, String description, String approximateSize, Integer capacity, String respawnAreas,
        String bases, String objectives, String strategicPoints, String neutralAreas, String prohibitedAreas,
        String routes, String notes, String specificRules, boolean managedByCurrentUser, long version) {}
    public record FieldListResponse(List<FieldResponse> items) {}
    public record MapListResponse(List<MapResponse> items) {}
}
