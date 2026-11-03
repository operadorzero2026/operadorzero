package br.com.operadorzero.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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

/**
 * Chamadas as APIs do Mercado Pago: pagamentos (Pix direto e consulta), OAuth dos organizadores, preferencias do Checkout Pro
 * com marketplace_fee, busca por referencia e reembolso. Nenhum token aparece em log ou em mensagem de erro.
 */
@Component
public class MercadoPagoClient {
    private static final Logger log = LoggerFactory.getLogger(MercadoPagoClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final DateTimeFormatter MP_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");
    private static final String UNAVAILABLE = "Não foi possível falar com o Mercado Pago. Tente novamente.";

    /**
     * Pagamento como o Mercado Pago informa (fonte da verdade). providerFee: tarifas do Mercado Pago (fee_details sem a
     * application_fee); applicationFee: comissao do marketplace efetivamente cobrada; refunded: valor ja devolvido.
     */
    public record RemotePayment(String id, String status, String statusDetail, String externalReference, BigDecimal amount,
                                String pixCode, String pixQrBase64, String ticketUrl, Instant expiresAt, Long collectorId,
                                String paymentTypeId, BigDecimal providerFee, BigDecimal applicationFee, BigDecimal refunded,
                                boolean feesKnown) {
        public RemotePayment(String id, String status, String statusDetail, String externalReference, BigDecimal amount,
                             String pixCode, String pixQrBase64, String ticketUrl, Instant expiresAt) {
            this(id, status, statusDetail, externalReference, amount, pixCode, pixQrBase64, ticketUrl, expiresAt, null, null, null, null, null, false);
        }
    }

    public record PixRequest(String idempotencyKey, BigDecimal amount, String description, String externalReference,
                             String payerEmail, String payerName, Instant expiresAt, URI notificationUrl) {}

    public record OAuthTokens(String accessToken, String refreshToken, long expiresIn, Long userId, String publicKey, String scope,
                              Boolean liveMode) {}

    /** Preferencia do Checkout Pro criada na conta do organizador. */
    public record PreferenceRequest(String idempotencyKey, String itemId, String title, BigDecimal amount, BigDecimal marketplaceFee,
                                    String externalReference, URI notificationUrl, URI backUrl, Instant expiresAt,
                                    String operationId, String paymentId) {}

    public record Preference(String id, String initPoint, String sandboxInitPoint, Long collectorId) {}

    /** Falha de OAuth com o codigo do Mercado Pago (ex.: invalid_grant), sem detalhes sensiveis. */
    public static class OAuthException extends RuntimeException {
        private final String code;

        public OAuthException(String code) {
            super("OAuth recusado: " + code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

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

    // ---- Pix direto (conta da plataforma, Etapa F) -----------------------------------------------------------------

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
        JsonNode response = send(platformToken(), HttpRequest.newBuilder(endpoint("/v1/payments"))
            .header("X-Idempotency-Key", request.idempotencyKey())
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())), "criar Pix")
            .orElseThrow(() -> new PaymentGatewayException("Pagamento recusado pelo Mercado Pago."));
        return parse(response);
    }

    /** Pagamento pela conta da plataforma; vazio se o Mercado Pago nao o conhece. */
    public Optional<RemotePayment> find(String paymentId) {
        return find(platformToken(), paymentId);
    }

    /** Pagamento consultado com o token de quem recebeu (organizador no marketplace). */
    public Optional<RemotePayment> find(String accessToken, String paymentId) {
        if (!paymentId.matches("[0-9A-Za-z_-]{1,64}")) return Optional.empty();
        return send(accessToken, HttpRequest.newBuilder(endpoint("/v1/payments/" + paymentId)).GET(), "consultar pagamento").map(this::parse);
    }

    /** Pagamento mais recente com esta referencia externa (recupera notificacoes perdidas). */
    public Optional<RemotePayment> findByReference(String accessToken, String externalReference) {
        String query = "/v1/payments/search?sort=date_created&criteria=desc&external_reference="
            + URLEncoder.encode(externalReference, StandardCharsets.UTF_8);
        return send(accessToken, HttpRequest.newBuilder(endpoint(query)).GET(), "buscar pagamento")
            .map(node -> node.path("results"))
            .filter(results -> results.isArray() && !results.isEmpty())
            .map(results -> parse(results.get(0)));
    }

    /** Cancela um Pix direto ainda nao pago (melhor esforco: se falhar, o codigo expira sozinho). */
    public void cancel(String paymentId) {
        try {
            send(platformToken(), HttpRequest.newBuilder(endpoint("/v1/payments/" + paymentId))
                .method("PUT", HttpRequest.BodyPublishers.ofString("{\"status\":\"cancelled\"}")), "cancelar Pix");
        } catch (PaymentGatewayException exception) {
            log.warn("Nao foi possivel cancelar o Pix {} no Mercado Pago", paymentId);
        }
    }

    /**
     * Reembolso pedido pelo organizador (nunca automatico). amount nulo = total. No split 1:1 o Mercado Pago debita o
     * organizador e o marketplace na proporcao de cada um.
     */
    public void refund(String accessToken, String paymentId, BigDecimal amount, String idempotencyKey) {
        ObjectNode body = json.createObjectNode();
        if (amount != null) body.put("amount", amount);
        send(accessToken, HttpRequest.newBuilder(endpoint("/v1/payments/" + paymentId + "/refunds"))
            .header("X-Idempotency-Key", idempotencyKey)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())), "reembolsar")
            .orElseThrow(() -> new PaymentGatewayException("Pagamento não encontrado no Mercado Pago."));
    }

    // ---- Marketplace: OAuth e Checkout Pro -------------------------------------------------------------------------

    /** URL de autorizacao (Authorization Code + PKCE S256). */
    public URI authorizationUrl(String state, String codeChallenge, URI redirectUri) {
        String query = "client_id=" + enc(properties.clientId().strip()) + "&response_type=code&platform_id=mp"
            + "&state=" + enc(state) + "&redirect_uri=" + enc(redirectUri.toString())
            + "&code_challenge=" + enc(codeChallenge) + "&code_challenge_method=S256";
        return URI.create(properties.authBaseUrl().toString().replaceAll("/+$", "") + "/authorization?" + query);
    }

    public OAuthTokens exchangeCode(String code, String codeVerifier, URI redirectUri) {
        ObjectNode body = oauthBody("authorization_code");
        body.put("code", code);
        body.put("code_verifier", codeVerifier);
        body.put("redirect_uri", redirectUri.toString());
        if (properties.testEnvironment()) body.put("test_token", "true");
        return oauth(body);
    }

    public OAuthTokens refresh(String refreshToken) {
        ObjectNode body = oauthBody("refresh_token");
        body.put("refresh_token", refreshToken);
        return oauth(body);
    }

    public Preference createPreference(String sellerAccessToken, PreferenceRequest request) {
        ObjectNode body = json.createObjectNode();
        ObjectNode item = body.putArray("items").addObject();
        item.put("id", request.itemId());
        item.put("title", request.title());
        item.put("quantity", 1);
        item.put("currency_id", "BRL");
        item.put("unit_price", request.amount());
        body.put("marketplace_fee", request.marketplaceFee());
        body.put("external_reference", request.externalReference());
        if (request.notificationUrl() != null) body.put("notification_url", request.notificationUrl().toString());
        if (request.backUrl() != null) {
            ObjectNode back = body.putObject("back_urls");
            back.put("success", request.backUrl().toString());
            back.put("pending", request.backUrl().toString());
            back.put("failure", request.backUrl().toString());
            body.put("auto_return", "approved");
        }
        body.put("expires", true);
        body.put("expiration_date_to", MP_DATE.format(request.expiresAt().atZone(BRAZIL)));
        body.put("statement_descriptor", "OPERADORZERO");
        // So identificadores internos: nada de dados pessoais nos metadados.
        ObjectNode metadata = body.putObject("metadata");
        metadata.put("operation_id", request.operationId());
        metadata.put("payment_id", request.paymentId());
        JsonNode response = send(sellerAccessToken, HttpRequest.newBuilder(endpoint("/checkout/preferences"))
            .header("X-Idempotency-Key", request.idempotencyKey())
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())), "criar preferencia")
            .orElseThrow(() -> new PaymentGatewayException("O Mercado Pago recusou a criação do pagamento."));
        return new Preference(text(response, "id"), text(response, "init_point"), text(response, "sandbox_init_point"),
            response.hasNonNull("collector_id") ? response.get("collector_id").asLong() : null);
    }

    // ---------------------------------------------------------------------------------------------------------------

    private ObjectNode oauthBody(String grantType) {
        if (!properties.marketplaceEnabled()) throw new PaymentGatewayException("Conexão com o Mercado Pago indisponível no momento.");
        ObjectNode body = json.createObjectNode();
        body.put("client_id", properties.clientId().strip());
        body.put("client_secret", properties.clientSecret().strip());
        body.put("grant_type", grantType);
        return body;
    }

    private OAuthTokens oauth(ObjectNode body) {
        HttpRequest request = HttpRequest.newBuilder(endpoint("/oauth/token")).timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/json").header("Accept", "application/json")
            .header("User-Agent", "operador-zero-api/0.1")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode node = response.body() == null || response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
            if (response.statusCode() >= 400 && response.statusCode() < 500) {
                String code = node.hasNonNull("error") ? node.get("error").asText() : "invalid_request";
                log.warn("OAuth do Mercado Pago recusado (HTTP {}, {})", response.statusCode(), code);
                throw new OAuthException(code.length() > 40 ? code.substring(0, 40) : code);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300 || !node.hasNonNull("access_token")) {
                log.warn("OAuth do Mercado Pago falhou (HTTP {})", response.statusCode());
                throw new PaymentGatewayException(UNAVAILABLE);
            }
            return new OAuthTokens(node.get("access_token").asText(), text(node, "refresh_token"), node.path("expires_in").asLong(15_552_000L),
                node.hasNonNull("user_id") ? node.get("user_id").asLong() : null, text(node, "public_key"), text(node, "scope"),
                node.hasNonNull("live_mode") ? node.get("live_mode").asBoolean() : null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException(UNAVAILABLE);
        } catch (IOException exception) {
            log.warn("OAuth do Mercado Pago indisponivel: {}", exception.getClass().getSimpleName());
            throw new PaymentGatewayException(UNAVAILABLE);
        }
    }

    private String platformToken() {
        if (!properties.enabled()) throw new PaymentGatewayException("Pagamento online indisponível no momento.");
        return properties.accessToken().strip();
    }

    private URI endpoint(String path) {
        return URI.create(properties.apiBaseUrl().toString().replaceAll("/+$", "") + path);
    }

    private Optional<JsonNode> send(String accessToken, HttpRequest.Builder builder, String action) {
        HttpRequest request = builder.timeout(REQUEST_TIMEOUT)
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "operador-zero-api/0.1")
            .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) return Optional.empty();
            if (response.statusCode() == 401) {
                log.warn("Mercado Pago recusou o token ao {} (HTTP 401)", action);
                throw new TokenRejectedException();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Mercado Pago recusou {} (HTTP {})", action, response.statusCode());
                throw new PaymentGatewayException(UNAVAILABLE);
            }
            return Optional.of(json.readTree(response.body()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException(UNAVAILABLE);
        } catch (IOException exception) {
            log.warn("Mercado Pago indisponivel ao {}: {}", action, exception.getClass().getSimpleName());
            throw new PaymentGatewayException(UNAVAILABLE);
        }
    }

    RemotePayment parse(JsonNode node) {
        JsonNode data = node.path("point_of_interaction").path("transaction_data");
        String expiration = text(node, "date_of_expiration");
        Instant expiresAt = null;
        if (expiration != null) {
            try { expiresAt = OffsetDateTime.parse(expiration).toInstant(); } catch (RuntimeException ignored) { /* formato inesperado */ }
        }
        BigDecimal providerFee = BigDecimal.ZERO;
        BigDecimal applicationFee = BigDecimal.ZERO;
        boolean feesKnown = node.path("fee_details").isArray();
        for (JsonNode fee : node.path("fee_details")) {
            BigDecimal amount = fee.hasNonNull("amount") ? fee.get("amount").decimalValue() : BigDecimal.ZERO;
            if ("application_fee".equals(text(fee, "type"))) applicationFee = applicationFee.add(amount);
            else providerFee = providerFee.add(amount);
        }
        return new RemotePayment(node.path("id").asText(), text(node, "status"), text(node, "status_detail"),
            text(node, "external_reference"), node.hasNonNull("transaction_amount") ? node.get("transaction_amount").decimalValue() : null,
            text(data, "qr_code"), text(data, "qr_code_base64"), text(data, "ticket_url"), expiresAt,
            node.hasNonNull("collector_id") ? node.get("collector_id").asLong() : null, text(node, "payment_type_id"),
            providerFee, applicationFee,
            node.hasNonNull("transaction_amount_refunded") ? node.get("transaction_amount_refunded").decimalValue() : BigDecimal.ZERO,
            feesKnown);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static class PaymentGatewayException extends RuntimeException {
        public PaymentGatewayException(String message) {
            super(message);
        }
    }

    /** O token foi recusado (revogado ou vencido): o organizador precisa reconectar. */
    public static class TokenRejectedException extends PaymentGatewayException {
        public TokenRejectedException() {
            super("A conta Mercado Pago do organizador precisa ser reconectada.");
        }
    }
}
