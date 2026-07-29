package br.com.operadorzero.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ResendAuthMailGatewayTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsJsonOverHttpsApiContractWithIdempotency() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> idempotency = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/emails", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            idempotency.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        ObjectMapper objectMapper = new ObjectMapper();
        ResendAuthMailGateway gateway = gateway(objectMapper, "re_test_key");
        gateway.send(message());

        JsonNode json = objectMapper.readTree(body.get());
        assertThat(authorization.get()).isEqualTo("Bearer re_test_key");
        assertThat(idempotency.get()).isEqualTo("auth-verify-email-token-hash");
        assertThat(json.path("from").asText()).isEqualTo("Operador Zero <acesso@example.test>");
        assertThat(json.path("to").get(0).asText()).isEqualTo("operator@example.test");
        assertThat(json.path("text").asText()).contains("https://app.example.test/?token=opaque");
        assertThat(json.path("tags").get(0).path("value").asText()).isEqualTo("verify-email");
        assertThat(body.get()).doesNotContain("re_test_key");
    }

    @Test
    void providerFailureIsGenericAndDoesNotExposeResponseBody() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/emails", exchange -> {
            byte[] response = "{\"message\":\"provider-sensitive-detail\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(422, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        assertThatThrownBy(() -> gateway(new ObjectMapper(), "re_test_key").send(message()))
            .isInstanceOf(AuthMailDeliveryException.class)
            .hasMessage("Provedor de e-mail recusou a solicitacao")
            .hasMessageNotContaining("provider-sensitive-detail")
            .hasMessageNotContaining("re_test_key");
    }

    @Test
    void missingApiKeyFailsWithoutAttemptingDelivery() {
        ResendAuthMailGateway gateway = new ResendAuthMailGateway(new ObjectMapper(), new ResendProperties(""));

        assertThatThrownBy(() -> gateway.send(message()))
            .isInstanceOf(AuthMailDeliveryException.class)
            .hasMessage("Provedor de e-mail nao configurado");
    }

    private ResendAuthMailGateway gateway(ObjectMapper objectMapper, String apiKey) {
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/emails");
        return new ResendAuthMailGateway(objectMapper, new ResendProperties(apiKey), endpoint,
            HttpClient.newBuilder().build());
    }

    private AuthMailGateway.Message message() {
        return new AuthMailGateway.Message("Operador Zero <acesso@example.test>", "operator@example.test",
            "Confirme sua conta", "Abra https://app.example.test/?token=opaque",
            "<a href=\"https://app.example.test/?token=opaque\">Confirmar</a>", "VERIFY_EMAIL",
            "auth-verify-email-token-hash");
    }
}
