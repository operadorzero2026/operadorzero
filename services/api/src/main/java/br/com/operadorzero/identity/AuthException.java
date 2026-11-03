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
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
            "E-mail ou senha invalidos. Se a conta foi criada com o Google, use Continuar com Google ou Esqueci minha senha.");
    }

    public static AuthException emailNotVerified() {
        return new AuthException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED",
            "Seu cadastro ainda nao foi confirmado. Verifique seu e-mail ou solicite um novo link de confirmacao.");
    }



    /** Decisao do produto: avisar que o e-mail ja tem conta (o limite de tentativas do cadastro continua valendo). */
    public static AuthException emailAlreadyRegistered() {
        return new AuthException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Este e-mail já possui uma conta. Faça login ou recupere sua senha.");
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

    public static AuthException googleSignupExpired() {
        return new AuthException(HttpStatus.CONFLICT, "GOOGLE_SIGNUP_EXPIRED",
            "A confirmacao do Google expirou. Toque em Continuar com Google novamente.");
    }

    public static AuthException googleUnavailable() {
        return new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_UNAVAILABLE",
            "Entrar com Google esta indisponivel no momento. Use e-mail e senha.");
    }

    public static AuthException mfaAlreadyEnabled() {
        return new AuthException(HttpStatus.CONFLICT, "MFA_ALREADY_ENABLED", "A verificacao em duas etapas ja esta ativa.");
    }

    public static AuthException mfaSetupRequired() {
        return new AuthException(HttpStatus.CONFLICT, "MFA_SETUP_REQUIRED", "Configure a verificacao em duas etapas antes de continuar.");
    }

    public static AuthException invalidMfaCode() {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_MFA_CODE", "Codigo invalido ou ja utilizado. Use o codigo atual do aplicativo autenticador.");
    }

    public static AuthException invalidGoogleMode() {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_GOOGLE_MODE", "Modo de acesso invalido.");
    }

    public static AuthException sessionRequired() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Entre para continuar.");
    }

    public static AuthException passwordRequiredToUnlink() {
        return new AuthException(HttpStatus.CONFLICT, "PASSWORD_REQUIRED",
            "Defina uma senha pela recuperacao de senha antes de desvincular o Google.");
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
