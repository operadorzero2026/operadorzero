package br.com.operadorzero.operation;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operation.OperationDocumentRepository.Access;
import br.com.operadorzero.operation.OperationDocumentRepository.Doc;
import br.com.operadorzero.shared.audit.AuditEventRepository;
import br.com.operadorzero.shared.web.BusinessException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Missoes e documentos (PDF). Jogador ve so com inscricao aprovada; em operacao paga, so com o pagamento confirmado;
 * e so ate o horario de inicio. O organizador (e administradores da operacao) sempre ve. Tudo conferido a cada leitura.
 */
@Service
public class OperationDocumentService {
    static final int MAX_DOCUMENTS = 20;
    /** "Documento atualizado" aparece por uma semana depois da troca do arquivo. */
    static final Duration RECENT = Duration.ofDays(7);
    static final Set<String> CONFIRMED = Set.of("APPROVED", "CONFIRMED", "CHECKED_IN");

    public record Item(UUID id, String title, String fileName, long sizeBytes, int version, Instant updatedAt, String updatedBy, boolean recentlyUpdated) {}

    /** access: MANAGER, ALLOWED, NOT_REGISTERED, PAYMENT_PENDING, STARTED ou CANCELLED. Sem acesso, so a quantidade. */
    public record Listing(String access, boolean manager, Instant availableUntil, long count, List<Item> items) {}

    private final OperationDocumentRepository repository;
    private final OperationDocumentStorage storage;
    private final OperationRepository operations;
    private final AuditEventRepository audit;
    private final Clock clock;

    @Autowired
    public OperationDocumentService(OperationDocumentRepository repository, OperationDocumentStorage storage, OperationRepository operations,
                                    AuditEventRepository audit) {
        this(repository, storage, operations, audit, Clock.systemUTC());
    }

