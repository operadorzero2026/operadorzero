package br.com.operadorzero.operation;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operation.CallUpService.CallUp;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

@RestController
public class CallUpController {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, dd/MM", Locale.forLanguageTag("pt-BR"));
    private final CallUpService service;

    public CallUpController(CallUpService service) {
        this.service = service;
    }

    @GetMapping("/api/feed/callups")
    CallUpService.CallUpPage feed(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam(required = false) Integer limit) {
        return service.forFeed(user, limit);
    }

    @GetMapping("/api/feed/callups/{id}")
    CallUp one(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.one(user, id);
    }

    /** Convocacao aberta (link do WhatsApp): sem dados de quem esta inscrito. */
    @GetMapping("/api/public/callups/{id}")
    ResponseEntity<CallUp> publicOne(@PathVariable UUID id) {
        return service.single(id, -1, false).map(callUp -> ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(callUp))
            .orElse(ResponseEntity.notFound().build());
    }

    /** Previa (Open Graph) para o WhatsApp; quem abre vai para /convocacao/{id}. */
    @GetMapping(value = "/c/{id}", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> share(@PathVariable UUID id) {
        var callUp = service.single(id, -1, false);
        String target = service.shareUrl(id).replace("/c/", "/convocacao/");
        if (callUp.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_HTML)
                .body(html("Convocação encerrada · Operador Zero", "Este jogo não está mais com inscrição aberta.", target));
        }
        CallUp value = callUp.get();
        String title = "CONVOCAÇÃO: " + value.name();
        String spots = value.limit() == null ? value.occupied() + " alistados" : value.occupied() + "/" + value.limit() + " vagas";
        String description = DAY.format(value.date()) + " às " + value.startTime().toString().substring(0, 5) + " · " + value.fieldName()
            + " (" + value.city() + "/" + value.stateCode() + ") · " + spots;
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).cacheControl(CacheControl.noCache()).body(html(title, description, target));
    }

    private static String html(String title, String description, String target) {
        String t = HtmlUtils.htmlEscape(title, "UTF-8");
        String d = HtmlUtils.htmlEscape(description, "UTF-8");
        String u = HtmlUtils.htmlEscape(target, "UTF-8");
        return "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>" + t + "</title>"
            + "<meta name=\"description\" content=\"" + d + "\"><meta property=\"og:type\" content=\"website\">"
            + "<meta property=\"og:site_name\" content=\"Operador Zero\"><meta property=\"og:title\" content=\"" + t + "\">"
            + "<meta property=\"og:description\" content=\"" + d + "\"><meta property=\"og:url\" content=\"" + u + "\">"
            + "<meta http-equiv=\"refresh\" content=\"0; url=" + u + "\"><link rel=\"canonical\" href=\"" + u + "\"></head>"
            + "<body><p><a href=\"" + u + "\">Abrir a convocação no Operador Zero</a></p></body></html>";
    }
}
