package br.com.operadorzero.payment;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operation.OperationDtos.MessageResponse;
import br.com.operadorzero.payment.MercadoPagoClient.PaymentGatewayException;
import br.com.operadorzero.payment.PaymentDtos.Checkout;
import br.com.operadorzero.payment.PaymentDtos.Managed;
import br.com.operadorzero.payment.PaymentDtos.ManualRequest;
import br.com.operadorzero.payment.PaymentDtos.Mine;
import br.com.operadorzero.payment.PaymentDtos.RefundRequest;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private final PaymentService service;
    private final PaymentRepository repository;
    private final MercadoPagoProperties properties;
    private final Clock clock = Clock.systemUTC();

    public PaymentController(PaymentService service, PaymentRepository repository, MercadoPagoProperties properties) {
        this.service = service;
        this.repository = repository;
        this.properties = properties;
    }

    @GetMapping("/api/operations/{id}/payment")
    Mine mine(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.mine(user, id);
    }

    @PostMapping("/api/operations/{id}/payment/pix")
    Mine pix(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.createPix(user, id);
    }

    /** Pagamento no Checkout Pro do organizador (com divisao). Devolve so a URL do Mercado Pago. */
    @PostMapping("/api/operations/{id}/payment/checkout")
    Checkout checkout(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.createCheckout(user, id);
    }

    @GetMapping("/api/operations/{id}/payments")
    Managed managed(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.managed(user, id);
    }

    @PostMapping("/api/operations/{id}/participants/{operatorId}/payment/confirm")
    MessageResponse confirm(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @PathVariable UUID operatorId,
                            @Valid @RequestBody ManualRequest request) {
        return service.confirmManually(user, id, operatorId, request.note());
    }

    @PostMapping("/api/operations/{id}/participants/{operatorId}/payment/refund")
    MessageResponse refund(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @PathVariable UUID operatorId,
                           @Valid @RequestBody ManualRequest request) {
        return service.registerRefund(user, id, operatorId, request.note());
    }

    @PostMapping("/api/operations/{id}/participants/{operatorId}/payment/mercadopago-refund")
    MessageResponse refundOnline(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @PathVariable UUID operatorId,
                                 @Valid @RequestBody RefundRequest request) {
        return service.refundViaMercadoPago(user, id, operatorId, request.amount(), request.note());
    }

    /**
     * Notificacao do Mercado Pago (publica, sem CSRF). Sem assinatura valida nada acontece. A mesma entrega (x-request-id) nao e
     * processada duas vezes. Responde 200 para o que nao e nosso (evita reenvios); 503 quando o Mercado Pago esta fora.
     */
    @PostMapping("/api/payments/mercadopago/webhook")
    ResponseEntity<Void> webhook(@RequestParam(name = "data.id", required = false) String dataId,
                                 @RequestParam(name = "id", required = false) String legacyId,
                                 @RequestParam(name = "type", required = false) String type,
                                 @RequestParam(name = "topic", required = false) String topic,
                                 @RequestHeader(name = "x-signature", required = false) String signature,
                                 @RequestHeader(name = "x-request-id", required = false) String requestId,
                                 @RequestBody(required = false) JsonNode body) {
        String id = dataId != null ? dataId : legacyId;
        if (id == null && body != null && body.path("data").hasNonNull("id")) id = body.path("data").get("id").asText();
        if (!properties.anyEnabled() || !WebhookSignature.valid(properties.webhookSecret(), signature, requestId, id)) {
            return ResponseEntity.status(401).build();
        }
        String kind = type != null ? type : topic != null ? topic : body != null && body.hasNonNull("type") ? body.get("type").asText() : null;
        Long mpUserId = body != null && body.hasNonNull("user_id") ? body.get("user_id").asLong() : null;
        String delivery = requestId != null && requestId.length() <= 120 ? requestId : "sem-id:" + kind + ":" + id;
        if (!repository.notificationIsNew(delivery, kind, id, mpUserId, clock.instant())) return ResponseEntity.ok().build();
        try {
            Optional<Long> payment = service.notification(kind, id, mpUserId);
            repository.notificationResult(delivery, payment.isPresent() ? "PROCESSED" : "IGNORED", payment.orElse(null), null, clock.instant());
        } catch (PaymentGatewayException unavailable) {
            log.warn("Webhook do Mercado Pago adiado: API indisponivel");
            repository.notificationResult(delivery, "FAILED", null, "Mercado Pago indisponível", clock.instant());
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok().build();
    }
}