    OperationDocumentService(OperationDocumentRepository repository, OperationDocumentStorage storage, OperationRepository operations,
                             AuditEventRepository audit, Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.operations = operations;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Listing list(AuthenticatedUser user, UUID operationId) {
        Access access = access(user, operationId);
        boolean manager = manager(access, user);
        String state = state(access, manager, clock.instant());
        boolean visible = manager || "ALLOWED".equals(state);
        Instant now = clock.instant();
        List<Item> items = visible ? repository.list(access.operationId()).stream().map(d -> new Item(d.publicId(), d.title(), d.fileName(),
            d.sizeBytes(), d.version(), d.updatedAt(), d.updatedBy(), d.version() > 1 && d.updatedAt().isAfter(now.minus(RECENT)))).toList() : List.of();
        return new Listing(state, manager, access.startsAt(), visible ? items.size() : repository.count(access.operationId()), items);
    }

    /** Caminho do arquivo para entregar, depois de conferir o acesso desta pessoa neste momento. */
    @Transactional(readOnly = true)
    public File file(AuthenticatedUser user, UUID operationId, UUID documentId) {
        Access access = access(user, operationId);
        boolean manager = manager(access, user);
        String state = state(access, manager, clock.instant());
        if (!manager && !"ALLOWED".equals(state)) throw denied(state);
        Doc doc = repository.one(access.operationId(), documentId).orElseThrow(() -> BusinessException.notFound("Documento não encontrado."));
        return new File(storage.path(doc.storageKey()), doc.fileName());
    }

    @Transactional
    public Item upload(AuthenticatedUser user, UUID operationId, String title, String fileName, InputStream body, long length) {
        Access access = managed(user, operationId);
        if (repository.count(access.operationId()) >= MAX_DOCUMENTS) {
            throw BusinessException.conflict("DOCUMENT_LIMIT", "Use no máximo " + MAX_DOCUMENTS + " documentos por operação.");
        }
        String name = cleanName(fileName);
        String cleanTitle = title == null || title.isBlank() ? name.replaceFirst("(?i)\\.pdf$", "") : title.strip();
        if (cleanTitle.length() > 120) cleanTitle = cleanTitle.substring(0, 120);
        var stored = storage.save(body, length);
        Instant now = clock.instant();
        UUID id = repository.insert(access.operationId(), cleanTitle, name, stored.size(), stored.key(), user.internalId(), now);
        audit.record(user.internalId(), "OPERATION_DOCUMENT_UPLOADED", "OPERATION", operationId, cleanTitle, now);
        return new Item(id, cleanTitle, name, stored.size(), 1, now, null, false);
    }

    /** Substitui o PDF mantendo o documento; quem tem acesso ve "Documento atualizado". O arquivo antigo e apagado. */
    @Transactional
    public Item replace(AuthenticatedUser user, UUID operationId, UUID documentId, String fileName, InputStream body, long length) {
        Access access = managed(user, operationId);
        Doc doc = repository.one(access.operationId(), documentId).orElseThrow(() -> BusinessException.notFound("Documento não encontrado."));
        String name = cleanName(fileName);
        var stored = storage.save(body, length);
        Instant now = clock.instant();
        repository.replace(doc.id(), doc.publicId(), name, stored.size(), stored.key(), user.internalId(), now);
        storage.delete(doc.storageKey());
        audit.record(user.internalId(), "OPERATION_DOCUMENT_REPLACED", "OPERATION", operationId, doc.title(), now);
        return new Item(doc.publicId(), doc.title(), name, stored.size(), doc.version() + 1, now, null, true);
    }

    @Transactional
    public void delete(AuthenticatedUser user, UUID operationId, UUID documentId) {
        Access access = managed(user, operationId);
        Doc doc = repository.one(access.operationId(), documentId).orElseThrow(() -> BusinessException.notFound("Documento não encontrado."));
        Instant now = clock.instant();
        repository.delete(doc.id(), doc.publicId(), user.internalId(), now);
        storage.delete(doc.storageKey());
        audit.record(user.internalId(), "OPERATION_DOCUMENT_REMOVED", "OPERATION", operationId, doc.title(), now);
    }

    static String state(Access access, boolean manager, Instant now) {
        if (manager) return "MANAGER";
        if ("CANCELLED".equals(access.status())) return "CANCELLED";
        if (!access.startsAt().isAfter(now) || Set.of("IN_PROGRESS", "FINISHED").contains(access.status())) return "STARTED";
        if (access.participation() == null || !CONFIRMED.contains(access.participation())) return "NOT_REGISTERED";
        if (access.paid() && !access.paymentConfirmed()) return "PAYMENT_PENDING";
        return "ALLOWED";
    }

    private static BusinessException denied(String state) {
        return BusinessException.forbidden(switch (state) {
            case "STARTED" -> "Os documentos ficam disponíveis só até o início do jogo.";
            case "PAYMENT_PENDING" -> "Os documentos são liberados após a confirmação do pagamento.";
            case "CANCELLED" -> "Esta operação foi cancelada.";
            default -> "Os documentos são exclusivos para jogadores inscritos.";
        });
    }

    private Access access(AuthenticatedUser user, UUID operationId) {
        return repository.access(operationId, user.internalId()).orElseThrow(() -> BusinessException.notFound("Operação não encontrada."));
    }

    private Access managed(AuthenticatedUser user, UUID operationId) {
        Access access = access(user, operationId);
        if (!manager(access, user)) throw BusinessException.forbidden("Só o organizador pode enviar documentos.");
        return access;
    }

    private boolean manager(Access access, AuthenticatedUser user) {
        return access.organizerUserId() == user.internalId() || operations.isOperationAdmin(access.operationId(), user.internalId());
    }

    /** Nome exibido do arquivo: so caracteres seguros, sempre terminando em .pdf. */
    static String cleanName(String raw) {
        String name = raw == null ? "" : raw.replaceAll("[\\\\/]", " ").replaceAll("[^\\p{L}\\p{N} ._()-]", "").strip();
        if (name.isEmpty()) name = "documento";
        if (!name.toLowerCase().endsWith(".pdf")) name = name + ".pdf";
        return name.length() > 160 ? name.substring(name.length() - 160) : name;
    }

    public record File(Path path, String fileName) {}
}
