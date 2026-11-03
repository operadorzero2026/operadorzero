package br.com.operadorzero.identity;

import br.com.operadorzero.identity.AuthDtos.CsrfResponse;
import br.com.operadorzero.identity.AuthDtos.EmailRequest;
import br.com.operadorzero.identity.AuthDtos.GoogleStartRequest;
import br.com.operadorzero.identity.AuthDtos.GoogleStartResponse;
import br.com.operadorzero.identity.AuthDtos.LoginRequest;
import br.com.operadorzero.identity.AuthDtos.MfaCodeRequest;
import br.com.operadorzero.identity.AuthDtos.MfaSetupResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final GoogleAuthService google;
    private final MfaService mfa;
    private final CsrfRotation csrf;

    public AuthController(AuthService service, GoogleAuthService google, MfaService mfa, CsrfRotation csrf) {
        this.service = service;
        this.google = google;
        this.mfa = mfa;
        this.csrf = csrf;
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
        return new MessageResponse("Enviamos um link de confirmação para seu e-mail. Confira também o Lixo Eletrônico.");
    }

    /** Confirma e ja abre a sessao (cookie novo e CSRF renovado), devolvendo o usuario como o login faz. */
    @PostMapping("/verify-email")
    SessionResponse verifyEmail(@Valid @RequestBody TokenRequest body, HttpServletRequest request, HttpServletResponse response) {
        UserAccount account = service.verifyEmail(body.token(), request, response);
        csrf.rotate(request, response);
        return SessionResponse.from(account, MfaService.withoutPrivilegedRoles(account.roles()), google.isLinked(account.id()),
            mfa.isEnabled(account.id()));
    }

    @PostMapping("/login")
    SessionResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        UserAccount account = service.login(body.email(), body.password(), request, response);
        csrf.rotate(request, response);
        google.completePendingLink(account, request, response);
        // Sessao recem-criada ainda nao confirmou o segundo fator.
        return SessionResponse.from(account, MfaService.withoutPrivilegedRoles(account.roles()), google.isLinked(account.id()),
            mfa.isEnabled(account.id()));
    }

    @PostMapping("/google/start")
    GoogleStartResponse googleStart(@Valid @RequestBody GoogleStartRequest body,
                                    @AuthenticationPrincipal AuthenticatedUser principal,
                                    HttpServletRequest request, HttpServletResponse response) {
        return new GoogleStartResponse(google.start(body.mode(), body.termsAccepted(), principal, request, response));
    }

    /** Conclui o cadastro de quem entrou com Google sem ter conta, depois de aceitar os Termos. */
    @PostMapping("/google/signup")
    SessionResponse googleSignup(@RequestBody GoogleSignupRequest body, HttpServletRequest request, HttpServletResponse response) {
        UserAccount account = google.completeSignup(body != null && body.termsAccepted(), request, response);
        csrf.rotate(request, response);
        return SessionResponse.from(account, MfaService.withoutPrivilegedRoles(account.roles()), true, mfa.isEnabled(account.id()));
    }

    public record GoogleSignupRequest(boolean termsAccepted) {}

    @PostMapping("/mfa/setup")
    MfaSetupResponse mfaSetup(@AuthenticationPrincipal AuthenticatedUser principal) {
        MfaService.Setup setup = mfa.setup(principal);
        return new MfaSetupResponse(setup.secret(), setup.otpauthUri());
    }

    @PostMapping("/mfa/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void mfaEnable(@AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody MfaCodeRequest body, HttpServletRequest request) {
        mfa.enable(principal, body.code(), request);
    }

    @PostMapping("/mfa/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void mfaVerify(@AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody MfaCodeRequest body, HttpServletRequest request) {
        mfa.verify(principal, body.code(), request);
    }

    @PostMapping("/mfa/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void mfaDisable(@AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody MfaCodeRequest body, HttpServletRequest request) {
        mfa.disable(principal, body.code(), request);
    }

    @DeleteMapping("/google/link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void googleUnlink(@AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        google.unlink(principal, request);
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
        return ResponseEntity.ok(service.findAccount(principal)
            .map(account -> SessionResponse.from(account, principal.roles(), google.isLinked(account.id()), mfa.isEnabled(account.id())))
            .orElseThrow(AuthException::sessionRequired));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request, HttpServletResponse response,
                @AuthenticationPrincipal AuthenticatedUser principal) {
        service.logout(request, response, principal);
        csrf.rotate(request, response);
    }
}
