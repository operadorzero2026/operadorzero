package br.com.operadorzero.identity;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("resendMail")
final class ResendMailHealthIndicator implements HealthIndicator {
    private final AuthProperties authProperties;
    private final ResendProperties resendProperties;

    ResendMailHealthIndicator(AuthProperties authProperties, ResendProperties resendProperties) {
        this.authProperties = authProperties;
        this.resendProperties = resendProperties;
    }

    @Override
    public Health health() {
        if (!authProperties.mail().enabled()) {
            return Health.up().build();
        }
        return resendProperties.configured()
            ? Health.up().build()
            : Health.down().withDetail("reason", "missing_api_key").build();
    }
}
