package br.com.operadorzero.shared.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

        environment.getPropertySources().addFirst(
            new MapPropertySource("renderDatabaseUrl", datasourceProperties(databaseUrl))
        );
    }

    private Map<String, Object> datasourceProperties(String databaseUrl) {
        try {
            String normalizedUrl = databaseUrl.startsWith("jdbc:")
                ? databaseUrl.substring("jdbc:".length())
                : databaseUrl;
            URI postgresUri = URI.create(normalizedUrl);

            if (!"postgresql".equals(postgresUri.getScheme()) || postgresUri.getHost() == null) {
                throw new IllegalArgumentException();
            }

            int port = postgresUri.getPort() > 0 ? postgresUri.getPort() : 5432;
            URI jdbcUri = new URI(
                "postgresql",
                null,
                postgresUri.getHost(),
                port,
                postgresUri.getPath(),
                requireTls(postgresUri.getQuery()),
                null
            );

            Map<String, Object> properties = new HashMap<>();
            properties.put("spring.datasource.url", "jdbc:" + jdbcUri.toASCIIString());

            String userInfo = postgresUri.getUserInfo();
            if (userInfo != null) {
                String[] credentials = userInfo.split(":", 2);
                properties.put("spring.datasource.username", credentials[0]);
                if (credentials.length == 2) {
                    properties.put("spring.datasource.password", credentials[1]);
                }
            }

            return properties;
        } catch (IllegalArgumentException | URISyntaxException ignored) {
            throw new IllegalStateException("DATABASE_URL deve ser uma URL PostgreSQL valida.");
        }
    }

    private String requireTls(String query) {
        if (query != null && Stream.of(query.split("&"))
            .filter(parameter -> parameter.regionMatches(true, 0, "sslmode=", 0, "sslmode=".length()))
            .map(parameter -> parameter.substring("sslmode=".length()))
            .anyMatch(mode -> mode.equalsIgnoreCase("require")
                || mode.equalsIgnoreCase("verify-ca") || mode.equalsIgnoreCase("verify-full"))) {
            return query;
        }
        String safeQuery = query == null ? "" : Stream.of(query.split("&"))
            .filter(parameter -> !parameter.regionMatches(true, 0, "sslmode=", 0, "sslmode=".length()))
            .collect(Collectors.joining("&"));
        return safeQuery.isBlank() ? "sslmode=require" : safeQuery + "&sslmode=require";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
