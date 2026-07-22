package br.com.operadorzero.identity;

import org.springframework.http.HttpStatus;

public final class AuthException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    private AuthException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static AuthException unavailable() {
        return new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_UNAVAILABLE", "Autenticacao temporariamente indisponivel.");
    }

    public static AuthException invalidCredentials() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "E-mail ou senha incorretos.");
    }

    public static AuthException invalidToken() {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_OR_EXPIRED_TOKEN", "O link e invalido ou expirou.");
    }

    public static AuthException termsRequired() {
        return new AuthException(HttpStatus.BAD_REQUEST, "TERMS_REQUIRED", "Aceite os Termos de Uso e a Politica de Privacidade.");
    }

    public static AuthException invalidPassword(String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", message);
    }

    public static AuthException accountLinkRequired() {
        return new AuthException(HttpStatus.CONFLICT, "ACCOUNT_LINK_REQUIRED", "Entre com e-mail e senha para vincular o Google com seguranca.");
    }

    public static AuthException rateLimited() {
        return new AuthException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Muitas tentativas. Aguarde e tente novamente.");
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
