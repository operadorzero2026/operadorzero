package br.com.operadorzero.operation;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operation.OperationDtos.MessageResponse;
import br.com.operadorzero.operation.OperationDtos.OperationFilter;
import br.com.operadorzero.operation.OperationDtos.OperationListResponse;
import br.com.operadorzero.operation.OperationDtos.OperationResponse;
import br.com.operadorzero.operation.OperationDtos.SaveOperationRequest;
import br.com.operadorzero.operation.OperationDtos.UpdateStatusRequest;
import br.com.operadorzero.operation.OperationRepository.FieldRef;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.web.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationService {
    private static final Set<String> MODALITIES = Set.of("ELIMINATION", "CONQUEST", "CAPTURE_THE_FLAG", "BASE_DEFENSE",
        "ATTACK_DEFENSE", "ESCORT", "RESCUE", "DOMINATION", "OBJECTIVES", "CHAMPIONSHIP", "THEMED", "CUSTOM");
    private static final Set<String> ENTRY_MODES = Set.of("INDIVIDUAL", "TEAM", "BOTH", "INVITATION");
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "REGISTRATION_OPEN", "FULL",
        "CONFIRMATION_PENDING", "IN_PROGRESS", "FINISHED", "CANCELLED");
    private final OperationRepository repository;
    private final AuditEventRepository audit;
    private final Clock clock;

    @Autowired
    public OperationService(OperationRepository repository, AuditEventRepository audit) {
        this(repository, audit, Clock.systemUTC());
    }
    OperationService(OperationRepository repository, AuditEventRepository audit, Clock clock) {
        this.repository = repository; this.audit = audit; this.clock = clock;
    }

    public OperationListResponse search(AuthenticatedUser user, OperationFilter filter) {
        return new OperationListResponse(repository.search(user.internalId(), filter));
    }

    public OperationResponse detail(AuthenticatedUser user, UUID operationId) {
        return repository.find(operationId, user.internalId()).orElseThrow(() -> BusinessException.notFound("Operação não encontrada."));
    }

    @Transactional
    public OperationResponse create(AuthenticatedUser user, SaveOperationRequest request) {
        validateTimes(request);
        String modality = allowed(request.modality(), MODALITIES, "INVALID_MODALITY", "Selecione uma modalidade válida.");
        if ("CUSTOM".equals(modality) && (request.customModality() == null || request.customModality().isBlank())) {
            throw BusinessException.badRequest("CUSTOM_MODALITY_REQUIRED", "Informe a modalidade personalizada.");
        }
        String entryMode = allowed(request.entryMode(), ENTRY_MODES, "INVALID_ENTRY_MODE", "Selecione uma forma de entrada válida.");
        FieldRef field = repository.activeField(request.fieldId()).orElseThrow(() -> BusinessException.notFound("Campo não encontrado."));
        Long mapId = null;
        if (request.mapId() != null) mapId = repository.activeMap(request.mapId(), field.id())
            .orElseThrow(() -> BusinessException.badRequest("MAP_FIELD_MISMATCH", "O mapa não pertence ao campo selecionado."));
        Instant now = clock.instant();
        UUID id = repository.create(user.internalId(), field, mapId, request, modality, entryMode, now);
        audit.record(user.internalId(), "OPERATION_CREATED", "OPERATION", id, null, now);
        return detail(user, id);
    }

    @Transactional
    public OperationResponse updateStatus(AuthenticatedUser user, UUID id, UpdateStatusRequest request) {
        String status = allowed(request.status(), STATUSES, "INVALID_OPERATION_STATUS", "Selecione um status válido.");
        Instant now = clock.instant();
        if (repository.updateStatus(id, user.internalId(), status, now) != 1) throw BusinessException.notFound("Operação não encontrada.");
        audit.record(user.internalId(), "OPERATION_STATUS_CHANGED", "OPERATION", id, nullable(request.reason()), now);
        return detail(user, id);
    }

    @Transactional
    public OperationResponse publish(AuthenticatedUser user, UUID id) {
        Instant now = clock.instant();
        if (repository.publish(id, user.internalId(), now) != 1) {
            throw BusinessException.conflict("OPERATION_NOT_PUBLISHABLE", "A operação não foi encontrada ou não está mais como rascunho.");
        }
        audit.record(user.internalId(), "OPERATION_PUBLISHED", "OPERATION", id, null, now);
        return detail(user, id);
    }

    @Transactional
    public MessageResponse requestParticipation(AuthenticatedUser user, UUID id) {
        Instant now = clock.instant();
        if (repository.requestParticipation(id, user.internalId(), now) != 1) {
            throw BusinessException.conflict("PARTICIPATION_UNAVAILABLE", "A participação já foi solicitada ou as inscrições não estão abertas.");
        }
        audit.record(user.internalId(), "OPERATION_PARTICIPATION_REQUESTED", "OPERATION", id, null, now);
        return new MessageResponse("Solicitação registrada.");
    }

    @Transactional
    public MessageResponse cancelParticipation(AuthenticatedUser user, UUID id) {
        Instant now = clock.instant();
        if (repository.cancelParticipation(id, user.internalId(), now) != 1) {
            throw BusinessException.conflict("PARTICIPATION_NOT_CANCELLABLE", "Esta participação não pode ser cancelada.");
        }
        audit.record(user.internalId(), "OPERATION_PARTICIPATION_CANCELLED", "OPERATION", id, null, now);
        return new MessageResponse("Participação cancelada.");
    }

    private void validateTimes(SaveOperationRequest request) {
        if (!request.presentationTime().isBefore(request.startTime()) || !request.startTime().isBefore(request.endTime())) {
            throw BusinessException.badRequest("INVALID_OPERATION_TIME", "A apresentação deve ocorrer antes do início, e o término após o início.");
        }
    }
    private String allowed(String value, Set<String> values, String code, String message) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!values.contains(normalized)) throw BusinessException.badRequest(code, message);
        return normalized;
    }
    private String nullable(String value) { if (value == null) return null; String v = value.trim(); return v.isEmpty() ? null : v; }
}
