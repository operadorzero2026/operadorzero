package br.com.operadorzero.payment;

import br.com.operadorzero.identity.TokenSupport;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Cifra os tokens OAuth dos organizadores (AES-GCM, chave derivada de AUTH_HASH_KEY, como os segredos de MFA). */
@Component
public class SecretBox {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    @Autowired
    public SecretBox(TokenSupport tokens) {
        this(tokens.derivedKey("mercadopago-oauth-v1"));
    }

    SecretBox(byte[] key) {
        this.key = new SecretKeySpec(key, "AES");
    }

    public String seal(String plain) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Nao foi possivel proteger o token.", exception);
        }
    }

    public String open(String value) {
        if (value == null) return null;
        try {
            byte[] data = Base64.getDecoder().decode(value);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, data, 0, 12));
            return new String(cipher.doFinal(data, 12, data.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Token ilegivel (chave trocada?).");
        }
    }
}
