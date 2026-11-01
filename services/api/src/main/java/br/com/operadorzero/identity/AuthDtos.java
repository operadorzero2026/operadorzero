package br.com.operadorzero.identity;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
        @NotBlank @Size(max = 80) String displayName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotBlank @Size(min = 12, max = 128) String passwordConfirmation,
        @AssertTrue boolean termsAccepted
    ) {
        @AssertTrue(message = "As senhas devem ser iguais.")
        public boolean isPasswordConfirmationValid() {
            return password != null && password.equals(passwordConfirmation);
        }
    }

    public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 128) String password
    ) {}

    public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {}

    public record TokenRequest(@NotBlank @Size(min = 32, max = 256) String token) {}

    public record PasswordResetRequest(
        @NotBlank @Size(min = 32, max = 256) String token,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotBlank @Size(min = 12, max = 128) String passwordConfirmation
    ) {
        @AssertTrue(message = "As senhas devem ser iguais.")
        public boolean isPasswordConfirmationValid() {
            return password != null && password.equals(passwordConfirmation);
        }
    }

    public record MessageResponse(String message) {}

    public record GoogleStartRequest(@NotBlank @Size(max = 16) String mode, boolean termsAccepted) {}

    public record GoogleStartResponse(String authorizationPath) {}

    public record CsrfResponse(String token, String headerName) {}

    /** roles traz somente os papeis validos nesta sessao; mfaRequired indica papeis administrativos aguardando o codigo. */
    public record SessionResponse(UUID id, String email, String username, String displayName, String callsign, List<String> roles,
                                  boolean googleLinked, boolean hasPassword, boolean mfaEnabled, boolean mfaRequired) {
        public static SessionResponse from(UserAccount account, List<String> effectiveRoles, boolean googleLinked, boolean mfaEnabled) {
            boolean pending = MfaService.hasPrivilegedRole(account.roles()) && !MfaService.hasPrivilegedRole(effectiveRoles);
            return new SessionResponse(account.publicId(), account.email(), account.username(), account.displayName(),
                account.callsign(), effectiveRoles, googleLinked, account.passwordHash() != null, mfaEnabled, pending);
        }
    }

    public record MfaCodeRequest(@NotBlank @Size(min = 6, max = 8) String code) {}

    public record MfaSetupResponse(String secret, String otpauthUri) {}
}
