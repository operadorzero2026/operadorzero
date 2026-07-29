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
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "E-mail ou senha invalidos.");
    }

    public static AuthException emailNotVerified() {
        return new AuthException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED",
            "Seu cadastro ainda nao foi confirmado. Verifique seu e-mail ou solicite um novo link de confirmacao.");
    }

    public static AuthException passwordSetupRequired() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "PASSWORD_SETUP_REQUIRED",
            "Esta conta foi criada anteriormente por outro metodo de acesso. Utilize Esqueci minha senha para definir uma senha e continuar.");
    }

    public static AuthException emailAlreadyRegistered() {
        return new AuthException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Este e-mail ja possui cadastro.");
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
