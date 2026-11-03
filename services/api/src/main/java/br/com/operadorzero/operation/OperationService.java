package br.com.operadorzero.operation;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.notification.Notifier;
import br.com.operadorzero.operation.OperationDtos.MessageResponse;
import br.com.operadorzero.operation.OperationDtos.OperationFilter;
import br.com.operadorzero.operation.OperationDtos.OperationListResponse;
import br.com.operadorzero.operation.OperationDtos.OperationResponse;
import br.com.operadorzero.operation.OperationDtos.SaveOperationRequest;
import br.com.operadorzero.operation.OperationDtos.UpdateStatusRequest;
import br.com.operadorzero.operation.OperationDtos.UpdateOperationRequest;
import br.com.operadorzero.operation.OperationDtos.DeleteOperationRequest;
import br.com.operadorzero.operation.OperationDtos.ParticipantDecisionRequest;
import br.com.operadorzero.operation.OperationDtos.OperationRosterResponse;
import br.com.operadorzero.operation.OperationRepository.FieldRef;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.web.BusinessException;
import br.com.operadorzero.shared.image.SafeRasterImageProcessor;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OperationService {
    private static final Set<String> MODALITIES = Set.of("ELIMINATION", "CONQUEST", "CAPTURE_THE_FLAG", "BASE_DEFENSE",
        "ATTACK_DEFENSE", "ESCORT", "RESCUE", "DOMINATION", "OBJECTIVES", "CHAMPIONSHIP", "THEMED", "CUSTOM");
    private static final Set<String> ENTRY_MODES = Set.of("INDIVIDUAL", "TEAM", "BOTH", "INVITATION");
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "REGISTRATION_OPEN", "FULL",
        "CONFIRMATION_PENDING", "IN_PROGRESS", "FINISHED", "CANCELLED");
    private static final Set<String> GAME_SIZES = Set.of("SMALL", "MEDIUM", "LARGE");
    private static final Set<String> REGISTRATION_MODELS = Set.of("SINGLE_LIST", "TEAMS");
    private static final Set<String> BEFORE_START = Set.of("PUBLISHED", "REGISTRATION_OPEN", "FULL", "CONFIRMATION_PENDING");
    // Status de destino -> status de origem permitidos. Rascunho so sai por /publish; finalizada e cancelada sao finais.
    private static final Map<String, Set<String>> STATUS_TRANSITIONS = Map.of(
        "CONFIRMATION_PENDING", Set.of("PUBLISHED", "REGISTRATION_OPEN", "FULL"),
        "REGISTRATION_OPEN", Set.of("CONFIRMATION_PENDING", "FULL"),
        "IN_PROGRESS", BEFORE_START,
        "FINISHED", Set.of("IN_PROGRESS"),
        "CANCELLED", Set.of("PUBLISHED", "REGISTRATION_OPEN", "FULL", "CONFIRMATION_PENDING", "IN_PROGRESS"));

    record Decision(Set<String> from, String to, Set<String> operationStatuses, String successMessage, String unavailableMessage) {}

    private static final Map<String, Decision> DECISIONS = Map.of(
        "APPROVE", new Decision(Set.of("REQUESTED", "WAITING_LIST"), "APPROVED", BEFORE_START,
            "Inscrição aprovada.", "Só é possível aprovar inscrições pendentes ou da lista de espera, antes do início e com vaga disponível."),
        "REJECT", new Decision(Set.of("REQUESTED", "WAITING_LIST", "APPROVED", "CONFIRMED"), "REJECTED", BEFORE_START,
            "Inscrição recusada.", "Esta inscrição não pode mais ser recusada."),
        "CONFIRM", new Decision(Set.of("APPROVED"), "CONFIRMED", BEFORE_START,
            "Presença confirmada.", "Só inscrições aprovadas, antes do início, podem ter a presença confirmada."),
        "CHECK_IN", new Decision(Set.of("APPROVED", "CONFIRMED"), "CHECKED_IN",
            Set.of("REGISTRATION_OPEN", "FULL", "CONFIRMATION_PENDING", "IN_PROGRESS"),
            "Check-in registrado.", "O check-in exige inscrição aprovada e operação aberta ou em andamento."),
        "UNDO_CHECK_IN", new Decision(Set.of("CHECKED_IN"), "CONFIRMED",
            Set.of("REGISTRATION_OPEN", "FULL", "CONFIRMATION_PENDING", "IN_PROGRESS"),
            "Check-in desfeito.", "Este check-in não pode ser desfeito."));
    private final OperationRepository repository;
    private final AuditEventRepository audit;
    private final Notifier notifier;
    private final Clock clock;

    @Autowired
    public OperationService(OperationRepository repository, AuditEventRepository audit, Notifier notifier) {
        this(repository, audit, notifier, Clock.systemUTC());
    }
    OperationService(OperationRepository repository, AuditEventRepository audit, Clock clock) {
        this(repository, audit, Notifier.none(), clock);
    }
    OperationService(OperationRepository repository, AuditEventRepository audit, Notifier notifier, Clock clock) {
        this.repository = repository; this.audit = audit; this.notifier = notifier; this.clock = clock;
    }

    public OperationListResponse search(AuthenticatedUser user, OperationFilter filter) {
        return new OperationListResponse(repository.search(user.internalId(), filter));
    }

    public OperationResponse detail(AuthenticatedUser user, UUID operationId) {
        return repository.find(operationId, user.internalId()).orElseThrow(() -> BusinessException.notFound("Operação não encontrada."));
    }

    @Transactional(readOnly = true)
    public OperationRosterResponse roster(AuthenticatedUser user, UUID operationId) {
        detail(user, operationId);
        return repository.roster(operationId, user.internalId());
    }

    @Transactional
    public OperationResponse create(AuthenticatedUser user, SaveOperationRequest request) {
        validateTimes(request);
        String gameSize = validateSize(request);
        String modality = allowed(request.modality(), MODALITIES, "INVALID_MODALITY", "Selecione uma modalidade válida.");
        if ("CUSTOM".equals(modality) && (request.customModality() == null || request.customModality().isBlank())) {
            throw BusinessException.badRequest("CUSTOM_MODALITY_REQUIRED", "Informe a modalidade personalizada.");
        }
        String entryMode = allowed(request.entryMode() == null ? "INDIVIDUAL" : request.entryMode(), ENTRY_MODES, "INVALID_ENTRY_MODE", "Selecione uma forma de entrada válida.");
        // Lista unica e o padrao; por equipes, o organizador cria quantas equipes quiser.
        String model = allowed(request.registrationModel() == null ? "SINGLE_LIST" : request.registrationModel(), REGISTRATION_MODELS,
            "INVALID_REGISTRATION_MODEL", "Escolha lista única ou inscrição por equipes.");
        FieldRef field = repository.activeField(request.fieldId()).orElseThrow(() -> BusinessException.notFound("Campo não encontrado."));
        Long mapId = null;
        if (request.mapId() != null) mapId = repository.activeMap(request.mapId(), field.id())
            .orElseThrow(() -> BusinessException.badRequest("MAP_FIELD_MISMATCH", "O mapa não pertence ao campo selecionado."));
        Instant now = clock.instant();
        UUID id = repository.create(user.internalId(), field, mapId, request, modality, entryMode, gameSize, model, now);
        if ("TEAMS".equals(model)) repository.createTeams(id, user.internalId());
        audit.record(user.internalId(), "OPERATION_CREATED", "OPERATION", id, null, now);
        return detail(user, id);
    }

    @Transactional
    public OperationResponse updateStatus(AuthenticatedUser user, UUID id, UpdateStatusRequest request) {
        String status = allowed(request.status(), STATUSES, "INVALID_OPERATION_STATUS", "Selecione um status válido.");
        Set<String> from = STATUS_TRANSITIONS.get(status);
        if (from == null) {
            throw BusinessException.badRequest("INVALID_OPERATION_STATUS", "Este status não pode ser definido manualmente.");
        }
        if ("CANCELLED".equals(status) && nullable(request.reason()) == null) {
            throw BusinessException.badRequest("CANCELLATION_REASON_REQUIRED", "Informe o motivo do cancelamento.");
        }
        Instant now = clock.instant();
        if (repository.updateStatus(id, user.internalId(), status, from, now) != 1) {
            if (repository.isOwned(id, user.internalId())) {
                throw BusinessException.conflict("INVALID_STATUS_TRANSITION", "A operação não pode passar para este status a partir do status atual.");
            }
            throw BusinessException.notFound("Operação não encontrada.");
        }
        audit.record(user.internalId(), "OPERATION_STATUS_CHANGED", "OPERATION", id, status + (nullable(request.reason()) == null ? "" : ": " + request.reason().trim()), now);
        if ("REGISTRATION_OPEN".equals(status)) {
            repository.lockOwnedOperation(id, user.internalId()).ifPresent(operationId -> repository.promoteWaitingList(operationId, now));
        }
        OperationResponse updated = detail(user, id);
        if ("CANCELLED".equals(status)) {
            notifier.toOperationParticipants(id, "OPERATION_CANCELLED", "Operação cancelada: " + updated.name(),
                "Motivo: " + request.reason().trim(), "Operações");
        } else if ("FINISHED".equals(status)) {
            notifier.toOperationParticipants(id, "OPERATION_FINISHED", "Operação finalizada: " + updated.name(),
                "Registre seu desempenho para entrar no ranking.", "Desempenho");
        } else if ("IN_PROGRESS".equals(status)) {
            notifier.toOperationParticipants(id, "OPERATION_STARTED", "Operação iniciada: " + updated.name(), null, "Operações");
        }
        return updated;
    }

    /** Organizador aprova, recusa, confirma presenca ou faz check-in de um participante. */
    @Transactional
    public MessageResponse decideParticipant(AuthenticatedUser user, UUID id, UUID operatorId, ParticipantDecisionRequest request) {
        String action = allowed(request.action(), DECISIONS.keySet(), "INVALID_PARTICIPANT_ACTION", "Selecione uma ação válida.");
        Decision decision = DECISIONS.get(action);
        Instant now = clock.instant();
        long operationId = repository.lockOwnedOperation(id, user.internalId())
            .orElseThrow(() -> BusinessException.notFound("Operação não encontrada."));
        if (repository.transitionParticipant(operationId, operatorId, decision.from(), decision.to(), decision.operationStatuses(), now) != 1) {
            throw BusinessException.conflict("PARTICIPANT_ACTION_UNAVAILABLE", decision.unavailableMessage());
        }
        if ("REJECTED".equals(decision.to())) {
            repository.promoteWaitingList(operationId, now);
        }
        audit.record(user.internalId(), "OPERATION_PARTICIPANT_" + action, "OPERATION", id,
            operatorId + (nullable(request.reason()) == null ? "" : ": " + request.reason().trim()), now);
        String title = switch (action) {
            case "APPROVE" -> "Inscrição aprovada";
            case "REJECT" -> "Inscrição recusada";
            case "CONFIRM" -> "Presença confirmada pelo organizador";
            default -> null;
        };
        if (title != null) {
            String name = repository.find(id, user.internalId()).map(OperationResponse::name).orElse("operação");
            notifier.toOperator(operatorId, "OPERATION_PARTICIPATION", title + ": " + name,
                "REJECT".equals(action) ? nullable(request.reason()) : null, "Operações");
        }
        return new MessageResponse(decision.successMessage());
    }

    @Transactional
    public MessageResponse confirmOwnPresence(AuthenticatedUser user, UUID id) {
        Instant now = clock.instant();
        if (repository.confirmOwnPresence(id, user.internalId(), now) != 1) {
            throw BusinessException.conflict("PRESENCE_NOT_CONFIRMABLE", "Somente inscrições aprovadas, antes do início da operação, podem confirmar presença.");
        }
        audit.record(user.internalId(), "OPERATION_PRESENCE_CONFIRMED", "OPERATION", id, null, now);
        return new MessageResponse("Presença confirmada.");
    }

    @Transactional
    public OperationResponse update(AuthenticatedUser user, UUID id, UpdateOperationRequest request) {
        validateTimes(request.presentationTime(), request.startTime(), request.endTime());
        Instant now = clock.instant();
        int changed = repository.update(id, user.internalId(), request, nullable(request.briefing()), now);
        if (changed == 0) {
            if (repository.isOwned(id, user.internalId())) {
                throw BusinessException.conflict("OPERATION_UPDATE_CONFLICT", "A operação foi alterada, iniciada ou finalizada. Atualize a tela antes de tentar novamente.");
            }
            throw BusinessException.notFound("Operação não encontrada.");
        }
        audit.record(user.internalId(), "OPERATION_UPDATED", "OPERATION", id, nullable(request.reason()), now);
        return detail(user, id);
    }

    @Transactional
    public MessageResponse delete(AuthenticatedUser user, UUID id, DeleteOperationRequest request) {
        Instant now = clock.instant();
        int changed = repository.softDelete(id, user.internalId(), now);
        if (changed == 0) {
            if (repository.isOwned(id, user.internalId())) {
                throw BusinessException.conflict("OPERATION_NOT_DELETABLE", "Uma operação iniciada ou finalizada não pode ser excluída.");
            }
            throw BusinessException.notFound("Operação não encontrada.");
        }
        audit.record(user.internalId(), "OPERATION_DELETED", "OPERATION", id, nullable(request.reason()), now);
        return new MessageResponse("Operação excluída.");
    }

    @Transactional
    public OperationResponse publish(AuthenticatedUser user, UUID id) {
        Instant now = clock.instant();
        if (!repository.publishStructureReady(id, user.internalId())) {
            throw BusinessException.conflict("OPERATION_STRUCTURE_INCOMPLETE", "Antes de publicar, crie pelo menos duas equipes.");
        }
        if (repository.publish(id, user.internalId(), now) != 1) {
            throw BusinessException.conflict("OPERATION_NOT_PUBLISHABLE", "A operação não foi encontrada ou não está mais como rascunho.");
        }
        audit.record(user.internalId(), "OPERATION_PUBLISHED", "OPERATION", id, null, now);
        return detail(user, id);
    }

    @Transactional
    public OperationResponse saveCover(AuthenticatedUser user, UUID id, MultipartFile file) {
        var image = SafeRasterImageProcessor.process(file);
        Instant now = clock.instant();
        if (repository.saveCover(id, user.internalId(), image.contentType(), image.data(), now) != 1) {
            throw BusinessException.notFound("OperaÃ§Ã£o nÃ£o encontrada.");
        }
        audit.record(user.internalId(), "OPERATION_COVER_UPDATED", "OPERATION", id, null, now);
        return detail(user, id);
    }

    @Transactional(readOnly = true)
    public OperationRepository.CoverRow cover(AuthenticatedUser user, UUID id) {
        return repository.cover(id, user.internalId())
            .orElseThrow(() -> BusinessException.notFound("Imagem de capa nÃ£o encontrada."));
    }
    @Transactional
    public MessageResponse removeCover(AuthenticatedUser user, UUID id) {
        Instant now = clock.instant();
        if (repository.removeCover(id, user.internalId()) != 1) throw BusinessException.notFound("Imagem de capa não encontrada.");
        audit.record(user.internalId(), "OPERATION_COVER_REMOVED", "OPERATION", id, null, now);
        return new MessageResponse("Imagem de capa removida.");
    }

    private void validateTimes(SaveOperationRequest request) {
        validateTimes(request.presentationTime(), request.startTime(), request.endTime());
    }
    private void validateTimes(java.time.LocalTime presentation, java.time.LocalTime start, java.time.LocalTime end) {
        if (!presentation.isBefore(start) || !start.isBefore(end)) {
            throw BusinessException.badRequest("INVALID_OPERATION_TIME", "A apresentação deve ocorrer antes do início, e o término após o início.");
        }
    }
    /** Sem tamanho informado (formulario novo): sem limite de jogadores, estrutura "grande". */
    private String validateSize(SaveOperationRequest request) {
        if (request.gameSize() == null || request.gameSize().isBlank()) return "LARGE";
        String size = allowed(request.gameSize(), GAME_SIZES, "INVALID_GAME_SIZE", "Selecione um tamanho de jogo válido.");
        int limit = request.participantLimit() == null ? Integer.MAX_VALUE : request.participantLimit();
        int teams = request.teamLimit() == null ? 2 : request.teamLimit();
        if ("SMALL".equals(size) && (limit > 50 || teams != 2)) {
            throw BusinessException.badRequest("SMALL_OPERATION_LIMITS", "Jogo pequeno aceita até 50 jogadores e exatamente 2 times.");
        }
        if ("MEDIUM".equals(size) && (limit > 100 || teams < 2 || teams > 4)) {
            throw BusinessException.badRequest("MEDIUM_OPERATION_LIMITS", "Jogo médio aceita até 100 jogadores e entre 2 e 4 times.");
        }
        if ("LARGE".equals(size) && teams < 2) {
            throw BusinessException.badRequest("LARGE_OPERATION_TEAMS", "Jogo grande deve começar com pelo menos 2 times.");
        }
        return size;
    }
    private String allowed(String value, Set<String> values, String code, String message) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!values.contains(normalized)) throw BusinessException.badRequest(code, message);
        return normalized;
    }
    private String nullable(String value) { if (value == null) return null; String v = value.trim(); return v.isEmpty() ? null : v; }
}
