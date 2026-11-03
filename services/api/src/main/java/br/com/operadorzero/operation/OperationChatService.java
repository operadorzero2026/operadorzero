package br.com.operadorzero.operation;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operation.OperationChatRepository.Context;
import br.com.operadorzero.operation.OperationChatRepository.Message;
import br.com.operadorzero.operation.OperationChatRepository.Muted;
import br.com.operadorzero.shared.ratelimit.UserActionLimiter;
import br.com.operadorzero.shared.web.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Chat geral da operacao, so texto. Acesso: organizador (e administradores da operacao) e inscritos aprovados; quem cancelou
 * ou foi removido perde o acesso na hora. Fica aberto ate 7 dias depois do termino; depois disso so leitura.
 * O organizador nao encerra antes do prazo. Silenciado le, mas nao envia.
 */
@Service
public class OperationChatService {
    static final Duration AFTER_END = Duration.ofDays(7);
    static final int MAX_BODY = 1000;
    static final Set<String> MEMBER = Set.of("APPROVED", "CONFIRMED", "CHECKED_IN");
    static final Map<String, Duration> MUTE_OPTIONS = Map.of("1H", Duration.ofHours(1), "6H", Duration.ofHours(6), "24H", Duration.ofHours(24));
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public record Author(UUID operatorId, String callsign, String displayName, String photoUrl) {}
    public record ChatMessage(UUID id, Author author, String body, boolean deleted, Instant createdAt, boolean pinned, boolean own) {}
    public record Me(boolean canSend, String blockedReason, boolean muted, Instant mutedUntil) {}
    public record MutedItem(UUID operatorId, String callsign, Instant until) {}
    public record ChatPage(List<ChatMessage> items, boolean hasMore, List<ChatMessage> pinned, Me me, boolean manager, Instant closesAt,
                           boolean closed, String notice, List<MutedItem> muted) {}

    private final OperationChatRepository repository;
    private final OperationChatHub hub;
    private final UserActionLimiter limiter;
    private final Clock clock;

    @Autowired
    public OperationChatService(OperationChatRepository repository, OperationChatHub hub, UserActionLimiter limiter) {
        this(repository, hub, limiter, Clock.systemUTC());
    }

