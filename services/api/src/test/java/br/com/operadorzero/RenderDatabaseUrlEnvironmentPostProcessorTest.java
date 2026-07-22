package br.com.operadorzero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.operadorzero.shared.config.RenderDatabaseUrlEnvironmentPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class RenderDatabaseUrlEnvironmentPostProcessorTest {
    private final RenderDatabaseUrlEnvironmentPostProcessor processor =
        new RenderDatabaseUrlEnvironmentPostProcessor();

    @Test
    void convertsRenderPostgresUrlWithoutLoggingOrSplittingCredentials() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("DATABASE_URL", "postgresql://database.internal:5432/operadorzero");

        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("spring.datasource.url"))
            .isEqualTo("jdbc:postgresql://database.internal:5432/operadorzero");
    }

    @Test
    void removesCredentialsFromJdbcUrlAndUsesDefaultPortWhenRenderOmitsIt() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("DATABASE_URL", "postgresql://app:test@database.internal/operadorzero");

        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("spring.datasource.url"))
            .isEqualTo("jdbc:postgresql://database.internal:5432/operadorzero");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("app");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("test");
    }

    @Test
    void rejectsUnexpectedDatabaseProtocols() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("DATABASE_URL", "mysql://database.internal/operadorzero");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication(Object.class)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PostgreSQL");
    }
}
