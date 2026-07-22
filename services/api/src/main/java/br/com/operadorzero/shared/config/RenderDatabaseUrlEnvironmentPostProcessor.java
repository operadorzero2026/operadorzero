package br.com.operadorzero.shared.config;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public final class RenderDatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return;
        }

        String jdbcUrl;
        if (databaseUrl.startsWith("jdbc:postgresql://")) {
            jdbcUrl = databaseUrl;
        } else if (databaseUrl.startsWith("postgresql://")) {
            jdbcUrl = "jdbc:" + databaseUrl;
        } else {
            throw new IllegalStateException("DATABASE_URL deve usar o protocolo PostgreSQL.");
        }

        environment.getPropertySources().addFirst(
            new MapPropertySource("renderDatabaseUrl", Map.of("spring.datasource.url", jdbcUrl))
        );
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
