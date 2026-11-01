package br.com.operadorzero.identity;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * So e alcancado quando o filtro do OAuth nao liberou o pedido (acesso direto, botao Voltar do navegador
 * ou fluxo expirado). Devolve a pessoa ao site com uma mensagem, sem tocar no Google.
 */
@RestController
public class GoogleAuthorizationFallbackController {
    private final GoogleAuthHandler handler;

    public GoogleAuthorizationFallbackController(GoogleAuthHandler handler) {
        this.handler = handler;
    }

    @GetMapping({GoogleAuthService.AUTHORIZATION_PATH, "/login/oauth2/code/google"})
    void expired(HttpServletResponse response) throws IOException {
        response.sendRedirect(handler.expiredRedirect());
    }
}
