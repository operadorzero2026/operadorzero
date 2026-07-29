package br.com.operadorzero.identity;

import br.com.operadorzero.identity.AuthDtos.CsrfResponse;
import br.com.operadorzero.identity.AuthDtos.EmailRequest;
import br.com.operadorzero.identity.AuthDtos.LoginRequest;
import br.com.operadorzero.identity.AuthDtos.MessageResponse;
import br.com.operadorzero.identity.AuthDtos.PasswordResetRequest;
import br.com.operadorzero.identity.AuthDtos.RegisterRequest;
import br.com.operadorzero.identity.AuthDtos.SessionResponse;
import br.com.operadorzero.identity.AuthDtos.TokenRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String NEUTRAL_EMAIL_MESSAGE = "Se o e-mail puder receber esta acao, enviaremos as instrucoes.";

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken token, HttpServletResponse response) {
        service.clearLegacyAuthentication(response);
        return new CsrfResponse(token.getToken(), token.getHeaderName());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    MessageResponse register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
        service.register(body.displayName(), body.email(), body.password(), body.termsAccepted(), request);
        return new MessageResponse("Cadastro realizado. Enviamos um link de confirmacao para o seu e-mail.");
    }

    @PostMapping("/verify-email")
    MessageResponse verifyEmail(@Valid @RequestBody TokenRequest body, HttpServletRequest request) {
        service.verifyEmail(body.token(), request);
        return new MessageResponse("E-mail confirmado. Sua conta esta ativa.");
    }

    @PostMapping("/login")
    SessionResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        return SessionResponse.from(service.login(body.email(), body.password(), request, response));
    }

    @PostMapping("/password-recovery")
    @ResponseStatus(HttpStatus.ACCEPTED)
    MessageResponse passwordRecovery(@Valid @RequestBody EmailRequest body, HttpServletRequest request) {
        service.requestPasswordRecovery(body.email(), request);
        return new MessageResponse(NEUTRAL_EMAIL_MESSAGE);
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    MessageResponse resendVerification(@Valid @RequestBody EmailRequest body, HttpServletRequest request) {
        service.resendVerification(body.email(), request);
        return new MessageResponse(NEUTRAL_EMAIL_MESSAGE);
    }

    @PostMapping("/password-reset")
    MessageResponse passwordReset(@Valid @RequestBody PasswordResetRequest body, HttpServletRequest request) {
        service.resetPassword(body.token(), body.password(), request);
        return new MessageResponse("Senha alterada. Entre novamente em todos os dispositivos.");
    }

    @GetMapping("/session")
    ResponseEntity<SessionResponse> session(@AuthenticationPrincipal AuthenticatedUser principal,
                                             HttpServletResponse response) {
        service.clearLegacyAuthentication(response);
        if (principal == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(SessionResponse.from(principal));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request, HttpServletResponse response,
                @AuthenticationPrincipal AuthenticatedUser principal) {
        service.logout(request, response, principal);
    }
}
