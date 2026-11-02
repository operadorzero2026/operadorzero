package br.com.operadorzero.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.stereotype.Component;

/**
 * Token CSRF em cookie (envio duplo). Ele so muda quando a identidade muda (login, Google, logout).
 * Antes, como a sessao nao fica no SecurityContext do Spring, o token era apagado a cada requisicao
 * autenticada e toda escrita seguinte tomava 403 e era repetida pelo cliente.
 */
@Component
public class CsrfRotation {
    private final CookieCsrfTokenRepository repository;

    public CsrfRotation(AuthProperties properties) {
        repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName("OZ_CSRF");
        repository.setHeaderName("X-CSRF-TOKEN");
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> {
            cookie.secure(properties.cookie().secure()).sameSite(properties.cookie().sameSite());
            if (properties.cookie().domain() != null && !properties.cookie().domain().isBlank()) {
                cookie.domain(properties.cookie().domain());
            }
        });
    }

    public CookieCsrfTokenRepository repository() {
        return repository;
    }

    /** Descarta o token atual; o proximo GET /api/auth/csrf emite um novo, ja ligado a nova identidade. */
    public void rotate(HttpServletRequest request, HttpServletResponse response) {
        repository.saveToken(null, request, response);
    }
}
