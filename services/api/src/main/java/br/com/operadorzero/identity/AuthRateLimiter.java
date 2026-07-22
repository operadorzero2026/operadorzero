package br.com.operadorzero.identity;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class AuthRateLimiter {
    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>("""
        local current = redis.call('INCR', KEYS[1])
        if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
        return current
        """, Long.class);

    private final StringRedisTemplate redis;
    private final TokenSupport tokens;

    public AuthRateLimiter(StringRedisTemplate redis, TokenSupport tokens) {
        this.redis = redis;
        this.tokens = tokens;
    }

    public void check(String action, HttpServletRequest request, String subject, int limit, Duration window) {
        String ipKey = key(action, "ip", request.getRemoteAddr());
        String subjectKey = key(action, "subject", subject == null ? "anonymous" : subject.toLowerCase());
        try {
            long ipCount = increment(ipKey, window);
            long subjectCount = increment(subjectKey, window);
            if (ipCount > limit || subjectCount > limit) {
                throw AuthException.rateLimited();
            }
        } catch (AuthException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw AuthException.unavailable();
        }
    }

    private long increment(String key, Duration window) {
        Long value = redis.execute(INCREMENT, List.of(key), Long.toString(window.toSeconds()));
        if (value == null) {
            throw AuthException.unavailable();
        }
        return value;
    }

    private String key(String action, String dimension, String value) {
        return "oz:auth:rate:" + action + ":" + dimension + ":" + tokens.hash(value);
    }
}
