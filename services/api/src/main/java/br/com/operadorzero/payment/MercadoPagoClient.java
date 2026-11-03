package br.com.operadorzero.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Chamadas a API de pagamentos do Mercado Pago (Pix). Erros nunca expoem a credencial nem a resposta crua ao usuario. */
@Component
public class MercadoPagoClient {
    private static final Logger log = LoggerFactory.getLogger(MercadoPagoClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final DateTimeFormatter MP_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");

    /** Dados do pagamento como o Mercado Pago informa (fonte da verdade do status). */
    public record RemotePayment(String id, String status, String statusDetail, String externalReference, BigDecimal amount,
                                String pixCode, String pixQrBase64, String ticketUrl, Instant expiresAt) {}

    public record PixRequest(String idempotencyKey, BigDecimal amount, String description, String externalReference,
                             String payerEmail, String payerName, Instant expiresAt, URI notificationUrl) {}

    private final ObjectMapper json;
    private final MercadoPagoProperties properties;
    private final HttpClient http;

    @Autowired
    public MercadoPagoClient(ObjectMapper json, MercadoPagoProperties properties) {
        this(json, properties, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
    }

    MercadoPagoClient(ObjectMapper json, MercadoPagoProperties properties, HttpClient http) {
        this.json = json;
        this.properties = properties;
        this.http = http;
    }

    public RemotePayment createPix(PixRequest request) {
        ObjectNode body = json.createObjectNode();
        body.put("transaction_amount", request.amount());
        body.put("description", request.description());
        body.put("payment_method_id", "pix");
        body.put("external_reference", request.externalReference());
        body.put("date_of_expiration", MP_DATE.format(request.expiresAt().atZone(BRAZIL)));
        if (request.notificationUrl() != null) body.put("notification_url", request.notificationUrl().toString());
        ObjectNode payer = body.putObject("payer");
        payer.put("email", request.payerEmail());
        if (request.payerName() != null && !request.payerName().isBlank()) payer.put("first_name", request.payerName());
        JsonNode response = send(HttpRequest.newBuilder(endpoint("/v1/payments"))
            .header("X-Idempotency-Key", request.idempotencyKey())
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())), "criar Pix")
            .orElseThrow(() -> new PaymentGatewayException("Pagamento recusado pelo Mercado Pago."));
        return parse(response);
    }

    /** Status atual de um pagamento; vazio se o Mercado Pago nao o conhece (ex.: id falso num webhook). */
    public Optional<RemotePayment> find(String paymentId) {
        if (!paymentId.matches("[0-9A-Za-z_-]{1,64}")) return Optional.empty();
        return send(HttpRequest.newBuilder(endpoint("/v1/payments/" + paymentId)).GET(), "consultar pagamento").map(this::parse);
    }

    /** Cancela um Pix ainda nao pago (melhor esforco: se falhar, o codigo expira sozinho). */
    public void cancel(String paymentId) {
        try {
            send(HttpRequest.newBuilder(endpoint("/v1/payments/" + paymentId))
                .method("PUT", HttpRequest.BodyPublishers.ofString("{\"status\":\"cancelled\"}")), "cancelar Pix");
        } catch (PaymentGatewayException exception) {
            log.warn("Nao foi possivel cancelar o Pix {} no Mercado Pago", paymentId);
        }
    }

    private URI endpoint(String path) {
        return URI.create(properties.apiBaseUrl().toString().replaceAll("/+$", "") + path);
    }

    private Optional<JsonNode> send(HttpRequest.Builder builder, String action) {
        if (!properties.enabled()) throw new PaymentGatewayException("Pagamento online indisponível no momento.");
        HttpRequest request = builder.timeout(REQUEST_TIMEOUT)
            .header("Authorization", "Bearer " + properties.accessToken().strip())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "operador-zero-api/0.1")
            .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) return Optional.empty();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Mercado Pago recusou {} (HTTP {})", action, response.statusCode());
                throw new PaymentGatewayException("Não foi possível falar com o Mercado Pago. Tente novamente.");
            }
            return Optional.of(json.readTree(response.body()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException("Não foi possível falar com o Mercado Pago. Tente novamente.");
        } catch (IOException exception) {
            log.warn("Mercado Pago indisponivel ao {}: {}", action, exception.getClass().getSimpleName());
            throw new PaymentGatewayException("Não foi possível falar com o Mercado Pago. Tente novamente.");
        }
    }

    private RemotePayment parse(JsonNode node) {
        JsonNode data = node.path("point_of_interaction").path("transaction_data");
        String expiration = text(node, "date_of_expiration");
        Instant expiresAt = null;
        if (expiration != null) {
            try { expiresAt = OffsetDateTime.parse(expiration).toInstant(); } catch (RuntimeException ignored) { /* formato inesperado */ }
        }
        return new RemotePayment(node.path("id").asText(), text(node, "status"), text(node, "status_detail"),
            text(node, "external_reference"), node.hasNonNull("transaction_amount") ? node.get("transaction_amount").decimalValue() : null,
            text(data, "qr_code"), text(data, "qr_code_base64"), text(data, "ticket_url"), expiresAt);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    public static class PaymentGatewayException extends RuntimeException {
        public PaymentGatewayException(String message) {
            super(message);
        }
    }
}
