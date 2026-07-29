package br.com.operadorzero.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthRateLimiterTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void subjectLimitIsScopedToSourceAddressInsteadOfCreatingAGlobalAccountLock() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(1L);
        TokenSupport tokens = new TokenSupport();
        AuthRateLimiter limiter = new AuthRateLimiter(redis, tokens);

        MockHttpServletRequest firstIp = new MockHttpServletRequest();
        firstIp.setRemoteAddr("203.0.113.10");
        MockHttpServletRequest secondIp = new MockHttpServletRequest();
        secondIp.setRemoteAddr("203.0.113.11");

        limiter.check("login", firstIp, "victim@example.com", 10, Duration.ofMinutes(15));
        limiter.check("login", secondIp, "victim@example.com", 10, Duration.ofMinutes(15));

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(redis, org.mockito.Mockito.times(2))
            .execute(any(RedisScript.class), keys.capture(), anyString());
        List<List<String>> calls = keys.getAllValues();
        assertThat(calls).allSatisfy(call -> assertThat(call).hasSize(2));
        assertThat(calls.getFirst().get(1)).isNotEqualTo(calls.get(1).get(1));
        assertThat(calls.getFirst().get(1)).contains("oz:auth:rate:login:subject-ip:");
    }
}
