package br.com.operadorzero.payment;

import br.com.operadorzero.identity.AuthenticatedUser;
import br.com.operadorzero.operation.OperationDtos.MessageResponse;
import br.com.operadorzero.payment.MercadoPagoClient.PaymentGatewayException;
import br.com.operadorzero.payment.PaymentDtos.Managed;
import br.com.operadorzero.payment.PaymentDtos.ManualRequest;
import br.com.operadorzero.payment.PaymentDtos.Mine;
import jakarta.validation.Valid;
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
    private final MercadoPagoProperties properties;

    public PaymentController(PaymentService service, MercadoPagoProperties properties) {
        this.service = service;
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

    /**
     * Notificacao do Mercado Pago (publica, sem CSRF). Sem assinatura valida nada acontece. Responde 200 para o que nao e nosso
     * (evita reenvios); 503 quando o Mercado Pago esta fora, para ele tentar de novo.
     */
    @PostMapping("/api/payments/mercadopago/webhook")
    ResponseEntity<Void> webhook(@RequestParam(name = "data.id", required = false) String dataId,
                                 @RequestParam(name = "id", required = false) String legacyId,
                                 @RequestParam(name = "type", required = false) String type,
                                 @RequestParam(name = "topic", required = false) String topic,
                                 @RequestHeader(name = "x-signature", required = false) String signature,
                                 @RequestHeader(name = "x-request-id", required = false) String requestId) {
        String id = dataId != null ? dataId : legacyId;
        if (!properties.enabled() || !WebhookSignature.valid(properties.webhookSecret(), signature, requestId, id)) {
            return ResponseEntity.status(401).build();
        }
        try {
            service.notification(type != null ? type : topic, id);
        } catch (PaymentGatewayException unavailable) {
            log.warn("Webhook do Mercado Pago adiado: API indisponivel");
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok().build();
    }
}
