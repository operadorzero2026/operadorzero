package br.com.operadorzero.operator;

import br.com.operadorzero.identity.AuthRateLimiter;
import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operator.BrazilLocationValidator.Location;
import br.com.operadorzero.operator.OperatorDtos.CreateEquipmentRequest;
import br.com.operadorzero.operator.OperatorDtos.EquipmentResponse;
import br.com.operadorzero.operator.OperatorDtos.OperatorSummary;
import br.com.operadorzero.operator.OperatorDtos.PrivacyRequest;
import br.com.operadorzero.operator.OperatorDtos.ProfileResponse;
import br.com.operadorzero.operator.OperatorDtos.SearchResponse;
import br.com.operadorzero.operator.OperatorDtos.UpdateProfileRequest;
import br.com.operadorzero.operator.OperatorRepository.ProfileRow;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.web.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperatorService {
    private static final Set<String> POSITIONS = Set.of(
        "ASSAULT", "SUPPORT", "MEDIC", "SNIPER", "RECON", "COMMAND", "DEFENSE", "OTHER"
    );
    private static final Set<String> RECRUITMENT = Set.of("LONE_WOLF", "LOOKING_FOR_TEAM", "NOT_LOOKING");
    private static final Set<String> PRIVACY_FIELDS = Set.of("LOCATION", "EQUIPMENT", "TEAM_STATUS");
    private static final Set<String> VISIBILITIES = Set.of("ONLY_ME", "MY_TEAM", "RELATED_ORGANIZERS", "AUTHENTICATED", "PUBLIC");
    private static final Set<String> EQUIPMENT_VISIBILITIES = Set.of("ONLY_ME", "MY_TEAM", "AUTHENTICATED", "PUBLIC");
    private static final Set<String> RESERVED_USERNAMES = Set.of("admin", "api", "support", "suporte", "operadorzero", "root", "moderador");

    private final OperatorRepository repository;
    private final BrazilLocationValidator locations;
    private final AuditEventRepository audit;
    private final AuthRateLimiter rateLimiter;
    private final Clock clock;

    public OperatorService(OperatorRepository repository, BrazilLocationValidator locations,
                           AuditEventRepository audit, AuthRateLimiter rateLimiter) {
        this(repository, locations, audit, rateLimiter, Clock.systemUTC());
    }

    OperatorService(OperatorRepository repository, BrazilLocationValidator locations,
                    AuditEventRepository audit, AuthRateLimiter rateLimiter, Clock clock) {
        this.repository = repository;
        this.locations = locations;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProfileResponse me(AuthenticatedUser user) {
        return response(profile(user.internalId()));
    }

    @Transactional
    public ProfileResponse update(AuthenticatedUser user, UpdateProfileRequest request) {
        ProfileRow current = profile(user.internalId());
        String username = normalizeUsername(request.username());
        if (RESERVED_USERNAMES.contains(username)) {
            throw BusinessException.conflict("USERNAME_UNAVAILABLE", "Escolha outro nome de usuário.");
        }
        boolean usernameChanged = !current.username().equalsIgnoreCase(username);
        if (usernameChanged && repository.usernameExistsForOther(username, user.internalId())) {
            throw BusinessException.conflict("USERNAME_UNAVAILABLE", "Este nome de usuário não está disponível.");
        }
        if (usernameChanged && repository.lastUsernameChange(user.internalId())
            .filter(last -> last.isAfter(clock.instant().minus(Duration.ofDays(30)))).isPresent()) {
            throw BusinessException.conflict("USERNAME_CHANGE_COOLDOWN", "O nome de usuário só pode ser alterado novamente após 30 dias.");
        }

        Location location = locations.validate(request.stateCode(), request.city());
        String primary = normalizePosition(request.preferredPosition(), true);
        List<String> secondary = normalizeSecondary(request.secondaryPositions(), primary);
        String recruitment = upper(request.recruitmentStatus());
        if (!RECRUITMENT.contains(recruitment)) {
            throw BusinessException.badRequest("INVALID_RECRUITMENT_STATUS", "Selecione um status de equipe válido.");
        }
        Instant now = clock.instant();
        int updated = repository.updateProfile(user.internalId(), request.version(), normalize(request.displayName()),
            normalize(request.callsign()), nullable(request.bio()), location.city(), location.stateCode(), primary, recruitment, now);
        if (updated != 1) {
            throw BusinessException.conflict("PROFILE_CHANGED", "Seu perfil foi atualizado em outro acesso. Recarregue e tente novamente.");
        }
        if (usernameChanged) repository.updateUsername(user.internalId(), current.username(), username, now);
        repository.replaceSecondaryPositions(current.profileId(), secondary, now);
        audit.record(user.internalId(), "OPERATOR_PROFILE_UPDATED", "OPERATOR_PROFILE", current.publicId(), null, now);
        return response(profile(user.internalId()));
    }

    @Transactional
    public ProfileResponse updatePrivacy(AuthenticatedUser user, PrivacyRequest request) {
        ProfileRow current = profile(user.internalId());
        request.fields().forEach((field, visibility) -> {
            if (!PRIVACY_FIELDS.contains(upper(field)) || !VISIBILITIES.contains(upper(visibility))) {
                throw BusinessException.badRequest("INVALID_PRIVACY_SETTING", "Revise as opções de privacidade.");
            }
        });
        Map<String, String> normalized = request.fields().entrySet().stream().collect(
            java.util.stream.Collectors.toUnmodifiableMap(entry -> upper(entry.getKey()), entry -> upper(entry.getValue())));
        repository.replacePrivacy(current.profileId(), normalized, clock.instant());
        audit.record(user.internalId(), "OPERATOR_PRIVACY_UPDATED", "OPERATOR_PROFILE", current.publicId(), null, clock.instant());
        return response(profile(user.internalId()));
    }

    @Transactional
    public ProfileResponse addEquipment(AuthenticatedUser user, CreateEquipmentRequest request) {
        ProfileRow current = profile(user.internalId());
        if (repository.equipmentCount(current.profileId()) >= 30) {
            throw BusinessException.conflict("EQUIPMENT_LIMIT_REACHED", "Você atingiu o limite de equipamentos cadastrados.");
        }
        String condition = upper(request.condition());
        String visibility = upper(request.visibility());
        if (!Set.of("NEW", "USED").contains(condition) || !EQUIPMENT_VISIBILITIES.contains(visibility)) {
            throw BusinessException.badRequest("INVALID_EQUIPMENT", "Revise os dados do equipamento.");
        }
        UUID id = repository.createEquipment(current.profileId(), upper(request.category()), normalize(request.name()),
            nullable(request.details()), condition, visibility, clock.instant());
        audit.record(user.internalId(), "OPERATOR_EQUIPMENT_CREATED", "OPERATOR_EQUIPMENT", id, null, clock.instant());
        return response(profile(user.internalId()));
    }

    @Transactional
    public void removeEquipment(AuthenticatedUser user, UUID equipmentId) {
        if (repository.deleteEquipment(user.internalId(), equipmentId) != 1) {
            throw BusinessException.notFound("Equipamento não encontrado.");
        }
        audit.record(user.internalId(), "OPERATOR_EQUIPMENT_REMOVED", "OPERATOR_EQUIPMENT", equipmentId, null, clock.instant());
    }

    @Transactional(readOnly = true)
    public SearchResponse search(AuthenticatedUser user, String query, int limit, HttpServletRequest request) {
        String normalized = normalize(query);
        if (normalized.length() < 2) return new SearchResponse(List.of());
        rateLimiter.check("operator-search", request, normalized, 30, Duration.ofMinutes(1));
        return new SearchResponse(repository.search(user.internalId(), normalized, Math.max(1, Math.min(limit, 20))));
    }

    @Transactional(readOnly = true)
    public OperatorSummary publicProfile(AuthenticatedUser user, String username) {
        return repository.findPublicProfile(user.internalId(), username)
            .orElseThrow(() -> BusinessException.notFound("Operador não encontrado."));
    }

    private ProfileResponse response(ProfileRow row) {
        List<EquipmentResponse> equipment = repository.equipment(row.profileId());
        return new ProfileResponse(row.publicId(), row.email(), row.username(), row.displayName(), row.callsign(), row.bio(),
            row.city(), row.stateCode(), row.preferredPosition(), repository.secondaryPositions(row.profileId()),
            row.recruitmentStatus(), repository.privacy(row.profileId()), equipment, row.version());
    }

    private ProfileRow profile(long userId) {
        return repository.findProfile(userId).orElseThrow(() -> BusinessException.notFound("Perfil não encontrado."));
    }

    private List<String> normalizeSecondary(List<String> values, String primary) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                String normalized = normalizePosition(value, false);
                if (normalized != null && !normalized.equals(primary)) unique.add(normalized);
            }
        }
        if (unique.size() > 5) throw BusinessException.badRequest("INVALID_POSITIONS", "Selecione no máximo cinco posições secundárias.");
        return List.copyOf(unique);
    }

    private String normalizePosition(String value, boolean optional) {
        String normalized = upper(value);
        if (normalized.isEmpty() && optional) return null;
        if (!POSITIONS.contains(normalized)) {
            throw BusinessException.badRequest("INVALID_POSITION", "Selecione uma posição válida.");
        }
        return normalized;
    }

    private String normalizeUsername(String value) {
        return normalize(value).toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String nullable(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? null : normalized;
    }

    private String upper(String value) {
        return normalize(value).toUpperCase(Locale.ROOT);
    }
}

