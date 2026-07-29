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

    public record CsrfResponse(String token, String headerName) {}

    public record SessionResponse(UUID id, String email, String username, String displayName, String callsign, List<String> roles) {
        public static SessionResponse from(UserAccount account) {
            return new SessionResponse(account.publicId(), account.email(), account.username(), account.displayName(),
                account.callsign(), account.roles());
        }

        public static SessionResponse from(AuthenticatedUser principal) {
            return new SessionResponse(principal.publicId(), principal.email(), principal.username(), principal.displayName(),
                principal.callsign(), principal.roles());
        }
    }
}
