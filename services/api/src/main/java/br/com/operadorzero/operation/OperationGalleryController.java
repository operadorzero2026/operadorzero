package br.com.operadorzero.operation;

import br.com.operadorzero.feed.FeedDtos.FeedPostResponse;
import br.com.operadorzero.feed.FeedService;
import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.shared.web.BusinessException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Fotos e videos" da operacao: posts do feed marcados com ela. Cada post segue as regras do feed (bloqueios, midia pronta,
 * post apagado some); a pagina publica mostra so os posts compartilhaveis. O organizador desvincula sem apagar o post.
 */
@RestController
public class OperationGalleryController {
    static final int LIMIT = 60;

    public record Gallery(List<FeedPostResponse> items, boolean manager) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final FeedService feed;
    private final OperationRepository operations;

    public OperationGalleryController(NamedParameterJdbcTemplate jdbc, FeedService feed, OperationRepository operations) {
        this.jdbc = jdbc;
        this.feed = feed;
        this.operations = operations;
    }

    @GetMapping("/api/operations/{id}/gallery")
    @Transactional(readOnly = true)
    Gallery gallery(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        var op = operation(id, user.internalId());
        List<Long> ids = postIds(op[0]);
        List<FeedPostResponse> items = feed.postsByIds(user, ids).stream().map(FeedService.IdentifiedPost::post).toList();
        return new Gallery(items, op[1] == user.internalId() || operations.isOperationAdmin(op[0], user.internalId()));
    }

    @GetMapping("/api/public/operations/{id}/gallery")
    @Transactional(readOnly = true)
    ResponseEntity<Gallery> publicGallery(@PathVariable UUID id) {
        var op = operation(id, -1);
        List<FeedPostResponse> items = jdbc.query("""
            SELECT fp.public_id FROM feed_post fp WHERE fp.operation_id = :operation AND fp.status = 'PUBLISHED' AND fp.public_share
            ORDER BY fp.published_at DESC LIMIT 30
            """, Map.of("operation", op[0]), (r, i) -> r.getObject(1, UUID.class)).stream()
            .map(postId -> feed.publicPost(postId).orElse(null)).filter(Objects::nonNull).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(new Gallery(items, false));
    }

    /** Desvincula uma publicacao marcada por engano. O post continua no feed; a acao fica registrada. */
    @DeleteMapping("/api/operations/{id}/gallery/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void unlink(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @PathVariable UUID postId) {
        var op = operation(id, user.internalId());
        if (op[1] != user.internalId() && !operations.isOperationAdmin(op[0], user.internalId())) {
            throw BusinessException.forbidden("Só o organizador pode desvincular publicações.");
        }
        int changed = jdbc.update("""
            UPDATE feed_post SET operation_id = NULL WHERE public_id = :post AND operation_id = :operation AND post_type = 'MEDIA'
            """, Map.of("post", postId, "operation", op[0]));
        if (changed != 1) throw BusinessException.notFound("Publicação não encontrada nesta operação.");
        jdbc.update("""
            INSERT INTO operation_moderation_action(operation_id, actor_user_id, target_type, target_public_id, action, created_at)
            VALUES (:operation, :actor, 'FEED_POST', :post, 'UNLINKED', :now)
            """, new MapSqlParameterSource().addValue("operation", op[0]).addValue("actor", user.internalId()).addValue("post", postId)
                .addValue("now", Timestamp.from(Instant.now())));
    }

    private List<Long> postIds(long operationId) {
        return jdbc.query("""
            SELECT fp.id FROM feed_post fp WHERE fp.operation_id = :operation AND fp.status = 'PUBLISHED'
            ORDER BY fp.published_at DESC LIMIT :limit
            """, Map.of("operation", operationId, "limit", LIMIT), (r, i) -> r.getLong(1));
    }

    /** [id interno, organizador]; rascunho so para o organizador. */
    private long[] operation(UUID id, long viewer) {
        return jdbc.query("""
            SELECT id, organizer_user_id FROM airsoft_operation
            WHERE public_id = :id AND deleted_at IS NULL AND (status <> 'DRAFT' OR organizer_user_id = :viewer)
            """, Map.of("id", id, "viewer", viewer), (r, i) -> new long[] {r.getLong(1), r.getLong(2)}).stream().findFirst()
            .orElseThrow(() -> BusinessException.notFound("Operação não encontrada."));
    }
}
