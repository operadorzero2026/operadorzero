package br.com.operadorzero.identity;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verificacao em duas etapas (TOTP). Papeis administrativos so valem em sessoes que confirmaram o codigo;
 * sem isso, o SessionAuthenticationFilter remove esses papeis da sessao.
 */
@Service
public class MfaService {
    public static final Set<String> PRIVILEGED_ROLES = Set.of("ADMIN", "MODERATOR");
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Setup(String secret, String otpauthUri) {}

    private final IdentityRepository repository;
    private final TokenSupport tokens;
    private final AuthCookieService cookies;
    private final AuthRateLimiter rateLimiter;
    private final AuthService auth;
    private final AuthProperties properties;
    private final SecretKeySpec cipherKey;

    public MfaService(IdentityRepository repository, TokenSupport tokens, AuthCookieService cookies,
                      AuthRateLimiter rateLimiter, AuthService auth, AuthProperties properties) {
        this.repository = repository;
        this.tokens = tokens;
        this.cookies = cookies;
        this.rateLimiter = rateLimiter;
        this.auth = auth;
        this.properties = properties;
        this.cipherKey = new SecretKeySpec(tokens.derivedKey("mfa-totp-v1"), "AES");
    }

    public static boolean hasPrivilegedRole(List<String> roles) {
        return roles.stream().anyMatch(PRIVILEGED_ROLES::contains);
    }

    public static List<String> withoutPrivilegedRoles(List<String> roles) {
        return roles.stream().filter(role -> !PRIVILEGED_ROLES.contains(role)).toList();
    }

    public boolean isEnabled(long userId) {
        return repository.findMfa(userId).map(IdentityRepository.MfaRow::enabled).orElse(false);
    }

    @Transactional
    public Setup setup(AuthenticatedUser principal) {
        auth.ensureEnabled();
        if (isEnabled(principal.internalId())) {
            throw AuthException.mfaAlreadyEnabled();
        }
        byte[] secret = Totp.newSecret();
        repository.savePendingMfa(principal.internalId(), encrypt(secret), Instant.now());
        String base32 = Totp.base32(secret);
        String label = URLEncoder.encode(properties.issuerName() + ":" + principal.email(), StandardCharsets.UTF_8).replace("+", "%20");
        String issuer = URLEncoder.encode(properties.issuerName(), StandardCharsets.UTF_8).replace("+", "%20");
        return new Setup(base32, "otpauth://totp/" + label + "?secret=" + base32 + "&issuer=" + issuer
            + "&algorithm=SHA1&digits=6&period=30");
    }

    @Transactional
    public void enable(AuthenticatedUser principal, String code, HttpServletRequest request) {
        IdentityRepository.MfaRow row = repository.findMfa(principal.internalId())
            .filter(mfa -> !mfa.enabled()).orElseThrow(AuthException::mfaSetupRequired);
        long step = checkCode(principal, row, code, request);
        Instant now = Instant.now();
        if (!repository.enableMfa(principal.internalId(), step, now)) {
            throw AuthException.invalidMfaCode();
        }
        markCurrentSession(request, now);
        audit(principal, "AUTH_MFA_ENABLED", request, now);
    }

    @Transactional
    public void verify(AuthenticatedUser principal, String code, HttpServletRequest request) {
        IdentityRepository.MfaRow row = repository.findMfa(principal.internalId())
            .filter(IdentityRepository.MfaRow::enabled).orElseThrow(AuthException::mfaSetupRequired);
        long step = checkCode(principal, row, code, request);
        Instant now = Instant.now();
        if (!repository.useMfaStep(principal.internalId(), step, now)) {
            throw AuthException.invalidMfaCode();
        }
        markCurrentSession(request, now);
        audit(principal, "AUTH_MFA_VERIFIED", request, now);
    }

    @Transactional
    public void disable(AuthenticatedUser principal, String code, HttpServletRequest request) {
        IdentityRepository.MfaRow row = repository.findMfa(principal.internalId())
            .filter(IdentityRepository.MfaRow::enabled).orElseThrow(AuthException::mfaSetupRequired);
        long step = checkCode(principal, row, code, request);
        Instant now = Instant.now();
        if (!repository.useMfaStep(principal.internalId(), step, now)) {
            throw AuthException.invalidMfaCode();
        }
        repository.disableMfa(principal.internalId(), now);
        audit(principal, "AUTH_MFA_DISABLED", request, now);
    }

    private long checkCode(AuthenticatedUser principal, IdentityRepository.MfaRow row, String code, HttpServletRequest request) {
        rateLimiter.check("mfa", request, String.valueOf(principal.internalId()), 6, Duration.ofMinutes(15));
        OptionalLong step = Totp.verify(decrypt(row.secretCiphertext()), code == null ? null : code.strip(), Instant.now());
        if (step.isEmpty() || step.getAsLong() <= row.lastUsedStep()) {
            throw AuthException.invalidMfaCode();
        }
        return step.getAsLong();
    }

    private void markCurrentSession(HttpServletRequest request, Instant now) {
        cookies.sessionToken(request).ifPresent(raw -> repository.markSessionMfaVerified(tokens.hash(raw), now));
    }

    private void audit(AuthenticatedUser principal, String action, HttpServletRequest request, Instant now) {
        Optional<UserAccount> account = repository.findById(principal.internalId());
        account.ifPresent(value -> auth.audit(value, action, null, auth.ipHash(request), now));
    }

    String encrypt(byte[] secret) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, cipherKey, new GCMParameterSpec(128, iv));
            byte[] sealed = cipher.doFinal(secret);
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Nao foi possivel proteger o segredo de MFA.", exception);
        }
    }

    byte[] decrypt(String value) {
        try {
            byte[] data = Base64.getDecoder().decode(value);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, cipherKey, new GCMParameterSpec(128, data, 0, 12));
            return cipher.doFinal(data, 12, data.length - 12);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw AuthException.mfaSetupRequired();
        }
    }
}
