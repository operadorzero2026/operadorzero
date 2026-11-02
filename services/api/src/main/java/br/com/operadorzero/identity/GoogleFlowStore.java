package br.com.operadorzero.identity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/**
 * Estado temporario do login Google no Redis. O navegador guarda somente um token opaco em cookie HttpOnly;
 * o Redis guarda o hash HMAC desse token como chave. Tudo expira sozinho e e consumido uma unica vez.
 */
@Component
public class GoogleFlowStore {
    static final Duration FLOW_TTL = Duration.ofMinutes(10);
    static final Duration LINK_TTL = Duration.ofMinutes(15);
    private static final String PREFIX = "operador-zero:google:";
    private static final ObjectInputFilter AUTHORIZATION_REQUEST_FILTER = ObjectInputFilter.Config.createFilter(
        "org.springframework.security.oauth2.core.**;java.lang.*;java.util.*;java.time.*;!*");

    public enum Mode { LOGIN, REGISTER, LINK }

    public record Flow(Mode mode, boolean termsAccepted, Long linkUserId) {}

    public record LinkIntent(long userId, String issuer, String subject, String email) {}

    /** Identidade Google ja confirmada que ainda nao tem conta: falta so aceitar os Termos. */
    public record SignupIntent(String issuer, String subject, String email, String displayName) {}

    private final StringRedisTemplate redis;
    private final TokenSupport tokens;

    public GoogleFlowStore(StringRedisTemplate redis, TokenSupport tokens) {
        this.redis = redis;
        this.tokens = tokens;
    }

    /** Cria o fluxo e libera exatamente um pedido de autorizacao. Retorna o token bruto para o cookie. */
    public String startFlow(Flow flow) {
        String raw = tokens.newToken();
        String id = tokens.hash(raw);
        String value = flow.mode().name() + "|" + flow.termsAccepted() + "|" + (flow.linkUserId() == null ? "" : flow.linkUserId());
        redis.opsForValue().set(PREFIX + "flow:" + id, value, FLOW_TTL);
        redis.opsForValue().set(PREFIX + "gate:" + id, "1", FLOW_TTL);
        return raw;
    }

    public boolean consumeGate(String raw) {
        return "1".equals(redis.opsForValue().getAndDelete(PREFIX + "gate:" + tokens.hash(raw)));
    }

    public Optional<Flow> takeFlow(String raw) {
        String value = redis.opsForValue().getAndDelete(PREFIX + "flow:" + tokens.hash(raw));
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split("\\|", -1);
        return Optional.of(new Flow(Mode.valueOf(parts[0]), Boolean.parseBoolean(parts[1]),
            parts[2].isEmpty() ? null : Long.valueOf(parts[2])));
    }

    public void saveAuthorizationRequest(String raw, OAuth2AuthorizationRequest request) {
        redis.opsForValue().set(PREFIX + "authz:" + tokens.hash(raw), serialize(request), FLOW_TTL);
    }

    public Optional<OAuth2AuthorizationRequest> loadAuthorizationRequest(String raw) {
        return Optional.ofNullable(redis.opsForValue().get(PREFIX + "authz:" + tokens.hash(raw))).map(this::deserialize);
    }

    public Optional<OAuth2AuthorizationRequest> takeAuthorizationRequest(String raw) {
        return Optional.ofNullable(redis.opsForValue().getAndDelete(PREFIX + "authz:" + tokens.hash(raw))).map(this::deserialize);
    }

    public String saveLinkIntent(LinkIntent intent) {
        String raw = tokens.newToken();
        String value = intent.userId() + "|" + encode(intent.issuer()) + "|" + encode(intent.subject()) + "|" + encode(intent.email());
        redis.opsForValue().set(PREFIX + "link:" + tokens.hash(raw), value, LINK_TTL);
        return raw;
    }

    public Optional<LinkIntent> takeLinkIntent(String raw) {
        String value = redis.opsForValue().getAndDelete(PREFIX + "link:" + tokens.hash(raw));
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split("\\|", -1);
        return Optional.of(new LinkIntent(Long.parseLong(parts[0]), decode(parts[1]), decode(parts[2]), decode(parts[3])));
    }

    public String saveSignupIntent(SignupIntent intent) {
        String raw = tokens.newToken();
        String value = encode(intent.issuer()) + "|" + encode(intent.subject()) + "|" + encode(intent.email()) + "|"
            + encode(intent.displayName() == null ? "" : intent.displayName());
        redis.opsForValue().set(PREFIX + "signup:" + tokens.hash(raw), value, FLOW_TTL);
        return raw;
    }

    public Optional<SignupIntent> takeSignupIntent(String raw) {
        String value = redis.opsForValue().getAndDelete(PREFIX + "signup:" + tokens.hash(raw));
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split("\\|", -1);
        return Optional.of(new SignupIntent(decode(parts[0]), decode(parts[1]), decode(parts[2]), decode(parts[3])));
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private String serialize(OAuth2AuthorizationRequest request) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(request);
            out.flush();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Nao foi possivel guardar o pedido de autorizacao.", exception);
        }
    }

    private OAuth2AuthorizationRequest deserialize(String value) {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(value)))) {
            in.setObjectInputFilter(AUTHORIZATION_REQUEST_FILTER);
            return (OAuth2AuthorizationRequest) in.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException | IllegalArgumentException exception) {
            return null;
        }
    }
}
