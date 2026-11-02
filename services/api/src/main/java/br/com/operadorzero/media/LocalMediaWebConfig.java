package br.com.operadorzero.media;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serve as midias processadas direto da API, so em desenvolvimento.
 * Em producao o Nginx entrega /media/ a partir do mesmo diretorio, sem passar pela API.
 */
@Configuration
@ConditionalOnProperty(name = "app.media.serve-locally", havingValue = "true")
public class LocalMediaWebConfig implements WebMvcConfigurer {
    private final Path root;

    public LocalMediaWebConfig(@Value("${app.media.root}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/media/**")
            .addResourceLocations(root.toUri().toString())
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable());
    }
}
