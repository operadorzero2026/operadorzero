package br.com.operadorzero.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
final class ResendAuthMailGateway implements AuthMailGateway {
    private static final URI SEND_ENDPOINT = URI.create("https://api.resend.com/emails");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final ObjectMapper objectMapper;
    private final ResendProperties properties;
    private final URI endpoint;
    private final HttpClient httpClient;

    ResendAuthMailGateway(ObjectMapper objectMapper, ResendProperties properties) {
        this(objectMapper, properties, SEND_ENDPOINT, HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build());
    }

    ResendAuthMailGateway(ObjectMapper objectMapper, ResendProperties properties, URI endpoint, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.endpoint = endpoint;
        this.httpClient = httpClient;
    }

    @Override
    public void send(Message message) {
        if (!properties.configured()) {
            throw new AuthMailDeliveryException("Provedor de e-mail nao configurado");
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(new SendEmailRequest(
                message.from(), List.of(message.to()), message.subject(), message.text(),
                List.of(new Tag("auth_action", message.kind().toLowerCase(Locale.ROOT).replace('_', '-')))
            ));
        } catch (JsonProcessingException exception) {
            throw new AuthMailDeliveryException("Nao foi possivel preparar o e-mail", exception);
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(endpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + properties.apiKeyValue())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Idempotency-Key", message.idempotencyKey())
                .header("User-Agent", "operador-zero-api/0.1")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        } catch (IllegalArgumentException exception) {
            throw new AuthMailDeliveryException("Configuracao do provedor de e-mail invalida", exception);
        }

        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AuthMailDeliveryException("Provedor de e-mail recusou a solicitacao");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AuthMailDeliveryException("Envio de e-mail interrompido", exception);
        } catch (IOException exception) {
            throw new AuthMailDeliveryException("Provedor de e-mail indisponivel", exception);
        }
    }

    private record SendEmailRequest(String from, List<String> to, String subject, String text, List<Tag> tags) {}
    private record Tag(String name, String value) {}
}
