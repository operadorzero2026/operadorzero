package br.com.operadorzero.feed;

import br.com.operadorzero.feed.FeedDtos.FeedPostResponse;
import br.com.operadorzero.feed.FeedDtos.MediaResponse;
import br.com.operadorzero.shared.web.BusinessException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * Link compartilhado (/p/{id}): WhatsApp e redes leem as tags Open Graph deste HTML (nao executam o app);
 * quem abre e levado para /post/{id}, a pagina publica do app. So posts publicados com compartilhamento publico.
 */
@RestController
public class PublicPostController {
    private final FeedService feed;
    private final String baseUrl;

    public PublicPostController(FeedService feed, @Value("${app.auth.frontend-base-url:http://localhost:4174}") String baseUrl) {
        this.feed = feed;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @GetMapping("/api/public/posts/{id}")
    ResponseEntity<FeedPostResponse> post(@PathVariable UUID id) {
        FeedPostResponse post = feed.publicPost(id).orElseThrow(() -> BusinessException.notFound("Post não encontrado."));
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(post);
    }

    @GetMapping(value = "/p/{id}", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> share(@PathVariable UUID id) {
        var post = feed.publicPost(id);
        if (post.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_HTML)
                .body(page("Post indisponível · Operador Zero", "Este post não está mais disponível.", null, baseUrl + "/"));
        }
        FeedPostResponse value = post.get();
        String target = baseUrl + "/post/" + value.id();
        String title = "@" + value.author().username() + " no Operador Zero";
        String description = value.caption() == null || value.caption().isBlank() ? "Veja este post no Operador Zero."
            : value.caption().length() > 180 ? value.caption().substring(0, 177) + "…" : value.caption();
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).cacheControl(CacheControl.noCache())
            .body(page(title, description, image(value), target));
    }

    private String image(FeedPostResponse post) {
        if (post.media().isEmpty()) return null;
        MediaResponse media = post.media().getFirst();
        String url = media.posterUrl() != null ? media.posterUrl() : media.images().isEmpty() ? null : media.images().getLast().url();
        return url == null ? null : url.startsWith("http") ? url : baseUrl + url;
    }

    private static String page(String title, String description, String image, String target) {
        String t = HtmlUtils.htmlEscape(title);
        String d = HtmlUtils.htmlEscape(description);
        String u = HtmlUtils.htmlEscape(target);
        StringBuilder html = new StringBuilder("<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
            .append("<title>").append(t).append("</title>")
            .append("<meta name=\"description\" content=\"").append(d).append("\">")
            .append("<meta property=\"og:type\" content=\"article\"><meta property=\"og:site_name\" content=\"Operador Zero\">")
            .append("<meta property=\"og:title\" content=\"").append(t).append("\">")
            .append("<meta property=\"og:description\" content=\"").append(d).append("\">")
            .append("<meta property=\"og:url\" content=\"").append(u).append("\">")
            .append("<meta name=\"twitter:card\" content=\"summary_large_image\">");
        if (image != null) html.append("<meta property=\"og:image\" content=\"").append(HtmlUtils.htmlEscape(image)).append("\">");
        return html.append("<meta http-equiv=\"refresh\" content=\"0; url=").append(u).append("\">")
            .append("<link rel=\"canonical\" href=\"").append(u).append("\"></head><body>")
            .append("<p><a href=\"").append(u).append("\">Abrir no Operador Zero</a></p></body></html>").toString();
    }
}
