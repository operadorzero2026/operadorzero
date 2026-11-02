package br.com.operadorzero.shared.ratelimit;

import br.com.operadorzero.shared.web.BusinessException;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Janela fixa por usuario para acoes de conteudo (publicar, comentar, denunciar). */
@Component
public class UserActionLimiter {
    private static final Logger log = LoggerFactory.getLogger(UserActionLimiter.class);
    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>("""
        local count = redis.call('INCR', KEYS[1])
        if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
        return count
        """, Long.class);

    private final StringRedisTemplate redis;

    public UserActionLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void check(String action, long userId, int limit, Duration window) {
        Long count;
        try {
            count = redis.execute(INCREMENT, List.of("oz:rate:" + action + ":user:" + userId), Long.toString(window.toSeconds()));
        } catch (DataAccessException exception) {
            // Sem Redis o conteudo continua disponivel; o limite volta assim que o Redis responder.
            log.warn("Limite de {} indisponivel: {}", action, exception.getMessage());
            return;
        }
        if (count != null && count > limit) {
            throw BusinessException.tooManyRequests("Você atingiu o limite de envios por enquanto. Tente novamente em alguns minutos.");
        }
    }
}
