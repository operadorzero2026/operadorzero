package br.com.operadorzero.operation;

import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class OperationChatRateLimiter {
    private final StringRedisTemplate redis;
    public OperationChatRateLimiter(StringRedisTemplate redis) { this.redis = redis; }

    public boolean allow(long userId, UUID channelId) {
        String key = "rate:operation-chat:" + channelId + ":" + userId;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) redis.expire(key, Duration.ofSeconds(10));
        return count != null && count <= 5;
    }
}
