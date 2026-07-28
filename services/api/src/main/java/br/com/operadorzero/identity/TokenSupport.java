package br.com.operadorzero.identity;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class TokenSupport {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec hashKey;

    @Autowired
    public TokenSupport(AuthProperties properties, @Value("${app.auth.hash-key:}") String configuredHashKey) {
        if (configuredHashKey == null || configuredHashKey.isBlank()) {
            if (properties.enabled()) {
                throw new IllegalStateException("AUTH_HASH_KEY e obrigatoria quando AUTH_ENABLED=true.");
            }
            byte[] ephemeralKey = new byte[32];
            secureRandom.nextBytes(ephemeralKey);
            this.hashKey = new SecretKeySpec(ephemeralKey, HMAC_ALGORITHM);
            return;
        }
        this.hashKey = key(configuredHashKey);
    }

    TokenSupport() {
        this.hashKey = key("operador-zero-test-hash-key-32-bytes-minimum");
    }

    TokenSupport(String configuredHashKey) {
        this.hashKey = key(configuredHashKey);
    }

    public String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(hashKey);
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA-256 indisponivel", exception);
        }
    }

    private static SecretKeySpec key(String configuredHashKey) {
        byte[] bytes = configuredHashKey.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("AUTH_HASH_KEY deve possuir ao menos 32 bytes.");
        }
        return new SecretKeySpec(bytes, HMAC_ALGORITHM);
    }
}
