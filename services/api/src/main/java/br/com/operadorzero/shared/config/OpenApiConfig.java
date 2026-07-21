package br.com.operadorzero.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI operadorZeroOpenApi() {
        return new OpenAPI().info(new Info().title("Operador Zero API").version("v1").description("API modular em construção. Nenhum contrato demonstrativo deve ser considerado produção."));
    }
}