    OperationChatService(OperationChatRepository repository, OperationChatHub hub, UserActionLimiter limiter, Clock clock) {
        this.repository = repository;
        this.hub = hub;
        this.limiter = limiter;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ChatPage page(AuthenticatedUser user, UUID operationId, Instant before, Instant after, Integer limit) {
        Context ctx = member(user, operationId);
        boolean manager = manager(ctx, user);
        Instant now = clock.instant();
        int size = limit == null ? 50 : Math.max(1, Math.min(100, limit));
        List<Message> rows = repository.messages(ctx.channelId(), before, after, size + 1);
        boolean more = rows.size() > size;
        List<ChatMessage> items = new ArrayList<>(rows.subList(0, Math.min(size, rows.size())).stream().map(m -> map(operationId, m, user)).toList());
        Collections.reverse(items);
        Instant closesAt = closesAt(ctx);
        boolean closed = !now.isBefore(closesAt);
        Optional<Optional<Instant>> mute = manager ? Optional.empty() : repository.activeMute(ctx.operationId(), user.internalId(), now);
        String blocked = closed ? "Chat encerrado. O histórico continua disponível para consulta."
            : mute.isPresent() ? mute.get().map(until -> "Você está silenciado até " + time(until) + ".").orElse("Você foi silenciado pelo organizador.")
            : null;
        return new ChatPage(items, more, repository.pinned(ctx.channelId()).stream().map(m -> map(operationId, m, user)).toList(),
            new Me(blocked == null, blocked, mute.isPresent(), mute.flatMap(m -> m).orElse(null)), manager, closesAt, closed,
            notice(ctx, now), manager ? repository.muted(ctx.operationId(), now).stream().map(this::muted).toList() : List.of());
    }

    @Transactional
    public ChatMessage send(AuthenticatedUser user, UUID operationId, String rawBody, UUID idempotencyKey) {
        Context ctx = member(user, operationId);
        Instant now = clock.instant();
        if (!now.isBefore(closesAt(ctx))) throw BusinessException.conflict("CHAT_CLOSED", "Chat encerrado. Não é possível enviar novas mensagens.");
        if (!manager(ctx, user) && repository.activeMute(ctx.operationId(), user.internalId(), now).isPresent()) {
            throw BusinessException.forbidden("Você está silenciado neste chat e não pode enviar mensagens agora.");
        }
        String body = clean(rawBody);
        limiter.check("operation-chat", user.internalId(), 30, Duration.ofMinutes(1));
        UUID id = repository.insert(ctx.channelId(), user.internalId(), body, idempotencyKey == null ? UUID.randomUUID() : idempotencyKey, now);
        hub.changed(ctx.operationId());
        return new ChatMessage(id, null, body, false, now, false, true);
    }

    @Transactional
    public void delete(AuthenticatedUser user, UUID operationId, UUID messageId) {
        Context ctx = managed(user, operationId);
        if (repository.delete(ctx.channelId(), messageId, clock.instant()) != 1) throw BusinessException.notFound("Mensagem não encontrada.");
        repository.log(ctx.operationId(), user.internalId(), "CHAT_MESSAGE", messageId, "DELETED", null, clock.instant());
        hub.changed(ctx.operationId());
    }

    @Transactional
    public void pin(AuthenticatedUser user, UUID operationId, UUID messageId, boolean pinned) {
        Context ctx = managed(user, operationId);
        if (repository.pin(ctx.channelId(), messageId, pinned ? user.internalId() : null, clock.instant()) != 1) {
            throw BusinessException.notFound("Mensagem não encontrada.");
        }
        repository.log(ctx.operationId(), user.internalId(), "CHAT_MESSAGE", messageId, pinned ? "PINNED" : "UNPINNED", null, clock.instant());
        hub.changed(ctx.operationId());
    }

    /** duration: 1H, 6H, 24H ou INDEFINITE. */
    @Transactional
    public void mute(AuthenticatedUser user, UUID operationId, UUID operatorId, String duration) {
        Context ctx = managed(user, operationId);
        String option = duration == null ? "" : duration.strip().toUpperCase();
        if (!MUTE_OPTIONS.containsKey(option) && !"INDEFINITE".equals(option)) {
            throw BusinessException.badRequest("INVALID_MUTE", "Escolha 1 hora, 6 horas, 24 horas ou tempo indeterminado.");
        }
        long target = repository.member(ctx.operationId(), operatorId).orElseThrow(() -> BusinessException.notFound("Jogador não encontrado no chat."));
        if (target == ctx.organizerUserId() || target == user.internalId()) throw BusinessException.badRequest("CANNOT_MUTE", "O organizador não pode ser silenciado.");
        Instant now = clock.instant();
        Instant until = "INDEFINITE".equals(option) ? null : now.plus(MUTE_OPTIONS.get(option));
        repository.mute(ctx.operationId(), target, until, user.internalId(), now);
        repository.log(ctx.operationId(), user.internalId(), "OPERATOR", operatorId, "MUTED", option, now);
        hub.changed(ctx.operationId());
    }

    @Transactional
    public void unmute(AuthenticatedUser user, UUID operationId, UUID operatorId) {
        Context ctx = managed(user, operationId);
        long target = repository.member(ctx.operationId(), operatorId).orElseThrow(() -> BusinessException.notFound("Jogador não encontrado no chat."));
        Instant now = clock.instant();
        repository.lift(ctx.operationId(), target, user.internalId(), now);
        repository.log(ctx.operationId(), user.internalId(), "OPERATOR", operatorId, "UNMUTED", null, now);
        hub.changed(ctx.operationId());
    }

    @Transactional(readOnly = true)
    public SseEmitter stream(AuthenticatedUser user, UUID operationId) {
        return hub.subscribe(member(user, operationId).operationId());
    }

    /** Foto de quem esta no chat, so para quem esta no chat. */
    @Transactional(readOnly = true)
    public OperationPageRepository.Cover photo(AuthenticatedUser user, UUID operationId, UUID operatorId) {
        Context ctx = member(user, operationId);
        repository.member(ctx.operationId(), operatorId).orElseThrow(() -> BusinessException.notFound("Foto não encontrada."));
        return repository.photo(operatorId).orElseThrow(() -> BusinessException.notFound("Foto não encontrada."));
    }

    static Instant closesAt(Context ctx) {
        return ctx.endsAt().plus(AFTER_END);
    }

    /** "Este chat sera encerrado em 17/11/2026, as 17h." durante os 7 dias depois do termino. */
    static String notice(Context ctx, Instant now) {
        Instant closes = closesAt(ctx);
        if (now.isBefore(ctx.endsAt()) || !now.isBefore(closes)) return null;
        return "Este chat será encerrado em " + DAY.format(closes.atZone(BRASILIA)) + ", às " + time(closes) + ".";
    }

    static String time(Instant instant) {
        var local = instant.atZone(BRASILIA);
        return local.getMinute() == 0 ? local.getHour() + "h" : local.getHour() + "h" + String.format("%02d", local.getMinute());
    }

    static String clean(String raw) {
        String body = raw == null ? "" : raw.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").strip();
        if (body.isEmpty()) throw BusinessException.badRequest("EMPTY_MESSAGE", "Escreva uma mensagem.");
        if (body.length() > MAX_BODY) throw BusinessException.badRequest("MESSAGE_TOO_LONG", "A mensagem pode ter até " + MAX_BODY + " caracteres.");
        return body;
    }

    private ChatMessage map(UUID operationId, Message m, AuthenticatedUser user) {
        boolean deleted = "DELETED".equals(m.status()) || "HIDDEN".equals(m.status());
        String photo = m.hasPhoto() ? "/api/operations/" + operationId + "/chat/photos/" + m.authorId() + "?v=" + m.photoVersion() : null;
        return new ChatMessage(m.id(), new Author(m.authorId(), m.callsign(), m.displayName(), photo), deleted ? "" : m.body(), deleted,
            m.createdAt(), m.pinnedAt() != null && !deleted, m.authorUserId() == user.internalId());
    }

    private MutedItem muted(Muted m) {
        return new MutedItem(m.operatorId(), m.callsign(), m.until());
    }

    private Context context(AuthenticatedUser user, UUID operationId) {
        return repository.context(operationId, user.internalId()).orElseThrow(() -> BusinessException.notFound("Operação não encontrada."));
    }

    private Context member(AuthenticatedUser user, UUID operationId) {
        Context ctx = context(user, operationId);
        if (!manager(ctx, user) && (ctx.participation() == null || !MEMBER.contains(ctx.participation()))) {
            throw BusinessException.forbidden("O chat é exclusivo para jogadores inscritos e para o organizador.");
        }
        return ctx;
    }

    private Context managed(AuthenticatedUser user, UUID operationId) {
        Context ctx = context(user, operationId);
        if (!manager(ctx, user)) throw BusinessException.forbidden("Só o organizador pode moderar o chat.");
        return ctx;
    }

    private static boolean manager(Context ctx, AuthenticatedUser user) {
        return ctx.organizerUserId() == user.internalId() || ctx.admin();
    }
}
